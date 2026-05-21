package com.againspring.llm.remote;

import com.againspring.llm.*;
import com.againspring.llm.bridge.CancelableInvocation;
import com.againspring.llm.bridge.PromptSanitizer;
import com.againspring.llm.bridge.exception.ClaudeCodeException;
import com.againspring.llm.bridge.exception.LLMCapacityException;
import com.againspring.llm.bridge.exception.LLMTimeoutException;
import com.againspring.llm.remote.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM Provider 구현 — 외부 againspring-llm 워커 HTTP 호출.
 * CancelableChatService/ChatService는 LLMProvider 인터페이스만 의존 → 무수정.
 * PromptSanitizer + 프롬프트 어셈블은 backend에서 수행, 워커는 완성 프롬프트만 수신.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "remote")
public class RemoteLlmProvider implements LLMProvider {

    private final PromptSanitizer sanitizer;
    private final RestClient restClient;
    private final String workerBaseUrl;
    private final long defaultTimeoutMs;
    private final long pollWaitMs;
    private final String defaultModel;

    // 모든 RemoteCancelableInvocation의 long-poll을 단일 스케줄러로 관리
    private final ScheduledExecutorService poller = new ScheduledThreadPoolExecutor(4, r -> {
        Thread t = new Thread(r, "remote-inv-poller-" + pollerId.getAndIncrement());
        t.setDaemon(true);
        return t;
    });
    private static final AtomicLong pollerId = new AtomicLong(0);

    public RemoteLlmProvider(
            PromptSanitizer sanitizer,
            @Value("${llm.remote.base-url:http://againspring-llm-dev:8090}") String workerBaseUrl,
            @Value("${llm.remote.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${llm.remote.read-timeout-ms:130000}") int readTimeoutMs,
            @Value("${llm.remote.default-timeout-ms:120000}") long defaultTimeoutMs,
            @Value("${llm.remote.poll-wait-ms:25000}") long pollWaitMs,
            @Value("${llm.claude-code.model:claude-haiku-4-5-20251001}") String defaultModel) {
        this.sanitizer = sanitizer;
        this.workerBaseUrl = workerBaseUrl;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.pollWaitMs = pollWaitMs;
        this.defaultModel = defaultModel;
        this.restClient = RestClient.builder()
                .baseUrl(workerBaseUrl)
                .build();
    }

    @Override
    public LLMResponse invoke(LLMRequest request) throws LLMException {
        String correlationId = request.getCorrelationId() != null
                ? request.getCorrelationId() : UUID.randomUUID().toString();
        long start = System.currentTimeMillis();

        String safeInput = sanitizer.sanitize(request.getUserInput(), correlationId);
        String finalPrompt = assembleFinalPrompt(request.getSystemPrompt(), request.getLayers(), safeInput);
        long timeoutMs = request.getTimeout() != null
                ? request.getTimeout().toMillis() : defaultTimeoutMs;

        try {
            String text = callWorkerInvoke(finalPrompt, defaultModel, timeoutMs, correlationId);
            long latency = System.currentTimeMillis() - start;
            return LLMResponse.builder()
                    .rawText(text).tokensUsed(text.length() / 4).latencyMs(latency)
                    .provider(getProviderName()).correlationId(correlationId).isFallback(false)
                    .build();
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            throw new ClaudeCodeException("UNKNOWN_ERROR", e.getMessage(), correlationId, -1,
                    e.getMessage() != null && e.getMessage().length() > 500
                            ? e.getMessage().substring(0, 500) : e.getMessage());
        }
    }

