package com.againspring.llm.claudeapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Anthropic Messages API response body.
 * Contains generated content, usage metrics, and cache read statistics.
 */
@Builder
public record AnthropicResponse(
    String id,
    String type,                              // "message"
    String model,
    @JsonProperty("stop_reason") String stopReason,  // "end_turn", "stop_sequence", etc.
    List<AnthropicTextBlock> content,        // response text blocks
    UsageBlock usage                          // token usage and cache metrics
) {
    /**
     * Usage statistics including cache read/creation tokens.
     * API 2025 기준 신규 중첩 포맷 (ephemeral_5m / ephemeral_1h) 병행 지원.
     */
    @Builder
    public record UsageBlock(
        @JsonProperty("input_tokens") int inputTokens,
        @JsonProperty("output_tokens") int outputTokens,
        @JsonProperty("cache_read_input_tokens") Integer cacheReadInputTokens,
        @JsonProperty("cache_creation_input_tokens") Integer cacheCreationInputTokens,
        /** 신규 중첩 포맷 — TTL별 캐시 생성량 */
        @JsonProperty("cache_creation") CacheCreationDetail cacheCreation
    ) {
        @Builder
        public record CacheCreationDetail(
            @JsonProperty("ephemeral_5m_input_tokens") Integer ephemeral5m,
            @JsonProperty("ephemeral_1h_input_tokens") Integer ephemeral1h
        ) {}
    }

    /**
     * Extract all text content from response blocks.
     * Joins multiple text blocks with empty string.
     */
    public String text() {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.stream()
            .filter(b -> b.type() != null && b.type().equals("text"))
            .map(AnthropicTextBlock::text)
            .collect(Collectors.joining());
    }

    /**
     * Get cache read input tokens, defaulting to 0 if null.
     */
    public int cacheReadInputTokens() {
        return usage != null && usage.cacheReadInputTokens != null
            ? usage.cacheReadInputTokens
            : 0;
    }

    /**
     * Get cache creation input tokens, defaulting to 0 if null.
     */
    public int cacheCreationInputTokens() {
        return usage != null && usage.cacheCreationInputTokens != null
            ? usage.cacheCreationInputTokens
            : 0;
    }

    /**
     * Calculate total input tokens including cache read tokens.
     */
    public int totalInputTokens() {
        if (usage == null) return 0;
        return usage.inputTokens + cacheReadInputTokens();
    }
}
