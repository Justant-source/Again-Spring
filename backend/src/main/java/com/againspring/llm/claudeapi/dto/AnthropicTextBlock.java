package com.againspring.llm.claudeapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Anthropic Messages API text block with optional cache_control.
 * Supports ephemeral prompt caching for efficiency.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record AnthropicTextBlock(
    String type,           // always "text"
    String text,
    @JsonProperty("cache_control") CacheControl cacheControl  // null = no caching
) {
    /**
     * Cache control directive for prompt caching.
     * type = "ephemeral" enables cache write on this block.
     */
    @Builder
    public record CacheControl(String type) {}

    /**
     * Create an uncached text block.
     */
    public static AnthropicTextBlock text(String text) {
        return AnthropicTextBlock.builder()
            .type("text")
            .text(text)
            .cacheControl(null)
            .build();
    }

    /**
     * Create a cached text block with ephemeral cache control.
     */
    public static AnthropicTextBlock cached(String text) {
        return AnthropicTextBlock.builder()
            .type("text")
            .text(text)
            .cacheControl(new CacheControl("ephemeral"))
            .build();
    }
}
