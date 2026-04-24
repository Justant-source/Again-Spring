package com.againspring.llm;

/**
 * Base exception for LLM operations.
 */
public class LLMException extends RuntimeException {
    private final String errorCode;
    private final String correlationId;

    public LLMException(String errorCode, String message, String correlationId) {
        super(message);
        this.errorCode = errorCode;
        this.correlationId = correlationId;
    }

    public LLMException(String errorCode, String message, Throwable cause, String correlationId) {
        super(message, cause);
        this.errorCode = errorCode;
        this.correlationId = correlationId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
