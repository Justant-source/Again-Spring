package com.againspring.aiuser.llm.exception;

import lombok.Getter;

@Getter
public class ClaudeCodeException extends LlmException {
    private final int exitCode;
    private final String stderrExcerpt;

    public ClaudeCodeException(String errorCode, String message, int exitCode, String stderrExcerpt) {
        super(errorCode, message);
        this.exitCode = exitCode;
        this.stderrExcerpt = stderrExcerpt;
    }

    public boolean isThrottled() {
        if (stderrExcerpt == null) return false;
        String lower = stderrExcerpt.toLowerCase();
        return lower.contains("rate limit") || lower.contains("rate_limit")
                || lower.contains("usage limit") || lower.contains("too many requests")
                || exitCode == 429;
    }

    /** 세션 만료·조직 차단·키 무효 등 인증 실패로 분류된 오류인지 (errorCode는 LlmException.getErrorCode()). */
    public boolean isAuthFailure() {
        return "AUTH_ERROR".equals(getErrorCode());
    }
}
