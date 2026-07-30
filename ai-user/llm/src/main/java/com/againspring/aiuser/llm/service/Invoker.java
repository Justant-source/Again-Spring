package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.exception.LlmException;
import com.againspring.aiuser.llm.pool.CancelableInvocation;

/**
 * LLM 텍스트 생성 인터페이스.
 * 런타임 구현체 선택은 InvokerRouter가 담당한다.
 */
public interface Invoker {

    /** 동기 invoke. */
    String invoke(String prompt, String model) throws LlmException;

    /**
     * One physical CLI attempt. Structured generation owns its one-retry policy,
     * so it must not inherit legacy Claude refusal retries or model fallback.
     */
    default String invokeSingleAttempt(String prompt, String model) throws LlmException {
        return invoke(prompt, model);
    }

    /**
     * One physical attempt with a provider-native output schema. Legacy callers
     * deliberately retain the no-schema overload; only v2 structured generation
     * may use this contract.
     */
    default String invokeSingleAttempt(String prompt, String model, StructuredOutputSchema schema) throws LlmException {
        return invokeSingleAttempt(prompt, model);
    }

    /** 취소 지원 invoke. */
    String invokeWithCancelSupport(String prompt, String model, CancelableInvocation inv) throws Exception;
}
