package com.againspring.llmworker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InvokeResponse {
    private final String text;
    private final long latencyMs;
    private final String correlationId;
    private final String error;
    private final String errorType;

    public static InvokeResponse success(String text, long latencyMs, String correlationId) {
        return InvokeResponse.builder().text(text).latencyMs(latencyMs).correlationId(correlationId).build();
    }

    public static InvokeResponse capacity(String message) {
        return InvokeResponse.builder().error(message).errorType("CAPACITY").build();
    }

    public static InvokeResponse timeout(String correlationId) {
        return InvokeResponse.builder().error("LLM invocation timed out").errorType("TIMEOUT")
                .correlationId(correlationId).build();
    }

    public static InvokeResponse claudeError(String message, int exitCode, boolean throttled) {
        return InvokeResponse.builder().error(message)
                .errorType(throttled ? "THROTTLED" : "CLAUDE_ERROR").build();
    }
}
