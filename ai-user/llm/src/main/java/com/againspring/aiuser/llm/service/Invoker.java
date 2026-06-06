package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.exception.LlmException;
import com.againspring.aiuser.llm.pool.CancelableInvocation;

/**
 * LLM 텍스트 생성 인터페이스.
 * CLI(ClaudeCliInvoker) 또는 API(ClaudeApiInvoker) 구현체 선택은 InvokerRouter가 담당.
 */
public interface Invoker {

    /** 동기 invoke. */
    String invoke(String prompt, String model) throws LlmException;

    /** 취소 지원 invoke. */
    String invokeWithCancelSupport(String prompt, String model, CancelableInvocation inv) throws Exception;
}
