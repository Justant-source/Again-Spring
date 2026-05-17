package com.againspring.llm.remote;

import com.againspring.llm.bridge.CancelableInvocation;
import com.againspring.llm.bridge.exception.InvocationCanceledException;
import com.againspring.llm.remote.dto.WorkerInvocationResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 원격 LLM 워커의 CancelableInvocation 프록시.
 * - cancel(): best-effort DELETE /v1/invocations/{id} + resultFuture 즉시 완료
 * - resultFuture: 공유 poller가 GET /v1/invocations/{id}/result long-poll로 채움
 * CancelableChatService는 CancelableInvocation public API만 사용 → 무수정.
 */
@Slf4j
public class RemoteCancelableInvocation extends CancelableInvocation {

    private final RestClient restClient;
    private final String workerBaseUrl;
    private final long pollWaitMs;
    private final AtomicBoolean pollingActive = new AtomicBoolean(true);
    private volatile ScheduledFuture<?> pollTask;

    public RemoteCancelableInvocation(
            String invocationId, String sessionId,
            RestClient restClient, String workerBaseUrl, long pollWaitMs) {
        super(invocationId, sessionId);
        this.restClient = restClient;
        this.workerBaseUrl = workerBaseUrl;
        this.pollWaitMs = pollWaitMs;
    }

    /**
     * 원격 취소: 로컬 canceled 플래그 set + best-effort DELETE + resultFuture 즉시 완료.
     * 멱등: 이미 완료된 future에 completeExceptionally는 no-op.
     */
    @Override
    public boolean cancel() {
        markCanceled();  // protected hook from CancelableInvocation
        pollingActive.set(false);
        if (pollTask != null) pollTask.cancel(false);

        // best-effort 원격 프로세스 종료 (비동기, 실패해도 local cancel은 완료)
        new Thread(() -> {
            try {
                restClient.delete()
                        .uri(workerBaseUrl + "/v1/invocations/" + getInvocationId())
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientException e) {
                log.debug("Remote cancel request failed (best-effort): inv={}, err={}",
                        getInvocationId(), e.getMessage());
            }
        }, "remote-cancel-" + getInvocationId()).start();

        getResultFuture().completeExceptionally(
                new InvocationCanceledException("Remote invocation canceled: " + getInvocationId(),
                        getInvocationId()));
        return true;
    }

    /**
     * poller가 결과 수신 시 호출. resultFuture를 complete/completeExceptionally.
     */
    void applyResult(WorkerInvocationResultResponse result) {
        if (isCanceled() || getResultFuture().isDone()) return;

        switch (result.getStatus()) {
            case "DONE" -> getResultFuture().complete(result.getText());
            case "CANCELED" -> getResultFuture().completeExceptionally(
                    new InvocationCanceledException("Worker canceled: " + getInvocationId(),
                            getInvocationId()));
            case "FAILED" -> getResultFuture().completeExceptionally(
                    new RuntimeException("[" + result.getErrorType() + "] " + result.getError()));
            // PENDING → 계속 폴링 (아무것도 안 함)
            default -> { /* PENDING */ }
        }
    }

    void setPollTask(ScheduledFuture<?> task) {
        this.pollTask = task;
    }

    boolean isPollingActive() {
        return pollingActive.get() && !getResultFuture().isDone();
    }

    long getPollWaitMs() {
        return pollWaitMs;
    }
}
