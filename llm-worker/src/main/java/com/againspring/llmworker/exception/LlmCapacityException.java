package com.againspring.llmworker.exception;

public class LlmCapacityException extends LlmException {
    public LlmCapacityException(String message) {
        super("CAPACITY_EXCEEDED", message);
    }
}
