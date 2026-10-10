package com.againspring.llm;

import java.util.List;

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
     * 동기 호출 + 선택 이미지(Haiku vision). 이미지가 없으면 텍스트 invoke.
     * 워커 vision이 아직 없으면 구현체는 {@link UnsupportedOperationException}을 던질 수 있다
     * (호출측 {@code VISION_FAIL}). RemoteLlmProvider는 이미지를 워커로 전달한다.
     */
    default String invoke(String prompt, String model, List<LlmImage> images) throws Exception {
        if (images == null || images.isEmpty()) {
            return invoke(prompt, model);
        }
        throw new UnsupportedOperationException("VISION_UNAVAILABLE");
    }

    /**
     * 동기 호출 + 선택 이미지 + Claude CLI {@code --effort}.
     * effort가 없으면 구현체는 플래그를 생략한다. 기본 구현은 effort를 무시하고 이미지 invoke로 떨어진다.
     */
    default String invoke(String prompt, String model, List<LlmImage> images, String effort) throws Exception {
        return invoke(prompt, model, images);
    }

    /**
     * Provider 식별자 (로깅용).
     */
    String getProviderName();

    /**
     * 헬스 체크.
     */
    boolean isHealthy();
}
