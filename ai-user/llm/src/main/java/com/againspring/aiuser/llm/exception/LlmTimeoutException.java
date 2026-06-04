package com.againspring.aiuser.llm.exception;

public class LlmTimeoutException extends LlmException {
    public LlmTimeoutException(String message) {
        super("TIMEOUT", message);
    }
}
