package com.againspring.llm;

/**
 * LLM Provider interface — Community (tonalization 등) 전용.
 * 구현체: RemoteLlmProvider (llm-worker HTTP 브릿지), MockLLMProvider (테스트)
 */
public interface LLMProvider {

    /**
     * 동기 텍스트 호출 — 프롬프트와 모델을 받아 완성 문자열 반환.
     */
    String invoke(String prompt, String model) throws Exception;

    /**
     * Provider 식별자 (로깅용).
     */
    String getProviderName();

    /**
     * 헬스 체크.
     */
    boolean isHealthy();
}
