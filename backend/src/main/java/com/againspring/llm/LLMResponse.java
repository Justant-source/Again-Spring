package com.againspring.llm;

import lombok.Builder;
import lombok.Value;
import java.util.Map;

/**
 * Immutable LLM response DTO.
 * Contains raw text, token usage, latency, provider name, correlation ID, metadata, and fallback flag.
 * Cache token fields (cacheReadTokens, cacheCreationTokens, inputTokens, outputTokens) are only
 * populated by the claude-api provider; CLI/remote paths leave them null.
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

    /** Anthropic cache_read_input_tokens — claude-api provider 전용, 나머지 null */
    private Integer cacheReadTokens;
    /** Anthropic cache_creation_input_tokens — claude-api provider 전용, 나머지 null */
    private Integer cacheCreationTokens;
    /** 실제 입력 토큰 수 — claude-api provider 전용, 나머지 null */
    private Integer inputTokens;
    /** 실제 출력 토큰 수 — claude-api provider 전용, 나머지 null */
    private Integer outputTokens;
}
