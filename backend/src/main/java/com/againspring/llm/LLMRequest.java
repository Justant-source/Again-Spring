package com.againspring.llm;

import lombok.Builder;
import lombok.Value;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Immutable LLM request DTO.
 * Contains system prompt, prompt layers, user input, timeout, correlation ID, and metadata.
 */
@Value
@Builder
public class LLMRequest {
    private String systemPrompt;
    private List<PromptLayer> layers;
    private String userInput;
    @Builder.Default
    private Duration timeout = Duration.ofSeconds(30);
    private String correlationId;
    @Builder.Default
    private Map<String, Object> metadata = Map.of();
}
