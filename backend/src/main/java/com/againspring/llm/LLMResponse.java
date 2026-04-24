package com.againspring.llm;

import lombok.Builder;
import lombok.Value;
import java.util.Map;

/**
 * Immutable LLM response DTO.
 * Contains raw text, token usage, latency, provider name, correlation ID, metadata, and fallback flag.
 */
@Value
@Builder
public class LLMResponse {
    private String rawText;
    private int tokensUsed;
    private long latencyMs;
    private String provider;
    private String correlationId;
    @Builder.Default
    private Map<String, Object> metadata = Map.of();
    @Builder.Default
    private boolean isFallback = false;
}
