package com.againspring.llm.bridge.exception;

import com.againspring.llm.LLMException;

/**
 * Exception thrown when user input is rejected by sanitizer.
 */
public class LLMSanitizationException extends LLMException {

    public LLMSanitizationException(String message, String correlationId) {
        super("SANITIZATION_FAILED", message, correlationId);
    }

    public LLMSanitizationException(String message, Throwable cause, String correlationId) {
        super("SANITIZATION_FAILED", message, cause, correlationId);
    }
}
