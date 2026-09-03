package com.againspring.aiuser.orchestrator.safety;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 생성 텍스트를 REST 제출 전 검사하는 안전 가드.
 * LLM 미사용 — 결정적 정규식/키워드 검사.
 * 봇 생성 콘텐츠에만 적용 (실유저 입력에는 적용 금지).
 */
@Slf4j
@Component
public class ContentSafetyGuard {

    // PII 패턴 (실제 연락처·주민번호·주소 등)
    private static final List<Pattern> PII_PATTERNS = List.of(
        Pattern.compile("\\d{3}-\\d{3,4}-\\d{4}"),               // 전화번호
        Pattern.compile("\\d{6}-[1-4]\\d{6}"),                   // 주민등록번호
        Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"), // 이메일
        Pattern.compile("https?://[^\\s]{10,}"),                   // URL (긴 것)
        Pattern.compile("카카오톡\\s*아이디[\\s:]+\\S+"),          // 카카오 ID
        Pattern.compile("연락처[\\s:]+[\\d\\-]{10,}"),            // 연락처 패턴
        Pattern.compile("주소[\\s:]+[가-힣\\d\\s]{5,}[동|구|시]") // 주소 패턴
    );

    // 자해·위기 키워드 (CrisisDetector와 연동 방지)
    private static final List<String> CRISIS_KEYWORDS = List.of(
        "자살", "자해", "죽고싶", "죽어버릴", "극단적 선택", "목숨을 끊"
    );

    // 혐오·차별 키워드 — 단순 포함 매칭 (문맥 오탐이 없는 토큰만)
    private static final List<String> HATE_KEYWORDS = List.of(
        "장애인놈", "병신새끼", "찐따"
    );

    // 혐오·차별 문맥 패턴 (2026-06-11) — 짧은 토큰의 substring 오탐 제거:
    // · "씹": "읽씹/안읽씹/말 씹고"(무시하다)는 정상 구어 → 욕설 연결형만 차단
    // · "보지": "보지 않/도/말/마/못"(동사 활용)은 정상 → 명사(비속어) 용법만 차단
    // · "니거": "이거 니거야?"(네 것)는 정상 구어 → 복수 멸칭형만 차단
    private static final List<Pattern> HATE_PATTERNS = List.of(
        Pattern.compile("씹(?=[년놈새창할쓰])"),
        Pattern.compile("(?<![가-힣])보지(?!\\s*(?:않|도|말|마|못|는))"),
        Pattern.compile("니거(?=들)")
    );

    /**
     * Thread-plan / structured-output JSON이 댓글·글 본문으로 샌 경우.
     * 2026-08-11 인시던트: {@code { post: null, comments: [ { ref, parentRef, personaId, body } ] }}
     * 가 그대로 게시됨. 키 인용 여부·리터럴 {@code \\n}·잘린 JSON 모두 차단.
     */
    static boolean looksLikeStructuredSchemaLeak(String text) {
        if (text == null || text.isBlank()) return false;
        String n = text.trim().replace("\\n", "\n").replace("\\r", "\r");
        String t = n.trim();
        if (!t.startsWith("{")) return false;
        String lower = t.toLowerCase(Locale.ROOT);
        boolean hasPersona = lower.contains("personaid");
        boolean hasParentRef = lower.contains("parentref");
        boolean hasComments = lower.contains("\"comments\"") || lower.contains("comments:");
        boolean hasPostField = lower.contains("\"post\"") || lower.contains("post:")
                || lower.startsWith("{post");
        // Distinctive thread-plan field combos — normal Korean comments do not look like this.
        if (hasPersona && (hasParentRef || hasComments)) return true;
        if (hasParentRef && hasComments) return true;
        if (hasPostField && hasComments && (hasPersona || hasParentRef)) return true;
        return false;
    }

    private static final int MIN_LENGTH = 5;
    // POST 상한: OutputSanitizer(llm) MAX_POST=2000보다 여유 있게 설정해 sanitizer가 실질적 상한이 됨.
    // Phase 5에서 ai-user.limits.max-post/max-comment 환경변수로 통일 예정.
    // TODO Phase 5: @Value("${ai-user.limits.max-post:2200}") 로 교체
    private static final int MAX_LEN_POST    = 2200;
    private static final int MAX_LEN_COMMENT = 350;

    /** 콘텐츠 타입: executePost→POST, executeComment/executeReply→COMMENT */
    public enum ContentType { POST, COMMENT }

    public record GuardResult(boolean passed, String reason) {
        public static GuardResult ok() {
            return new GuardResult(true, null);
        }

        public static GuardResult blocked(String reason) {
            return new GuardResult(false, reason);
        }
    }

    public GuardResult check(String text, ContentType type) {
        if (text == null || text.isBlank()) {
            return GuardResult.blocked("EMPTY_TEXT");
        }
        // 제공자 오류/거절/누출 시그니처 — JSON SSOT 로더 위임 (최종 안전망: 인보커가 놓쳐도 여기서 게시 차단)
        LlmErrorSignatures sig = LlmErrorSignatures.get();
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (sig.containsSignature(lower)) {
            log.error("ContentSafetyGuard: LLM provider-error signature in content — BLOCKED. 토큰 부족·거절 의심.");
            return GuardResult.blocked("LLM_ERROR_SIGNATURE");
        }
        if (sig.hasInsufficientKorean(text)) {
            log.error("ContentSafetyGuard: insufficient Korean content (language-guard) — BLOCKED.");
            return GuardResult.blocked("INSUFFICIENT_KOREAN");
        }
        if (looksLikeStructuredSchemaLeak(text)) {
            log.error("ContentSafetyGuard: thread-plan/structured JSON schema leaked into content — BLOCKED.");
            return GuardResult.blocked("STRUCTURED_SCHEMA_LEAK");
        }
        if (sig.hasPromptLeak(text)) {
            log.error("ContentSafetyGuard: internal prompt/correction note leaked into content — BLOCKED.");
            return GuardResult.blocked("PROMPT_LEAK_META");
        }
        if (text.length() < MIN_LENGTH) {
            return GuardResult.blocked("TOO_SHORT");
        }
        int maxLen = (type == ContentType.POST) ? MAX_LEN_POST : MAX_LEN_COMMENT;
        if (text.length() > maxLen) {
            return GuardResult.blocked("TOO_LONG: " + text.length());
        }

        // PII 검사
        for (Pattern p : PII_PATTERNS) {
            if (p.matcher(text).find()) {
                log.warn("ContentSafetyGuard: PII pattern matched: {}", p.pattern());
                return GuardResult.blocked("PII_DETECTED");
            }
        }

        // 위기 키워드
        for (String kw : CRISIS_KEYWORDS) {
            if (text.contains(kw)) {
                log.warn("ContentSafetyGuard: crisis keyword detected: {}", kw);
                return GuardResult.blocked("CRISIS_KEYWORD");
            }
        }

        // 혐오 키워드
        for (String kw : HATE_KEYWORDS) {
            if (text.contains(kw)) {
                log.warn("ContentSafetyGuard: hate keyword detected: {}", kw);
                return GuardResult.blocked("HATE_KEYWORD");
            }
        }

        // 혐오 문맥 패턴 (substring 오탐 방지형)
        for (Pattern p : HATE_PATTERNS) {
            if (p.matcher(text).find()) {
                log.warn("ContentSafetyGuard: hate pattern matched: {}", p.pattern());
                return GuardResult.blocked("HATE_KEYWORD");
            }
        }

        return GuardResult.ok();
    }
}
