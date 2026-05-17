package com.againspring.llmworker.exception;

public class LlmTimeoutException extends LlmException {
    public LlmTimeoutException(String message) {
        super("TIMEOUT", message);
    }
}
