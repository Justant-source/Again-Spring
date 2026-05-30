package com.againspring.llm.claudeapi;

import com.againspring.llm.LLMException;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.LLMRequest;
import com.againspring.llm.LLMResponse;
import com.againspring.llm.prompt.CacheTier;
import com.againspring.llm.prompt.PromptSegment;
import com.againspring.llm.prompt.StructuredPrompt;
import com.againspring.llm.bridge.CancelableInvocation;
import com.againspring.llm.bridge.exception.InvocationCanceledException;
import com.againspring.llm.bridge.exception.LLMCapacityException;
import com.againspring.llm.claudeapi.dto.AnthropicMessage;
import com.againspring.llm.claudeapi.dto.AnthropicRequest;
import com.againspring.llm.claudeapi.dto.AnthropicResponse;
import com.againspring.llm.claudeapi.dto.AnthropicTextBlock;
import com.againspring.llm.fallback.FallbackResponses;
import com.againspring.llm.monitoring.LLMCallLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Claude API provider using Anthropic Messages REST API.
 * Supports prompt caching (ephemeral) and concurrent request limiting via semaphore.
 *
 * Configuration (application.yml):
 *   llm.claude-api.key: ${ANTHROPIC_API_KEY}
 *   llm.claude-api.model: ${CLAUDE_MODEL:claude-sonnet-4-5}
 *   llm.claude-api.max-concurrency: 8
 *   llm.claude-api.cache.enabled: true
 */
@Slf4j
@Component("claudeApiProvider")
public class ClaudeApiProvider implements LLMProvider {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String ANTHROPIC_BETA = "prompt-caching-2024-07-31";
    private static final int MAX_TOKENS = 1024;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    @Value("${llm.claude-api.key:}")
    private String apiKey;

    @Value("${llm.claude-api.model:claude-sonnet-4-5}")
    private String defaultModel;

    @Value("${llm.claude-api.max-concurrency:8}")
    private int maxConcurrency;

    @Value("${llm.claude-api.cache.enabled:true}")
    private boolean cacheEnabled;

    private final ObjectMapper objectMapper;
    private final FallbackResponses fallbackResponses;
    private final LLMCallLogger llmCallLogger;

    private RestClient restClient;
    private Semaphore semaphore;

    public ClaudeApiProvider(ObjectMapper objectMapper,
                            FallbackResponses fallbackResponses,
                            LLMCallLogger llmCallLogger) {
        this.objectMapper = objectMapper;
        this.fallbackResponses = fallbackResponses;
        this.llmCallLogger = llmCallLogger;
    }

    @PostConstruct
    public void init() {
        this.restClient = RestClient.create();
        this.semaphore = new Semaphore(maxConcurrency);
        log.info("ClaudeApiProvider initialized: model={}, maxConcurrency={}, cacheEnabled={}",
                defaultModel, maxConcurrency, cacheEnabled);
    }

    @Override
    public LLMResponse invoke(LLMRequest request) throws LLMException {
        try {
            String prompt = request.getSystemPrompt() != null
                ? request.getSystemPrompt() + "\n" + request.getUserInput()
                : request.getUserInput();

            String response = invoke(prompt, defaultModel);
            String correlationId = request.getCorrelationId() != null
                ? request.getCorrelationId()
                : UUID.randomUUID().toString();

            return LLMResponse.builder()
                .rawText(response)
                .tokensUsed(estimateTokens(response))
                .latencyMs(0)
                .provider("claude-api")
                .correlationId(correlationId)
                .isFallback(false)
                .build();
        } catch (Exception e) {
            String correlationId = request.getCorrelationId() != null
                ? request.getCorrelationId()
                : UUID.randomUUID().toString();
            log.error("LLM invoke failed: {}", e.getMessage());
            throw new LLMException("INVOKE_FAILED", "Claude API call failed: " + e.getMessage(), e, correlationId);
        }
    }

