package com.againspring.llm.bridge.exception;

import com.againspring.llm.LLMException;

/**
 * Exception thrown when LLM invocation exceeds timeout.
 */
public class LLMTimeoutException extends LLMException {

    public LLMTimeoutException(String message, String correlationId) {
        super("TIMEOUT", message, correlationId);
    }

    public LLMTimeoutException(String message, Throwable cause, String correlationId) {
        super("TIMEOUT", message, cause, correlationId);
    }
}
