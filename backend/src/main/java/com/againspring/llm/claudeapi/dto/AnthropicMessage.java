package com.againspring.llm.claudeapi.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Anthropic Messages API message object.
 * Contains role (user/assistant) and content blocks.
 */
@Builder
public record AnthropicMessage(
    String role,                              // "user" or "assistant"
    List<AnthropicTextBlock> content          // list of text blocks
) {
    /**
     * Create a user message with a single text block.
     */
    public static AnthropicMessage user(String text) {
        return AnthropicMessage.builder()
            .role("user")
            .content(List.of(AnthropicTextBlock.text(text)))
            .build();
    }

    /**
     * Create an assistant message with a single text block.
     */
    public static AnthropicMessage assistant(String text) {
        return AnthropicMessage.builder()
            .role("assistant")
            .content(List.of(AnthropicTextBlock.text(text)))
            .build();
    }
}