    @Override
    public CompletableFuture<LLMResponse> invokeAsync(LLMRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return invoke(request);
            } catch (LLMException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 구조화 프롬프트 취소 가능 호출 — cache_control breakpoint 3개 적용.
     *
     * 매핑 전략 (HISTORY가 단일 텍스트 블록이므로 system[]에 배치):
     *   system[0] GLOBAL_STATIC  → bp1 (세션 간 재사용)
     *   system[1] SESSION_STATIC → bp2 (세션 내 고정)
     *   system[2] HISTORY        → bp3 (매 턴 증분 캐싱)
     *   messages[0] DYNAMIC      → user 메시지 (캐시 없음, 매 턴 변동)
     */
    @Override
    public CancelableInvocation invokeCancelable(StructuredPrompt prompt, String model, String sessionId) {
        String invocationId = UUID.randomUUID().toString();
        CancelableInvocation invocation = new CancelableInvocation(invocationId, sessionId);

        CompletableFuture.runAsync(() -> {
            try {
                if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    invocation.getResultFuture().completeExceptionally(
                        new LLMCapacityException("claude-api concurrency limit reached", invocationId));
                    return;
                }
                try {
                    if (invocation.isCanceled()) {
                        invocation.getResultFuture().completeExceptionally(
                            new InvocationCanceledException("Invocation canceled", invocationId));
                        return;
                    }

                    AnthropicRequest request = buildStructuredRequest(prompt, model);
                    AnthropicResponse response = callWithRetry(request);

                    if (invocation.isCanceled()) {
                        invocation.getResultFuture().completeExceptionally(
                            new InvocationCanceledException("Invocation canceled", invocationId));
                        return;
                    }

                    invocation.getResultFuture().complete(response.text());
                    log.debug("ClaudeApi structured: id={} cacheRead={} cacheCreate={}",
                        invocationId, response.cacheReadInputTokens(), response.cacheCreationInputTokens());

                } finally {
                    semaphore.release();
                }
            } catch (InvocationCanceledException e) {
                invocation.getResultFuture().completeExceptionally(e);
            } catch (Exception e) {
                log.error("[ClaudeApi] structured invocation failed: {}", e.getMessage());
                invocation.getResultFuture().complete("처리 중에 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
            }
        });

        return invocation;
    }

    // -------------------------------------------------------------------------

    /**
     * StructuredPrompt → Anthropic Messages API 구조로 매핑.
     * GLOBAL_STATIC + SESSION_STATIC + HISTORY = system[] (breakpoint 각 tier 끝에)
     * DYNAMIC = 단일 user messages[0]
     */
    private AnthropicRequest buildStructuredRequest(StructuredPrompt prompt, String model) {
        List<AnthropicTextBlock> systemBlocks = new ArrayList<>();

        appendTierWithBreakpoint(systemBlocks, prompt.getSegmentsByTier(CacheTier.GLOBAL_STATIC));
        appendTierWithBreakpoint(systemBlocks, prompt.getSegmentsByTier(CacheTier.SESSION_STATIC));
        appendTierWithBreakpoint(systemBlocks, prompt.getSegmentsByTier(CacheTier.HISTORY));

        String dynamicText = prompt.getSegmentsByTier(CacheTier.DYNAMIC).stream()
            .map(PromptSegment::getText)
            .collect(Collectors.joining());

        return AnthropicRequest.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
            .system(systemBlocks.isEmpty() ? null : systemBlocks)
            .messages(List.of(AnthropicMessage.user(dynamicText.isBlank() ? "." : dynamicText)))
            .build();
    }

    /** tier 세그먼트들을 systemBlocks에 추가; 마지막 세그먼트에 cache_control 부착 */
    private void appendTierWithBreakpoint(List<AnthropicTextBlock> target, List<PromptSegment> segments) {
        if (segments.isEmpty()) return;
        for (int i = 0; i < segments.size(); i++) {
            boolean isLast = (i == segments.size() - 1);
            String text = segments.get(i).getText();
            target.add(isLast && cacheEnabled
                ? AnthropicTextBlock.cached(text)
                : AnthropicTextBlock.text(text));
        }
    }

    @Override
    public String invoke(String prompt, String model) throws Exception {
        if (!isHealthy()) {
            String correlationId = UUID.randomUUID().toString();
            throw new LLMException("UNHEALTHY", "Claude API key not configured", correlationId);
        }

        String correlationId = UUID.randomUUID().toString();
        if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
            throw new LLMCapacityException("Claude API concurrency limit reached", correlationId);
        }

        try {
            AnthropicRequest request = AnthropicRequest.simple(model, MAX_TOKENS, prompt);
            AnthropicResponse response = callWithRetry(request);
            return response.text();
        } finally {
            semaphore.release();
        }
    }

