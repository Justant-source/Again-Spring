package com.againspring.aiuser.orchestrator.safety;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 생성 텍스트가 "깨진 LLM 출력"(오류·거절·누출·한글부족·길이)인지 검사한다.
 * 콘텐츠·표현은 검열하지 않는다(절대 규칙 #3).
 * 봇 생성 콘텐츠에만 적용 (실유저 입력에는 적용 금지).
 */
@Slf4j
@Component
public class ContentSafetyGuard {

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
    /** backend PostCreateRequest.bodyRaw @Size(max=1000) · CommentRequest.body @Size(max=1000)와 동일 — SSOT는 backend DTO. */
    @org.springframework.beans.factory.annotation.Value("${ai-user.limits.max-post:1000}")
    private int maxLenPost = 1000;
    @org.springframework.beans.factory.annotation.Value("${ai-user.limits.max-comment:1000}")
    private int maxLenComment = 1000;

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
        int maxLen = (type == ContentType.POST) ? maxLenPost : maxLenComment;
        if (text.length() > maxLen) {
            return GuardResult.blocked("TOO_LONG: " + text.length());
        }

        return GuardResult.ok();
    }
}
