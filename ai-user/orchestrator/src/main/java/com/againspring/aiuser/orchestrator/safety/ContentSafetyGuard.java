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

    // 혐오·차별 키워드
    private static final List<String> HATE_KEYWORDS = List.of(
        "장애인놈", "병신새끼", "보지", "씹", "니거", "찐따"
    );

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

        return GuardResult.ok();
    }
}
