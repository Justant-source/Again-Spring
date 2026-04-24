package com.againspring.llm.bridge.exception;

import com.againspring.llm.LLMException;

/**
 * Exception thrown when worker pool is saturated.
 */
public class LLMCapacityException extends LLMException {

    public LLMCapacityException(String message, String correlationId) {
        super("CAPACITY_EXCEEDED", message, correlationId);
    }

    public LLMCapacityException(String message, Throwable cause, String correlationId) {
        super("CAPACITY_EXCEEDED", message, cause, correlationId);
    }
}
