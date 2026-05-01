package com.againspring.llm.bridge.exception;

import com.againspring.llm.LLMException;

/**
 * LLM 호출이 외부에서 취소됐을 때 발생.
 */
public class InvocationCanceledException extends LLMException {

    private final String invocationId;

    public InvocationCanceledException(String message, String invocationId) {
        super("INVOCATION_CANCELED", message, invocationId);
        this.invocationId = invocationId;
    }

    public String getInvocationId() {
        return invocationId;
    }
}
