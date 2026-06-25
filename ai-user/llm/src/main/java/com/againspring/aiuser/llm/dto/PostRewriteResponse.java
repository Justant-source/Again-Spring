package com.againspring.aiuser.llm.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostRewriteResponse {
    private final String title;
    private final String body;
    private final long latencyMs;
    private final String correlationId;
    private final String error;
    private final String errorType;

    public static PostRewriteResponse success(String title, String body, long latencyMs, String correlationId) {
        return PostRewriteResponse.builder()
            .title(title)
            .body(body)
            .latencyMs(latencyMs)
            .correlationId(correlationId)
            .build();
    }

    public static PostRewriteResponse capacity(String message) {
        return PostRewriteResponse.builder()
            .error(message)
            .errorType("CAPACITY")
            .build();
    }

    public static PostRewriteResponse timeout(String correlationId) {
        return PostRewriteResponse.builder()
            .error("Rewrite timed out")
            .errorType("TIMEOUT")
            .correlationId(correlationId)
            .build();
    }

    public static PostRewriteResponse rewriteError(String message, String correlationId) {
        return PostRewriteResponse.builder()
            .error(message)
            .errorType("REWRITE_ERROR")
            .correlationId(correlationId)
            .build();
    }
}
