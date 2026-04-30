package com.againspring.llm.bridge;

import com.againspring.llm.bridge.exception.LLMCapacityException;
import com.againspring.llm.bridge.exception.LLMTimeoutException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Worker pool for Claude Code CLI invocation.
 * Uses a Semaphore to limit concurrency (default 3).
 * Delegates actual execution to ExecutorService with timeout enforcement.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "claude-code", matchIfMissing = true)
public class ClaudeCodeWorkerPool {

    @Value("${llm.claude-code.pool-size:3}")
    private int poolSize;

    @Value("${llm.claude-code.permit-acquire-timeout-ms:2000}")
    private long permitAcquireTimeoutMs;

    private Semaphore concurrencyLimit;
    private ExecutorService executor;
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    private final AtomicInteger tasksCompleted = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        this.concurrencyLimit = new Semaphore(poolSize);
        AtomicLong threadCounter = new AtomicLong(0);
        this.executor = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread t = new Thread(runnable, "claude-worker-" + threadCounter.getAndIncrement());
            t.setDaemon(false);
            return t;
        });
        log.info("ClaudeCodeWorkerPool initialized: poolSize={}", poolSize);
    }

    /**
     * Execute a task (Claude Code invocation) with timeout enforcement.
     * Acquires a semaphore permit (with short timeout), runs on executor, enforces task timeout.
     *
     * @param task the callable task
     * @param timeout task execution timeout
     * @return task result
     * @throws LLMCapacityException if permit cannot be acquired (pool saturated)
     * @throws LLMTimeoutException if task exceeds timeout
     * @throws Exception on other failures
     */
    public <T> T execute(Callable<T> task, Duration timeout, String correlationId)
            throws LLMCapacityException, LLMTimeoutException, Exception {

        // Try to acquire permit with short timeout
        boolean acquired = concurrencyLimit.tryAcquire(permitAcquireTimeoutMs, TimeUnit.MILLISECONDS);
        if (!acquired) {
            int available = concurrencyLimit.availablePermits();
            log.warn("Pool exhausted: available={}, active={}, correlation={}",
                    available, activeThreads.get(), correlationId);
            throw new LLMCapacityException(
                    "Worker pool exhausted: no available permits",
                    correlationId);
        }

        activeThreads.incrementAndGet();
        try {
            Future<T> future = executor.submit(task);
            try {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("Task timeout ({}ms): correlation={}", timeout.toMillis(), correlationId);
                future.cancel(true);  // Try to interrupt
                throw new LLMTimeoutException(
                        "LLM invocation exceeded timeout: " + timeout.toMillis() + "ms",
                        e, correlationId);
            } catch (InterruptedException e) {
                log.warn("Task interrupted: correlation={}", correlationId);
                Thread.currentThread().interrupt();
                throw new LLMTimeoutException("Task was interrupted", e, correlationId);
            }
        } finally {
            activeThreads.decrementAndGet();
            tasksCompleted.incrementAndGet();
            concurrencyLimit.release();
        }
    }

    /**
     * invokeCancelable에서 Semaphore 직접 참여. 기존 풀과 동시성 제한 공유.
     */
    public boolean acquirePermit(long timeoutMs) throws InterruptedException {
        return concurrencyLimit.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void releasePermit() {
        concurrencyLimit.release();
    }

    /**
     * Metrics exposure
     */
    public int getAvailablePermits() {
        return concurrencyLimit.availablePermits();
    }

    public int getActiveThreads() {
        return activeThreads.get();
    }

    public int getTasksCompleted() {
        return tasksCompleted.get();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down ClaudeCodeWorkerPool");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("Executor did not terminate gracefully");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