    @Override
    public CancelableInvocation invokeCancelable(String prompt, String model, String sessionId) {
        String invocationId = UUID.randomUUID().toString();
        CancelableInvocation invocation = new CancelableInvocation(invocationId, sessionId);

        CompletableFuture.runAsync(() -> {
            try {
                if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    invocation.getResultFuture().completeExceptionally(
                        new LLMCapacityException("claude-api concurrency limit reached", invocationId));
                    return;
                }

                try {
                    if (invocation.isCanceled()) {
                        invocation.getResultFuture().completeExceptionally(
                            new InvocationCanceledException("Invocation canceled", invocationId));
                        return;
                    }

                    AnthropicRequest request = AnthropicRequest.simple(model, MAX_TOKENS, prompt);
                    AnthropicResponse response = callWithRetry(request);

                    if (invocation.isCanceled()) {
                        invocation.getResultFuture().completeExceptionally(
                            new InvocationCanceledException("Invocation canceled", invocationId));
                        return;
                    }

                    String text = response.text();
                    invocation.getResultFuture().complete(text);

                    log.debug("ClaudeApi invocation completed: id={}, cacheRead={}, cacheCreate={}",
                        invocationId, response.cacheReadInputTokens(), response.cacheCreationInputTokens());

                } finally {
                    semaphore.release();
                }
            } catch (InvocationCanceledException e) {
                invocation.getResultFuture().completeExceptionally(e);
            } catch (Exception e) {
                log.error("[ClaudeApi] invocation failed: {}", e.getMessage());
                // Fallback to static response
                invocation.getResultFuture().complete(
                    "처리 중에 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
            }
        });

        return invocation;
    }

    @Override
    public String getProviderName() {
        return "claude-api";
    }

    @Override
    public boolean isHealthy() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Build Anthropic API request from simple prompt string.
     * For advanced usage with structured prompts, override this method.
     */
    private AnthropicRequest buildSimpleRequest(String prompt, String model) {
        return AnthropicRequest.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
            .system(null)
            .messages(List.of(AnthropicMessage.user(prompt)))
            .build();
    }

    /**
     * Call Anthropic API with exponential backoff retry on rate limits (429).
     */
    private AnthropicResponse callWithRetry(AnthropicRequest request) throws Exception {
        int[] delays = {1000, 2000, 4000, 8000};
        Exception lastEx = null;

        for (int attempt = 0; attempt <= delays.length; attempt++) {
            try {
                return restClient.post()
                    .uri(API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("anthropic-beta", ANTHROPIC_BETA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AnthropicResponse.class);
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < delays.length) {
                    log.warn("Rate limited by Claude API (429), retrying in {}ms...", delays[attempt]);
                    Thread.sleep(delays[attempt]);
                    lastEx = e;
                    continue;
                }
                log.error("Claude API error ({}): {}", e.getStatusCode(), e.getMessage());
                throw e;
            } catch (Exception e) {
                if (attempt < delays.length) {
                    log.warn("Claude API call failed (attempt {}), retrying in {}ms: {}",
                        attempt + 1, delays[attempt], e.getMessage());
                    Thread.sleep(delays[attempt]);
                    lastEx = e;
                } else {
                    throw e;
                }
            }
        }

        throw new RuntimeException("Exhausted retries calling Claude API", lastEx);
    }

    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }
}
