package com.againspring.aiuser.llm.pool;

import com.againspring.aiuser.llm.exception.InvocationCanceledException;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
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
    private final AtomicReference<Process> processRef = new AtomicReference<>();
    private final CompletableFuture<String> resultFuture = new CompletableFuture<>();
    private final CompletableFuture<String> partialReadyFuture = new CompletableFuture<>();
    private volatile String partialContent = "";
    private volatile boolean canceled = false;

    public CancelableInvocation(String invocationId, String sessionId) {
        this.invocationId = invocationId;
        this.sessionId = sessionId;
    }

    public void attachProcess(Process process) {
        processRef.set(process);
    }

    /** 스트리밍 중 호출. 누적 텍스트 업데이트 + 첫 호출 시 partialReadyFuture 완료. */
    public void updatePartial(String cumulative) {
        this.partialContent = cumulative;
        partialReadyFuture.complete(cumulative);
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
