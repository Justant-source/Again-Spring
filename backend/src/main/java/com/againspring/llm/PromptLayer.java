package com.againspring.llm;

/**
 * Immutable record representing a single prompt layer.
 * Layers are assembled in order by PromptAssembler.
 */
public record PromptLayer(String name, String content, int order) {
}
