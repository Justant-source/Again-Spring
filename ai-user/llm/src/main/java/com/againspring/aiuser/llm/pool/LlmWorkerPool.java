package com.againspring.aiuser.llm.pool;

import com.againspring.aiuser.llm.dto.WorkerMetrics;
import com.againspring.aiuser.llm.exception.*;
import com.againspring.aiuser.llm.service.ClaudeCliInvoker;
import com.againspring.aiuser.llm.service.InvokerRouter;
import com.againspring.aiuser.llm.service.LlmProvider;
import com.againspring.aiuser.llm.service.ProviderHealthRegistry;
import com.againspring.aiuser.llm.service.StructuredOutputSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM 워커 풀: ThreadPoolExecutor + bounded LinkedBlockingQueue.
 * - 동시 실행 상한: LLM_POOL_SIZE (기본 20)
 * - 초과 요청: 큐에서 대기 (기존 fail-fast Semaphore → blocking queue로 전환)
 * - 큐 용량 초과 시: 즉시 429 CAPACITY (진짜 과부하)
 * - 큐-대기 타임아웃: 워커 픽업 시 enqueue 경과 시간 > queueWaitTimeoutMs → CAPACITY 실패
 * - 실행 타임아웃: 모든 경로에서 timeoutMs 강제 (취소 경로 포함 — 기존 결함 해소)
 * - 취소 가능 invocations: ConcurrentHashMap<invocationId, CancelableInvocation>
 */
@Slf4j
@Component
public class LlmWorkerPool {

    @Value("${llm.worker.pool-size:20}")
    private int poolSize;

    @Value("${llm.worker.queue-capacity:100}")
    private int queueCapacity;

    @Value("${llm.worker.queue-wait-timeout-ms:30000}")
    private long queueWaitTimeoutMs;

    @Value("${llm.worker.default-timeout-ms:600000}")
    private long defaultTimeoutMs;

    @Value("${llm.worker.claude-model:claude-haiku-4-5-20251001}")
    private String defaultModel;

