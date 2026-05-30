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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Claude API provider — Anthropic Messages API + SSE 스트리밍 + 프롬프트 캐싱.
 *
 * 구조화 대화 경로: invokeCancelable(StructuredPrompt) → SSE 스트리밍
 *   - GLOBAL_STATIC bp1(1h TTL) + SESSION_STATIC bp2(5m) + HISTORY bp3(5m) → system[]
 *   - DYNAMIC → messages[0] user (캐시 없음)
 *   - 스트리밍 partial → CancelableInvocation.notifyPartial() → DB draft 저장
 *
 * 단순 호출 경로: invoke(String) / invokeCancelable(String) → 비스트리밍 (캐싱 없음)
 */
@Slf4j
@Component("claudeApiProvider")
public class ClaudeApiProvider implements LLMProvider {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String ANTHROPIC_BETA = "prompt-caching-2024-07-31";
    /** 한국어 1~3문장 = 최대 ~150 토큰. 출력 토큰은 캐싱 불가이므로 작을수록 직접 절감. */
    private static final int MAX_TOKENS = 256;

    @Value("${llm.claude-api.key:}")
    private String apiKey;

    @Value("${llm.claude-api.model:claude-haiku-4-5-20251001}")
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
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120)); // 스트리밍 전체 수신에 충분한 타임아웃
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.semaphore = new Semaphore(maxConcurrency);
        log.info("ClaudeApiProvider initialized: model={}, maxConcurrency={}, cacheEnabled={}, streaming=true",
                defaultModel, maxConcurrency, cacheEnabled);
    }

    // ── 구조화 호출 (스트리밍 + 캐싱) ─────────────────────────────────────────

    /**
     * 대화 경로 전용 — 스트리밍 SSE + cache_control breakpoint 3개.
     * partial 도착마다 invocation.notifyPartial() → CancelableChatService가 DB draft 갱신.
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

                    long callStart = System.currentTimeMillis();
                    AnthropicRequest request = buildStreamingRequest(prompt, model);
                    StreamedResult result = streamWithRetry(request, invocation);
                    long latencyMs = System.currentTimeMillis() - callStart;

                    if (invocation.isCanceled()) {
                        invocation.getResultFuture().completeExceptionally(
                            new InvocationCanceledException("Invocation canceled", invocationId));
                        return;
                    }

                    invocation.getResultFuture().complete(result.text());
                    logAndPersist(invocationId, sessionId, result, latencyMs);

                } finally {
                    semaphore.release();
                }
            } catch (InvocationCanceledException e) {
                invocation.getResultFuture().completeExceptionally(e);
            } catch (Exception e) {
                log.error("[ClaudeApi] structured streaming failed: {}", e.getMessage());
                invocation.getResultFuture().complete("처리 중에 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
            }
        });

        return invocation;
    }

    // ── SSE 스트리밍 ──────────────────────────────────────────────────────────

    private StreamedResult streamWithRetry(AnthropicRequest request, CancelableInvocation invocation) throws Exception {
        int[] delays = {1000, 2000, 4000, 8000};
        Exception lastEx = null;
        for (int attempt = 0; attempt <= delays.length; attempt++) {
            try {
                return doStream(request, invocation);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < delays.length) {
                    log.warn("Rate limited (429), retry in {}ms (attempt {})", delays[attempt], attempt + 1);
                    Thread.sleep(delays[attempt]);
                    lastEx = e;
                } else {
                    log.error("Claude API error ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
                    throw e;
                }
            } catch (Exception e) {
                if (attempt < delays.length) {
                    log.warn("Streaming failed (attempt {}), retry in {}ms: {}", attempt + 1, delays[attempt], e.getMessage());
                    Thread.sleep(delays[attempt]);
                    lastEx = e;
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("Exhausted streaming retries", lastEx);
    }

    /**
     * SSE 스트림 파싱 — content_block_delta 이벤트에서 텍스트를 누적하며 notifyPartial 호출.
     * message_start / message_delta에서 캐시 토큰 통계 수집.
     */
    private StreamedResult doStream(AnthropicRequest request, CancelableInvocation invocation) {
        StringBuilder accumulated = new StringBuilder();
        int[] stats = {0, 0, 0, 0}; // [inputTokens, outputTokens, cacheReadTokens, cacheCreationTokens]

        return restClient.post()
            .uri(API_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("anthropic-beta", ANTHROPIC_BETA)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange((req, resp) -> {
                if (!resp.getStatusCode().is2xxSuccessful()) {
                    throw new HttpClientErrorException(resp.getStatusCode());
                }
                try (var reader = new BufferedReader(
                        new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (invocation.isCanceled()) break;
                        if (!line.startsWith("data: ")) continue;
                        String data = line.substring(6).trim();
                        if (data.isEmpty()) continue;
                        try {
                            parseSseEvent(objectMapper.readTree(data), accumulated, stats, invocation);
                        } catch (Exception ignored) { /* skip malformed event */ }
                    }
                }
                return new StreamedResult(accumulated.toString(), stats[0], stats[1], stats[2], stats[3]);
            });
    }

    private void parseSseEvent(JsonNode node, StringBuilder accumulated, int[] stats, CancelableInvocation invocation) {
        switch (node.path("type").asText()) {
            case "content_block_delta" -> {
                String delta = node.path("delta").path("text").asText("");
                if (!delta.isEmpty()) {
                    accumulated.append(delta);
                    invocation.notifyPartial(accumulated.toString());
                }
            }
            case "message_start" -> {
                JsonNode u = node.path("message").path("usage");
                stats[0] = u.path("input_tokens").asInt(0);
                stats[2] = u.path("cache_read_input_tokens").asInt(0);
                stats[3] = u.path("cache_creation_input_tokens").asInt(0);
            }
            case "message_delta" -> stats[1] = node.path("usage").path("output_tokens").asInt(0);
        }
    }

    private record StreamedResult(String text, int inputTokens, int outputTokens,
                                  int cacheReadTokens, int cacheCreationTokens) {}

    // ── 프롬프트 빌드 ─────────────────────────────────────────────────────────

    private AnthropicRequest buildStreamingRequest(StructuredPrompt prompt, String model) {
        List<AnthropicTextBlock> systemBlocks = new ArrayList<>();
        appendTierWithBreakpoint(systemBlocks, prompt.getSegmentsByTier(CacheTier.GLOBAL_STATIC), true);
        appendTierWithBreakpoint(systemBlocks, prompt.getSegmentsByTier(CacheTier.SESSION_STATIC), false);
        appendTierWithBreakpoint(systemBlocks, prompt.getSegmentsByTier(CacheTier.HISTORY), false);

        String dynamicText = prompt.getSegmentsByTier(CacheTier.DYNAMIC).stream()
            .map(PromptSegment::getText)
            .collect(Collectors.joining());

        return AnthropicRequest.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
            .system(systemBlocks.isEmpty() ? null : systemBlocks)
            .messages(List.of(AnthropicMessage.user(dynamicText.isBlank() ? "." : dynamicText)))
            .stream(true)   // SSE 스트리밍 활성화
            .build();
    }

    /**
     * @param useLongTtl true → 1h TTL (GLOBAL_STATIC), false → 5m TTL (SESSION/HISTORY)
     */
    private void appendTierWithBreakpoint(List<AnthropicTextBlock> target,
                                          List<PromptSegment> segments,
                                          boolean useLongTtl) {
        if (segments.isEmpty()) return;
        for (int i = 0; i < segments.size(); i++) {
            boolean isLast = (i == segments.size() - 1);
            String text = segments.get(i).getText();
            if (isLast && cacheEnabled) {
                target.add(useLongTtl ? AnthropicTextBlock.cachedLong(text) : AnthropicTextBlock.cached(text));
            } else {
                target.add(AnthropicTextBlock.text(text));
            }
        }
    }

    // ── 로깅 ─────────────────────────────────────────────────────────────────

    private void logAndPersist(String invocationId, String sessionId, StreamedResult r, long latencyMs) {
        log.info("LLM_CALL provider=claude-api correlationId={} latencyMs={} " +
                 "inputTokens={} outputTokens={} cacheRead={} cacheCreate={} sessionId={}",
                 invocationId, latencyMs, r.inputTokens(), r.outputTokens(),
                 r.cacheReadTokens(), r.cacheCreationTokens(), sessionId);
        try {
            LLMRequest req = LLMRequest.builder()
                .correlationId(invocationId).userInput("")
                .metadata(Map.of("sessionId", sessionId != null ? sessionId : "", "provider", "claude-api"))
                .build();
            LLMResponse res = LLMResponse.builder()
                .rawText(r.text()).provider("claude-api").correlationId(invocationId)
                .latencyMs(latencyMs).tokensUsed(r.inputTokens() + r.outputTokens())
                .inputTokens(r.inputTokens()).outputTokens(r.outputTokens())
                .cacheReadTokens(r.cacheReadTokens() > 0 ? r.cacheReadTokens() : null)
                .cacheCreationTokens(r.cacheCreationTokens() > 0 ? r.cacheCreationTokens() : null)
                .isFallback(false).build();
            llmCallLogger.logCall(req, res, null);
        } catch (Exception e) {
            log.warn("[ClaudeApi] logCall failed: {}", e.getMessage());
        }
    }

    // ── 단순 호출 (비스트리밍, 비캐싱) ───────────────────────────────────────

    @Override
    public String invoke(String prompt, String model) throws Exception {
        if (!isHealthy()) throw new LLMException("UNHEALTHY", "Claude API key not configured", UUID.randomUUID().toString());
        String correlationId = UUID.randomUUID().toString();
        if (!semaphore.tryAcquire(5, TimeUnit.SECONDS))
            throw new LLMCapacityException("Claude API concurrency limit reached", correlationId);
        try {
            AnthropicResponse response = callWithRetry(AnthropicRequest.simple(model, MAX_TOKENS, prompt));
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
                    if (invocation.isCanceled()) { invocation.getResultFuture().completeExceptionally(new InvocationCanceledException("canceled", invocationId)); return; }
                    AnthropicResponse response = callWithRetry(AnthropicRequest.simple(model, MAX_TOKENS, prompt));
                    if (invocation.isCanceled()) { invocation.getResultFuture().completeExceptionally(new InvocationCanceledException("canceled", invocationId)); return; }
                    invocation.getResultFuture().complete(response.text());
                } finally { semaphore.release(); }
            } catch (InvocationCanceledException e) {
                invocation.getResultFuture().completeExceptionally(e);
            } catch (Exception e) {
                log.error("[ClaudeApi] string invocation failed: {}", e.getMessage());
                invocation.getResultFuture().complete("처리 중에 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
            }
        });
        return invocation;
    }

    @Override
    public LLMResponse invoke(LLMRequest request) throws LLMException {
        try {
            String prompt = request.getSystemPrompt() != null
                ? request.getSystemPrompt() + "\n" + request.getUserInput()
                : request.getUserInput();
            String text = invoke(prompt, defaultModel);
            return LLMResponse.builder().rawText(text).tokensUsed(estimateTokens(text))
                .latencyMs(0).provider("claude-api")
                .correlationId(request.getCorrelationId() != null ? request.getCorrelationId() : UUID.randomUUID().toString())
                .isFallback(false).build();
        } catch (Exception e) {
            throw new LLMException("INVOKE_FAILED", "Claude API call failed: " + e.getMessage(), e,
                request.getCorrelationId() != null ? request.getCorrelationId() : UUID.randomUUID().toString());
        }
    }

    @Override
    public CompletableFuture<LLMResponse> invokeAsync(LLMRequest request) {
        return CompletableFuture.supplyAsync(() -> { try { return invoke(request); } catch (LLMException e) { throw new RuntimeException(e); } });
    }

    @Override
    public String getProviderName() { return "claude-api"; }

    @Override
    public boolean isHealthy() { return apiKey != null && !apiKey.isBlank(); }

    // ── 비스트리밍 HTTP 호출 (단순 경로용) ───────────────────────────────────

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
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < delays.length) { Thread.sleep(delays[attempt]); lastEx = e; }
                else { log.error("Claude API error ({}): {}", e.getStatusCode(), e.getMessage()); throw e; }
            } catch (Exception e) {
                if (attempt < delays.length) { Thread.sleep(delays[attempt]); lastEx = e; }
                else throw e;
            }
        }
        throw new RuntimeException("Exhausted retries calling Claude API", lastEx);
    }

    private int estimateTokens(String text) { return Math.max(1, text.length() / 4); }
}
