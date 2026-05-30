package com.againspring.llm.claudeapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Anthropic Messages API request body.
 * Structured with system prompts, message history, and model parameters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnthropicRequest {
    private String model;

    @JsonProperty("max_tokens")
    private int maxTokens;

    private List<AnthropicTextBlock> system;  // system prompt blocks (with cache_control)

    private List<AnthropicMessage> messages;  // user/assistant message history

    /**
     * Create a minimal request for simple text invocation.
     */
    public static AnthropicRequest simple(String model, int maxTokens, String userPrompt) {
        return AnthropicRequest.builder()
            .model(model)
            .maxTokens(maxTokens)
            .system(null)
            .messages(List.of(AnthropicMessage.user(userPrompt)))
            .build();
    }
}
