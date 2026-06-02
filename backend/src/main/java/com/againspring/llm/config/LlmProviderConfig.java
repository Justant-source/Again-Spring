package com.againspring.llm.config;

import com.againspring.llm.LLMProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LLM Provider 설정 — Community (jury, neutralize) 전용.
 * 모든 경로: remote (againspring-llm 워커 HTTP 브릿지)
 */
@Slf4j
@Configuration
public class LlmProviderConfig {

    @Bean
    @Primary
    @Qualifier("composeLlmProvider")
    public LLMProvider composeLlmProvider(@Qualifier("remoteLlmProvider") LLMProvider remote) {
        log.info("Compose LLM provider: remote");
        return remote;
    }

    @Bean
    @Qualifier("juryLlmProvider")
    public LLMProvider juryLlmProvider(@Qualifier("remoteLlmProvider") LLMProvider remote) {
        log.info("Jury LLM provider: remote");
        return remote;
    }
}
