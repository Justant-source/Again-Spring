package com.againspring.aiuser.orchestrator.safety;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
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

    // 제공자(LLM) 오류 문자열 — 토큰/크레딧 소진 또는 프록시 라우팅 오류로 본문에 새는 텍스트.
    // 절대 prod에 게시 금지 (2026-06-07 인시던트 + 2026-06-10 Kiro/Claude 자기정체 인시던트). 모두 소문자.
    private static final List<String> LLM_ERROR_SIGNATURES = List.of(
        // 크레딧/쿼터 오류
        "credit balance", "too low to access", "purchase credits", "plans & billing",
        "usage limit", "reached your usage", "5-hour limit", "rate limit", "rate_limit",
        "overloaded", "invalid_request_error", "authentication_error", "api_error",
        "anthropic api", "insufficient credit", "too many requests",
        "service unavailable", "internal server error",
        // LLM 자기 정체 노출 / 역할극 거절 (프록시 라우팅 오류 등)
        "i'm kiro", "i am kiro", "저는 kiro", "kiro입니다",
        "i'm claude", "i am claude", "i'm an ai assistant", "저는 claude",
        "i can't discuss that", "i cannot roleplay", "i'm not able to roleplay",
        "not able to roleplay", "not set up to generate",
        "can't roleplay", "cannot roleplay as", "won't roleplay",
        "i need to be direct: i can't", "i need to be direct: i'm",
        "i need to clarify: i'm", "i need to be transparent",
        "i appreciate you", "i appreciate you sharing", "i appreciate you testing",
        "i'm an ai", "i am an ai", "as an ai", "저는 ai",
        // 2026-06-12 인시던트: 시그니처 미스로 거절문이 게시됨 (LlmErrorSignature와 동기 유지 — 절대규칙 #7)
        "can't help with this", "cannot help with this", "unable to help with",
        "i can't assist", "cannot assist with", "role-play as", "this is asking me to",
        "이 요청을 도와드릴 수 없", "요청을 도와드릴 수가 없", "죄송하지만 저는 이 요청",
        "이 프롬프트는", "프롬프트 인젝션",
        // 2026-06-18 언어-가드 보완: 시그니처 미스 방어용 보조 패턴
        "i can't fulfill", "i can't write this",
        "i can't write this comment", "i can't write this content", "i can't write this response",
        "i can't do this", "i appreciate the context", "i appreciate the detailed request",
        "i appreciate the detailed instructions", "these instructions ask me", "the instructions ask me",
        "actual operating online community", "operating online community",
        "authentic community member", "genuine community member", "real human user",
        "posing as a real user", "designed to appear authentic", "inauthentic engagement",
        "community participation", "이 요청은 도와드릴 수 없습니다", "이 요청은 수행할 수 없습니다",
        "실제 운영 중인", "실제 온라인 커뮤니티", "진정성 있는 사용자",
        "허위 정보 및 스푸핑", "조작된 커뮤니티 활동",
        "가짜 페르소나", "신원 위장", "사용자 조작", "진정성에 손상"
    );

    private static final int MIN_LENGTH = 5;
    // POST 상한: OutputSanitizer(llm) MAX_POST=2000보다 여유 있게 설정해 sanitizer가 실질적 상한이 됨.
    // Phase 5에서 ai-user.limits.max-post/max-comment 환경변수로 통일 예정.
    // TODO Phase 5: @Value("${ai-user.limits.max-post:2200}") 로 교체
    private static final int MAX_LEN_POST    = 2200;
    private static final int MAX_LEN_COMMENT = 350;
    private static final double MIN_KOREAN_RATIO = 0.10;
    private static final int MIN_KOREAN_CHECK_LEN = 20;

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

    /** 한국어 AI 콘텐츠에 한글이 사실상 없으면(비율<10%) 영어 거절·오류로 판정. */
    private static boolean hasInsufficientKorean(String text) {
        long significant = text.chars().filter(c -> c > 32).count();
        if (significant < MIN_KOREAN_CHECK_LEN) return false;
        long korean = text.chars().filter(c ->
                (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0x1100 && c <= 0x11FF)
                || (c >= 0x3130 && c <= 0x318F)).count();
        return (double) korean / significant < MIN_KOREAN_RATIO;
    }

    public GuardResult check(String text, ContentType type) {
        if (text == null || text.isBlank()) {
            return GuardResult.blocked("EMPTY_TEXT");
        }
        // 제공자 오류 문자열 차단 (최종 안전망: 인보커가 놓쳐도 여기서 게시 차단)
        String lower = text.toLowerCase();
        for (String sig : LLM_ERROR_SIGNATURES) {
            if (lower.contains(sig)) {
                log.error("ContentSafetyGuard: LLM provider-error signature in content — BLOCKED ('{}'). 토큰 부족 의심.", sig);
                return GuardResult.blocked("LLM_ERROR_SIGNATURE");
            }
        }
        // 언어 가드: 한국어 AI 콘텐츠에 한글이 사실상 없으면 무효 처리 (영어 거절문 방어)
        if (hasInsufficientKorean(text)) {
            log.error("ContentSafetyGuard: insufficient Korean content (language-guard) — BLOCKED.");
            return GuardResult.blocked("INSUFFICIENT_KOREAN");
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
