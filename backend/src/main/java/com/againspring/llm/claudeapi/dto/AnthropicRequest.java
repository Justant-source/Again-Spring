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

    /**
     * SSE 스트리밍 활성화. null = false (필드 직렬화 생략).
     * true 설정 시 Anthropic이 text/event-stream으로 응답.
     */
    private Boolean stream;

    /** 단순 문자열 호출용 — 캐싱·스트리밍 없음 */
    public static AnthropicRequest simple(String model, int maxTokens, String userPrompt) {
        return AnthropicRequest.builder()
            .model(model)
            .maxTokens(maxTokens)
            .messages(List.of(AnthropicMessage.user(userPrompt)))
            .build();
    }
}
