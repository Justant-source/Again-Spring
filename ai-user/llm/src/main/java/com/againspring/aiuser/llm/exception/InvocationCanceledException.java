package com.againspring.aiuser.llm.exception;

import lombok.Getter;

@Getter
public class InvocationCanceledException extends LlmException {
    private final String invocationId;

    public InvocationCanceledException(String message, String invocationId) {
        super("INVOCATION_CANCELED", message);
        this.invocationId = invocationId;
    }
}
