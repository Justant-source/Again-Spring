package com.againspring.llm.claudeapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Anthropic Messages API request body.
 * @JsonInclude(NON_NULL) prevents null system field from being serialized —
 * Anthropic rejects "system": null with 400 "Input should be a valid array".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicRequest {
    private String model;

    @JsonProperty("max_tokens")
    private int maxTokens;

    /** system prompt blocks (with cache_control). Null-safe: omitted when null/empty. */
    private List<AnthropicTextBlock> system;

    private List<AnthropicMessage> messages;

    /** Create a minimal request for simple string invocation (no system, no caching). */
    public static AnthropicRequest simple(String model, int maxTokens, String userPrompt) {
        return AnthropicRequest.builder()
            .model(model)
            .maxTokens(maxTokens)
            // system intentionally omitted → Anthropic treats as no system prompt
            .messages(List.of(AnthropicMessage.user(userPrompt)))
            .build();
    }
}
