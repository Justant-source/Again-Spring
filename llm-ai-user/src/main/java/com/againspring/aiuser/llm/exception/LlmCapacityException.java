package com.againspring.aiuser.llm.exception;

public class LlmCapacityException extends LlmException {
    public LlmCapacityException(String message) {
        super("CAPACITY_EXCEEDED", message);
    }
}
