package com.againspring.llmworker.pool;

import com.againspring.llmworker.exception.InvocationCanceledException;
import com.againspring.llmworker.exception.LlmException;
import com.againspring.llmworker.exception.LlmTimeoutException;
import com.againspring.llmworker.service.ProcessTerminator;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 워커 내 취소 가능한 LLM 호출 단위.
 * 로컬 Process 참조를 보유해 destroyForcibly() 가능.
 * partialContent: 스트리밍 중 누적 텍스트. 첫 partial 도착 시 partialReadyFuture 완료.
 */
@Getter
public class CancelableInvocation {

    private final String invocationId;
    private final String sessionId;
    private final ProcessTerminator processTerminator;
    private final AtomicReference<Process> processRef = new AtomicReference<>();
    private final AtomicBoolean terminationRequested = new AtomicBoolean(false);
    private final CompletableFuture<String> resultFuture = new CompletableFuture<>();
    private final CompletableFuture<String> partialReadyFuture = new CompletableFuture<>();
    private volatile String partialContent = "";
    private volatile TerminationReason terminationReason;

    public CancelableInvocation(String invocationId, String sessionId, ProcessTerminator processTerminator) {
        this.invocationId = invocationId;
        this.sessionId = sessionId;
        this.processTerminator = processTerminator;
    }

    public void attachProcess(Process process) {
        processRef.set(process);
        if (terminationRequested.get()) {
            processTerminator.terminate(process, terminationReason.name(), invocationId);
        }
    }

    /** 스트리밍 중 호출. 누적 텍스트 업데이트 + 첫 호출 시 partialReadyFuture 완료. */
    public void updatePartial(String cumulative) {
        this.partialContent = cumulative;
        partialReadyFuture.complete(cumulative);
    }

    public boolean cancel() {
        return terminate(TerminationReason.MANUAL_CANCEL);
    }

    public boolean timeout() {
        return terminate(TerminationReason.TIMEOUT);
    }

    public boolean shutdown() {
        return terminate(TerminationReason.WORKER_SHUTDOWN);
    }

    public boolean isCanceled() {
        return terminationRequested.get();
    }

    public LlmException terminationException() {
        if (terminationReason == TerminationReason.TIMEOUT) {
            return new LlmTimeoutException("Invocation timed out: " + invocationId);
        }
        return new InvocationCanceledException("Invocation canceled: " + invocationId, invocationId);
    }

    private boolean terminate(TerminationReason reason) {
        if (!terminationRequested.compareAndSet(false, true)) return false;
        terminationReason = reason;
        Process p = processRef.get();
        if (p != null) {
            processTerminator.terminate(p, reason.name(), invocationId);
        }
        resultFuture.completeExceptionally(terminationException());
        return true;
    }

    public enum TerminationReason {
        MANUAL_CANCEL,
        TIMEOUT,
        WORKER_SHUTDOWN
    }
}
