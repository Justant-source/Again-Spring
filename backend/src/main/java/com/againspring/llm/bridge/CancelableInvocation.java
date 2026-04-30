package com.againspring.llm.bridge;

import com.againspring.llm.bridge.exception.InvocationCanceledException;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 취소 가능한 LLM 호출 단위.
 * Process 참조를 보유하여 외부에서 destroyForcibly() 호출 가능.
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

    /** Process 시작 직후 호출 — 이후 외부에서 cancel() 가능 */
    public void attachProcess(Process process) {
        processRef.set(process);
    }

    /** 외부에서 호출: 진행 중인 Process를 강제 종료 */
    public boolean cancel() {
        canceled = true;
        Process p = processRef.get();
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
        }
        // future가 이미 완료됐어도 completeExceptionally는 무시됨 (멱등)
        resultFuture.completeExceptionally(
            new InvocationCanceledException("Invocation canceled: " + invocationId, invocationId));
        return true;
    }
}
