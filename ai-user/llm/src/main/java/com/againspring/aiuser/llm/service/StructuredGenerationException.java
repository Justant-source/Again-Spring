package com.againspring.aiuser.llm.service;

/** The model returned text that cannot safely become a stored plan. */
public class StructuredGenerationException extends RuntimeException {
    public StructuredGenerationException(String message) { super(message); }
}