    @Override
    public CompletableFuture<LLMResponse> invokeAsync(LLMRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try { return invoke(request); }
            catch (LLMException e) { throw new CompletionException(e); }
        });
    }

    /**
     * ChatService/ReportGenerationService가 사용하는 raw string 경로.
     */
    @Override
    public String invoke(String prompt, String model) throws Exception {
        return callWorkerInvoke(prompt, model, defaultTimeoutMs, UUID.randomUUID().toString());
    }

    /**
     * CancelableChatService가 사용하는 취소 가능 경로.
     * POST /v1/invocations → invocationId → RemoteCancelableInvocation 반환.
     * resultFuture는 poller가 GET /v1/invocations/{id}/result long-poll로 채움.
     */
    @Override
    public CancelableInvocation invokeCancelable(String prompt, String model, String sessionId) {
        String correlationId = UUID.randomUUID().toString();
        WorkerCreateInvocationRequest req = WorkerCreateInvocationRequest.builder()
                .prompt(prompt).model(model).sessionId(sessionId).timeoutMs(defaultTimeoutMs)
                .build();

        WorkerCreateInvocationResponse resp;
        try {
            resp = restClient.post()
                    .uri("/v1/invocations")
                    .body(req)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new LLMCapacityException("Worker rejected invocation (429/4xx)", correlationId);
                    })
                    .body(WorkerCreateInvocationResponse.class);
        } catch (LLMCapacityException e) {
            RemoteCancelableInvocation failedInv = new RemoteCancelableInvocation(
                    correlationId, sessionId, restClient, workerBaseUrl, pollWaitMs);
            failedInv.getResultFuture().completeExceptionally(e);
            return failedInv;
        } catch (Exception e) {
            log.error("Failed to create remote invocation: {}", e.getMessage());
            RemoteCancelableInvocation failedInv = new RemoteCancelableInvocation(
                    correlationId, sessionId, restClient, workerBaseUrl, pollWaitMs);
            failedInv.getResultFuture().completeExceptionally(e);
            return failedInv;
        }

        String invocationId = resp != null ? resp.getInvocationId() : correlationId;
        RemoteCancelableInvocation inv = new RemoteCancelableInvocation(
                invocationId, sessionId, restClient, workerBaseUrl, pollWaitMs);

        startPolling(inv);
        return inv;
    }

    @Override
    public String getProviderName() {
        return "remote";
    }

    @Override
    public boolean isHealthy() {
        try {
            restClient.get().uri("/actuator/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("LLM worker health check failed: {}", e.getMessage());
            return false;
        }
    }

    // ── 내부 메서드 ──────────────────────────────────────────────────────────────

    private String callWorkerInvoke(String prompt, String model, long timeoutMs, String correlationId)
            throws LLMException {
        WorkerInvokeRequest req = WorkerInvokeRequest.builder()
                .prompt(prompt).model(model).timeoutMs(timeoutMs).correlationId(correlationId)
                .build();

        WorkerInvokeResponse resp;
        try {
            resp = restClient.post()
                    .uri("/v1/invoke")
                    .body(req)
                    .retrieve()
                    .onStatus(status -> status.value() == 429, (request, response) -> {
                        throw new LLMCapacityException("Worker queue full", correlationId);
                    })
                    .onStatus(status -> status.value() == 504, (request, response) -> {
                        throw new LLMTimeoutException("Worker invocation timed out", correlationId);
                    })
                    .onStatus(status -> status.value() == 502, (request, response) -> {
                        throw new ClaudeCodeException("CLAUDE_ERROR", "Worker: Claude CLI error",
                                correlationId, -1, "502 Bad Gateway from worker");
                    })
                    .body(WorkerInvokeResponse.class);
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            log.error("Worker invoke HTTP error: {}", e.getMessage());
            throw new ClaudeCodeException("UNKNOWN_ERROR", e.getMessage(), correlationId, -1,
                    e.getMessage() != null && e.getMessage().length() > 500
                            ? e.getMessage().substring(0, 500) : e.getMessage());
        }

        if (resp == null || resp.getText() == null) {
            throw new ClaudeCodeException("EMPTY_RESPONSE", "Worker returned empty response",
                    correlationId, -1, null);
        }
        return resp.getText();
    }

    /**
     * 단일 poller ScheduledExecutorService로 long-poll 반복.
     * resultFuture 완료 시 폴링 중단.
     */
    private void startPolling(RemoteCancelableInvocation inv) {
        scheduleNextPoll(inv, 0L);
    }

    private void scheduleNextPoll(RemoteCancelableInvocation inv, long delayMs) {
        if (!inv.isPollingActive()) return;

        ScheduledFuture<?> task = poller.schedule(() -> {
            if (!inv.isPollingActive()) return;
            try {
                WorkerInvocationResultResponse result = restClient.get()
                        .uri("/v1/invocations/" + inv.getInvocationId()
                                + "/result?waitMs=" + inv.getPollWaitMs())
                        .retrieve()
                        .body(WorkerInvocationResultResponse.class);

                if (result != null) {
                    inv.applyResult(result);
                    if ("PENDING".equals(result.getStatus())) {
                        scheduleNextPoll(inv, 0L);
                    } else if ("STREAMING".equals(result.getStatus())) {
                        scheduleNextPoll(inv, 2000L);
                    }
                } else {
                    scheduleNextPoll(inv, 0L);
                }
            } catch (Exception e) {
                if (inv.isPollingActive()) {
                    log.warn("Poll error for inv={}: {} — retry", inv.getInvocationId(), e.getMessage());
                    scheduleNextPoll(inv, 0L);
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);

        inv.setPollTask(task);
    }

    private String assembleFinalPrompt(String systemPrompt, List<PromptLayer> layers, String userInput) {
        StringBuilder sb = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append("<system>\n").append(systemPrompt).append("\n</system>\n\n");
        }
        if (layers != null && !layers.isEmpty()) {
            sb.append("<context>\n");
            layers.stream()
                    .sorted((a, b) -> Integer.compare(a.order(), b.order()))
                    .forEach(l -> sb.append(l.content()).append("\n"));
            sb.append("</context>\n\n");
        }
        if (userInput != null && !userInput.isBlank()) {
            sb.append("<user_input>\n").append(userInput).append("\n</user_input>\n");
        }
        return sb.toString();
    }
}
