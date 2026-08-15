package com.againspring.llmworker.pool;

import com.againspring.llmworker.dto.WorkerMetrics;
import com.againspring.llmworker.exception.*;
import com.againspring.llmworker.service.ClaudeCliInvoker;
import com.againspring.llmworker.service.ProcessTerminator;
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
 * - 동시 실행 상한: LLM_POOL_SIZE (기본 100)
 * - 초과 요청: 큐에서 대기 (기존 fail-fast Semaphore → blocking queue로 전환)
 * - 큐 용량 초과 시: 즉시 429 CAPACITY (진짜 과부하)
 * - 큐-대기 타임아웃: 워커 픽업 시 enqueue 경과 시간 > queueWaitTimeoutMs → CAPACITY 실패
 * - 실행 타임아웃: 모든 경로에서 timeoutMs 강제 (취소 경로 포함 — 기존 결함 해소)
 * - 취소 가능 invocations: ConcurrentHashMap<invocationId, CancelableInvocation>
 */
@Slf4j
@Component
public class LlmWorkerPool {

    @Value("${llm.worker.pool-size:100}")
    private int poolSize;

    @Value("${llm.worker.queue-capacity:500}")
    private int queueCapacity;

    @Value("${llm.worker.queue-wait-timeout-ms:30000}")
    private long queueWaitTimeoutMs;

    @Value("${llm.worker.default-timeout-ms:120000}")
    private long defaultTimeoutMs;

    @Value("${llm.worker.claude-model:claude-haiku-4-5-20251001}")
    private String defaultModel;

    private final ClaudeCliInvoker invoker;
    private final ProcessTerminator processTerminator;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4,
            r -> { Thread t = new Thread(r, "llm-pool-scheduler"); t.setDaemon(true); return t; });

    private ThreadPoolExecutor executor;
    private final ConcurrentHashMap<String, CancelableInvocation> invocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CancelableInvocation, Boolean> activeInvocations = new ConcurrentHashMap<>();

    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final AtomicLong completedCount = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicLong throttledCount = new AtomicLong(0);
    private final AtomicLong timedOutCount = new AtomicLong(0);
    private final AtomicLong manuallyCanceledCount = new AtomicLong(0);

    public LlmWorkerPool(ClaudeCliInvoker invoker, ProcessTerminator processTerminator) {
        this.invoker = invoker;
        this.processTerminator = processTerminator;
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
    public String executeSyncTask(String prompt, String model, long timeoutMs, String correlationId)
            throws LlmException {
        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;
        String resolvedModel = (model != null && !model.isBlank()) ? model : defaultModel;
        long enqueueTime = System.currentTimeMillis();

        CancelableInvocation inv = new CancelableInvocation(correlationId, "sync", processTerminator);
        trackInvocation(inv);
        CompletableFuture<String> resultFuture = inv.getResultFuture();

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
                if (inv.isCanceled()) return;
                activeCount.incrementAndGet();
                try {
                    String result = invoker.invokeWithCancelSupport(prompt, resolvedModel, inv);
                    if (!inv.isCanceled() && resultFuture.complete(result)) completedCount.incrementAndGet();
                } catch (InvocationCanceledException | LlmTimeoutException e) {
                    // timeout/cancel already completed the future and terminated its process tree
                } catch (ClaudeCodeException e) {
                    if (e.isThrottled()) throttledCount.incrementAndGet();
                    resultFuture.completeExceptionally(e);
                } catch (Exception e) {
                    resultFuture.completeExceptionally(e);
                } finally {
                    activeCount.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            rejectedCount.incrementAndGet();
            throw new LlmCapacityException("Worker queue full (capacity=" + queueCapacity + ")");
        }

        // 실행 타임아웃은 결과만 실패시키지 않고 Claude CLI 프로세스 트리를 종료한다.
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            if (!resultFuture.isDone() && inv.timeout()) {
                timedOutCount.incrementAndGet();
                log.warn("Sync task execution timeout after {}ms: corr={}", effectiveTimeout, correlationId);
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

    /**
     * 취소 가능 invocation 생성 및 큐 투입. 즉시 반환(202 Accepted 경로).
     * 결과는 invocation.getResultFuture()로 비동기 수신.
     */
    public CancelableInvocation submitCancelableTask(
            String prompt, String model, String sessionId, long timeoutMs) throws LlmCapacityException {

        String resolvedModel = (model != null && !model.isBlank()) ? model : defaultModel;
        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;
        String invocationId = UUID.randomUUID().toString();
        CancelableInvocation inv = new CancelableInvocation(invocationId, sessionId, processTerminator);
        long enqueueTime = System.currentTimeMillis();

        invocations.put(invocationId, inv);
        trackInvocation(inv);

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
                        if (inv.timeout()) timedOutCount.incrementAndGet();
                    }
                }, effectiveTimeout, TimeUnit.MILLISECONDS);

                try {
                    String result = invoker.invokeWithCancelSupport(prompt, resolvedModel, inv);
                    timeoutTask.cancel(false);
                    if (!inv.isCanceled() && inv.getResultFuture().complete(result)) {
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
            if (inv.cancel()) manuallyCanceledCount.incrementAndGet();
            return true;
        }
        return false;
    }

    private void trackInvocation(CancelableInvocation inv) {
        activeInvocations.put(inv, Boolean.TRUE);
        inv.getResultFuture().whenComplete((result, error) -> activeInvocations.remove(inv));
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
                .manuallyCanceled(manuallyCanceledCount.get())
                .terminatedProcesses(processTerminator.getTerminatedProcesses())
                .forcedTerminations(processTerminator.getForcedTerminations())
                .activeProcessCount(processTerminator.getActiveProcessCount())
                .build();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down LlmWorkerPool (active={}, queued={})",
                activeCount.get(), executor.getQueue().size());
        activeInvocations.keySet().parallelStream().forEach(CancelableInvocation::shutdown);
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
