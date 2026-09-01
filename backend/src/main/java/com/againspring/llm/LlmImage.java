package com.againspring.llm;

/**
 * Optional vision attachment for {@link LLMProvider#invoke(String, String, java.util.List)}.
 */
public record LlmImage(String mime, String base64) {
}
