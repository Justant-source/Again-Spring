package com.againspring.llm;

import java.util.concurrent.CompletableFuture;

/**
 * LLM Provider interface for abstract LLM interactions.
 * Implementations: ClaudeCodeBridge, ClaudeAPIProvider (future), MockLLMProvider
 */
public interface LLMProvider {

    /**
     * Synchronous completion: invoke LLM and return response.
     */
    LLMResponse invoke(LLMRequest request) throws LLMException;

    /**
     * Asynchronous completion: non-blocking variant.
     */
    CompletableFuture<LLMResponse> invokeAsync(LLMRequest request);

    /**
     * Raw string invocation with explicit model selection.
     * Used by ChatService and other services that assemble prompts directly.
     */
    String invoke(String prompt, String model) throws Exception;

    /**
     * Provider identity for logging/monitoring.
     */
    String getProviderName();

    /**
     * Health check: confirm provider is accessible.
     */
    boolean isHealthy();
}
