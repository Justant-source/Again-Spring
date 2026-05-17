package com.againspring.llmworker.pool;

import com.againspring.llmworker.exception.InvocationCanceledException;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 워커 내 취소 가능한 LLM 호출 단위.
 * 로컬 Process 참조를 보유해 destroyForcibly() 가능.
 */
@Getter
public class CancelableInvocation {

    private final String invocationId;
    private final String sessionId;
    private final AtomicReference<Process> processRef = new AtomicReference<>();
    private final CompletableFuture<String> resultFuture = new CompletableFuture<>();
    private volatile boolean canceled = false;

    public CancelableInvocation(String invocationId, String sessionId) {
        this.invocationId = invocationId;
        this.sessionId = sessionId;
    }

    public void attachProcess(Process process) {
        processRef.set(process);
    }

    public boolean cancel() {
        canceled = true;
        Process p = processRef.get();
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
        }
        resultFuture.completeExceptionally(
            new InvocationCanceledException("Invocation canceled: " + invocationId, invocationId));
        return true;
    }
}