    private final ClaudeCliInvoker invoker;
    private final InvokerRouter invokerRouter;
    private final ProcessTerminator processTerminator;
    private final ProviderHealthRegistry healthRegistry;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4,
            r -> { Thread t = new Thread(r, "llm-pool-scheduler"); t.setDaemon(true); return t; });

    private ThreadPoolExecutor executor;
    private final ConcurrentHashMap<String, CancelableInvocation> invocations = new ConcurrentHashMap<>();

    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final AtomicLong completedCount = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicLong throttledCount = new AtomicLong(0);
    private final AtomicLong timedOutCount = new AtomicLong(0);

    public LlmWorkerPool(ClaudeCliInvoker invoker, InvokerRouter invokerRouter, ProcessTerminator processTerminator,
                          ProviderHealthRegistry healthRegistry) {
        this.invoker = invoker;
        this.invokerRouter = invokerRouter;
        this.processTerminator = processTerminator;
        this.healthRegistry = healthRegistry;
    }

    @PostConstruct
    public void init() {
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(queueCapacity);
        AtomicLong threadCounter = new AtomicLong(0);
        executor = new ThreadPoolExecutor(
                poolSize, poolSize, 60L, TimeUnit.SECONDS, workQueue,
                r -> {
                    Thread t = new Thread(r, "llm-worker-" + threadCounter.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                },
                (r, e) -> {
                    rejectedCount.incrementAndGet();
                    throw new RejectedExecutionException("LLM worker queue is full (capacity=" + queueCapacity + ")");
                }
        );
        log.info("LlmWorkerPool initialized: poolSize={}, queueCapacity={}, queueWaitTimeoutMs={}",
                poolSize, queueCapacity, queueWaitTimeoutMs);
    }

    /**
     * 동기 invoke (블로킹 HTTP 스레드). 리포트/동기 경로 전용.
     * RejectedExecutionException → LlmCapacityException (큐 포화).
     * TimeoutException → LlmTimeoutException.
     */
    /** 하위 호환용 — provider 미지정 시 CLAUDE 사용 */
    public String executeSyncTask(String prompt, String model, long timeoutMs, String correlationId)
            throws LlmException {
        return executeSyncTask(prompt, model, timeoutMs, correlationId, LlmProvider.parseLegacy(null, null));
    }

    public String executeSyncTask(String prompt, String model, long timeoutMs, String correlationId, LlmProvider provider)
            throws LlmException {
        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;
        String resolvedModel = (model != null && !model.isBlank()) ? model : defaultModel;
        var selectedInvoker = invokerRouter.routeProvider(provider);
        long enqueueTime = System.currentTimeMillis();

        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        final ExecutionSlot[] slotRef = new ExecutionSlot[1];

        try {
            executor.submit(() -> {
                long waited = System.currentTimeMillis() - enqueueTime;
                if (waited > queueWaitTimeoutMs) {
                    log.warn("Sync task queue-wait timeout {}ms exceeded (waited {}ms): corr={}",
                            queueWaitTimeoutMs, waited, correlationId);
                    resultFuture.completeExceptionally(
                            new LlmCapacityException("Queue wait timeout exceeded: " + waited + "ms"));
                    return;
                }
                activeCount.incrementAndGet();
                ExecutionSlot slot = ExecutionSlot.open(correlationId);
                slotRef[0] = slot;
                try {
                    String result = selectedInvoker.invoke(prompt, resolvedModel);
                    healthRegistry.markUp(provider);
                    if (!slot.isTerminated()) {
                        resultFuture.complete(result);
                        completedCount.incrementAndGet();
                    }
                } catch (ClaudeCodeException e) {
                    if (e.isThrottled()) throttledCount.incrementAndGet();
                    if (e.isAuthFailure()) healthRegistry.markAuthDown(provider, e.getMessage());
                    if (!slot.isTerminated()) resultFuture.completeExceptionally(e);
                } catch (Exception e) {
                    if (!slot.isTerminated()) resultFuture.completeExceptionally(e);
                } finally {
                    slot.close();
                    activeCount.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            rejectedCount.incrementAndGet();
            throw new LlmCapacityException("Worker queue full (capacity=" + queueCapacity + ")");
        }

        // 실행 타임아웃 강제 + 프로세스 킬
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            if (!resultFuture.isDone()) {
                ExecutionSlot slot = slotRef[0];
                boolean killed = slot != null && slot.terminate(processTerminator, "execution-timeout");
                timedOutCount.incrementAndGet();
                log.warn("Sync task timeout after {}ms corr={} processKilled={}", effectiveTimeout, correlationId, killed);
                resultFuture.completeExceptionally(new LlmTimeoutException(
                        "Sync task timed out after " + effectiveTimeout + "ms"));
            }
        }, effectiveTimeout, TimeUnit.MILLISECONDS);

        try {
            String result = resultFuture.get();
            timeoutTask.cancel(false);
            return result;
        } catch (ExecutionException e) {
            timeoutTask.cancel(false);
            Throwable cause = e.getCause();
            if (cause instanceof LlmException le) throw le;
            throw new ClaudeCodeException("UNKNOWN_ERROR",
                    cause != null ? cause.getMessage() : "Unknown error", -1, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmTimeoutException("Sync task interrupted");
        }
    }

    /** Structured-plan path: provider is explicit and only CLI session bridges are eligible. */
    public String executeProviderTask(String prompt, String model, long timeoutMs, String correlationId,
                                      LlmProvider provider) throws LlmException {
        return executeProviderTask(prompt, model, timeoutMs, correlationId, provider, null);
    }

    /** v2 structured path: schema is enforced by the selected session CLI. */
    public String executeProviderTask(String prompt, String model, long timeoutMs, String correlationId,
                                      LlmProvider provider, StructuredOutputSchema schema) throws LlmException {
        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;
        String resolvedModel = (model != null && !model.isBlank()) ? model : defaultModel;
        var selectedInvoker = invokerRouter.routeProvider(provider);
        long enqueueTime = System.currentTimeMillis();
        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        final ExecutionSlot[] slotRef = new ExecutionSlot[1];
        try {
            executor.submit(() -> {
                if (System.currentTimeMillis() - enqueueTime > queueWaitTimeoutMs) {
                    resultFuture.completeExceptionally(new LlmCapacityException("Queue wait timeout exceeded"));
                    return;
                }
                activeCount.incrementAndGet();
                ExecutionSlot slot = ExecutionSlot.open(correlationId);
                slotRef[0] = slot;
                try {
                    // Structured endpoints retry exactly once at service level. Do not
                    // accidentally use the legacy Claude API/CLI fallback policy here.
                    String result = schema == null
                            ? selectedInvoker.invokeSingleAttempt(prompt, resolvedModel)
                            : selectedInvoker.invokeSingleAttempt(prompt, resolvedModel, schema);
                    healthRegistry.markUp(provider);
                    if (!slot.isTerminated()) {
                        resultFuture.complete(result);
                        completedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    if (e instanceof ClaudeCodeException c) {
                        if (c.isThrottled()) throttledCount.incrementAndGet();
                        if (c.isAuthFailure()) healthRegistry.markAuthDown(provider, c.getMessage());
                    }
                    if (!slot.isTerminated()) resultFuture.completeExceptionally(e);
                } finally {
                    slot.close();
                    activeCount.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            rejectedCount.incrementAndGet();
            throw new LlmCapacityException("Worker queue full (capacity=" + queueCapacity + ")");
        }
        ScheduledFuture<?> timeout = scheduler.schedule(() -> {
            if (!resultFuture.isDone()) {
                ExecutionSlot slot = slotRef[0];
                boolean killed = slot != null && slot.terminate(processTerminator, "execution-timeout");
                timedOutCount.incrementAndGet();
                log.warn("Provider task timeout after {}ms corr={} processKilled={}", effectiveTimeout, correlationId, killed);
                resultFuture.completeExceptionally(
                        new LlmTimeoutException("Provider task timed out after " + effectiveTimeout + "ms"));
            }
        }, effectiveTimeout, TimeUnit.MILLISECONDS);
        try {
            return resultFuture.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof LlmException le) throw le;
            throw new ClaudeCodeException("UNKNOWN_ERROR", cause == null ? "Unknown error" : cause.getMessage(), -1, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmTimeoutException("Provider task interrupted");
        } finally { timeout.cancel(false); }
    }

    /**
     * 취소 가능 invocation 생성 및 큐 투입. 즉시 반환(202 Accepted 경로).
     * 결과는 invocation.getResultFuture()로 비동기 수신.
     */
    public CancelableInvocation submitCancelableTask(
            String prompt, String model, String sessionId, long timeoutMs) throws LlmCapacityException {

        String resolvedModel = (model != null && !model.isBlank()) ? model : defaultModel;
        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;
        String invocationId = UUID.randomUUID().toString();
        CancelableInvocation inv = new CancelableInvocation(invocationId, sessionId);
        long enqueueTime = System.currentTimeMillis();

        invocations.put(invocationId, inv);

        // TTL 정리: resultFuture 완료 후 60초 뒤 맵에서 제거
        inv.getResultFuture().whenComplete((r, ex) ->
                scheduler.schedule(() -> invocations.remove(invocationId), 60, TimeUnit.SECONDS));

        try {
            executor.submit(() -> {
                long waited = System.currentTimeMillis() - enqueueTime;
                if (waited > queueWaitTimeoutMs) {
                    log.warn("Cancelable task queue-wait timeout {}ms exceeded (waited {}ms): inv={}",
                            queueWaitTimeoutMs, waited, invocationId);
                    if (!inv.isCanceled()) {
                        inv.getResultFuture().completeExceptionally(
                                new LlmCapacityException("Queue wait timeout exceeded: " + waited + "ms"));
                    }
                    invocations.remove(invocationId);
                    return;
                }
                if (inv.isCanceled()) return;

                activeCount.incrementAndGet();
                // 실행 타임아웃 스케줄
                ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
                    if (!inv.isCanceled() && !inv.getResultFuture().isDone()) {
                        log.warn("Cancelable task execution timeout after {}ms: inv={}", effectiveTimeout, invocationId);
                        inv.cancel(); // destroyForcibly + completeExceptionally(InvocationCanceledException)
                    }
                }, effectiveTimeout, TimeUnit.MILLISECONDS);

                try {
                    String result = invoker.invokeWithCancelSupport(prompt, resolvedModel, inv);
                    timeoutTask.cancel(false);
                    if (!inv.isCanceled()) {
                        inv.getResultFuture().complete(result);
                        completedCount.incrementAndGet();
                    }
                } catch (InvocationCanceledException e) {
                    timeoutTask.cancel(false);
                    // cancel()이 이미 resultFuture를 완료시킴 → 정상 흐름
                } catch (ClaudeCodeException e) {
                    timeoutTask.cancel(false);
                    if (e.isThrottled()) throttledCount.incrementAndGet();
                    if (!inv.isCanceled() && !inv.getResultFuture().isDone()) {
                        inv.getResultFuture().completeExceptionally(e);
                    }
                } catch (Exception e) {
                    timeoutTask.cancel(false);
                    if (!inv.isCanceled() && !inv.getResultFuture().isDone()) {
                        inv.getResultFuture().completeExceptionally(e);
                    }
                } finally {
                    activeCount.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            rejectedCount.incrementAndGet();
            invocations.remove(invocationId);
            throw new LlmCapacityException("Worker queue full (capacity=" + queueCapacity + ")");
        }

        return inv;
    }

    public CancelableInvocation getInvocation(String invocationId) {
        return invocations.get(invocationId);
    }

    public boolean cancelInvocation(String invocationId) {
        CancelableInvocation inv = invocations.remove(invocationId);
        if (inv != null) {
            inv.cancel();
            return true;
        }
        return false;
    }

    public WorkerMetrics getMetrics() {
        return WorkerMetrics.builder()
                .poolSize(poolSize)
                .active(activeCount.get())
                .queued(executor.getQueue().size())
                .available(poolSize - activeCount.get())
                .completed(completedCount.get())
                .rejected(rejectedCount.get())
                .throttled(throttledCount.get())
                .timedOut(timedOutCount.get())
                .build();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down LlmWorkerPool (active={}, queued={})",
                activeCount.get(), executor.getQueue().size());
        executor.shutdown();
        scheduler.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
