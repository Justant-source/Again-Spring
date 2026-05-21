package com.againspring.llm.bridge;

import com.againspring.llm.bridge.exception.InvocationCanceledException;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 취소 가능한 LLM 호출 단위.
 * Process 참조를 보유하여 외부에서 destroyForcibly() 호출 가능.
 * partialHandler: 스트리밍 partial 수신 시 콜백 (선택적, setPartialHandler로 등록).
 */
@Getter
public class CancelableInvocation {

    private final String invocationId;
    private final String sessionId;
    private final AtomicReference<Process> processRef = new AtomicReference<>();
    private final CompletableFuture<String> resultFuture = new CompletableFuture<>();
    private volatile boolean canceled = false;
    private volatile Consumer<String> partialHandler;

    public CancelableInvocation(String invocationId, String sessionId) {
        this.invocationId = invocationId;
        this.sessionId = sessionId;
    }

    public void attachProcess(Process process) {
        processRef.set(process);
    }

    protected void markCanceled() {
        canceled = true;
    }

    public void setPartialHandler(Consumer<String> handler) {
        this.partialHandler = handler;
    }

    /** 워커에서 STREAMING 수신 시 호출. partialHandler가 등록된 경우에만 실행. */
    public void notifyPartial(String cumulative) {
        Consumer<String> h = partialHandler;
        if (h != null && cumulative != null && !cumulative.isEmpty()) {
            try { h.accept(cumulative); } catch (Exception e) { /* 콜백 실패 무시 */ }
        }
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
