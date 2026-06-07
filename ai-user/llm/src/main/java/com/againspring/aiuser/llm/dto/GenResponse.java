package com.againspring.aiuser.llm.dto;

import lombok.*;

@Getter
@Builder
public class GenResponse {
    private final String text;
    /** 피기백 반응 JSON (comment/reply 전용). null이면 반응 없음. */
    private final String reactionsJson;
    private final long latencyMs;
    private final String correlationId;
    private final String error;
    private final String errorType;

    public static GenResponse success(String text, long latencyMs, String correlationId) {
        return GenResponse.builder()
            .text(text)
            .latencyMs(latencyMs)
            .correlationId(correlationId)
            .build();
    }

    /** comment/reply 전용 — reactionsJson 포함 버전. */
    public static GenResponse success(String text, String reactionsJson, long latencyMs, String correlationId) {
        return GenResponse.builder()
            .text(text)
            .reactionsJson(reactionsJson)
            .latencyMs(latencyMs)
            .correlationId(correlationId)
            .build();
    }

    public static GenResponse capacity(String message) {
        return GenResponse.builder()
            .error(message)
            .errorType("CAPACITY")
            .build();
    }

    public static GenResponse timeout(String correlationId) {
        return GenResponse.builder()
            .error("Generation timed out")
            .errorType("TIMEOUT")
            .correlationId(correlationId)
            .build();
    }

    public static GenResponse genError(String message) {
        return GenResponse.builder()
            .error(message)
            .errorType("GEN_ERROR")
            .build();
    }
}
