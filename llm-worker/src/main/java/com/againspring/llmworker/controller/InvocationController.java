package com.againspring.llmworker.controller;

import com.againspring.llmworker.dto.*;
import com.againspring.llmworker.exception.*;
import com.againspring.llmworker.pool.CancelableInvocation;
import com.againspring.llmworker.pool.LlmWorkerPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * LLM 워커 HTTP API.
 * POST   /v1/invoke                      — 동기 invoke (리포트/Sonnet 경로)
 * POST   /v1/invocations                 — 취소 가능 invocation 생성 (채팅 경로)
 * GET    /v1/invocations/{id}/result     — long-poll 결과 조회
 * DELETE /v1/invocations/{id}            — 원격 취소
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class InvocationController {

    private final LlmWorkerPool pool;

    // long-poll 타임아웃 스케줄러 (데몬 스레드)
    private final ScheduledExecutorService pollScheduler =
            new ScheduledThreadPoolExecutor(4, r -> {
                Thread t = new Thread(r, "llm-poll-scheduler");
                t.setDaemon(true);
                return t;
            });

    /**
     * POST /v1/invoke — 동기 블로킹 invoke.
     * 주로 리포트 생성(Sonnet) 경로에서 사용.
     */
    @PostMapping("/invoke")
    public ResponseEntity<InvokeResponse> syncInvoke(@RequestBody InvokeRequest req) {
        String correlationId = req.getCorrelationId() != null ? req.getCorrelationId() : "n/a";
        long start = System.currentTimeMillis();
        try {
            String text = pool.executeSyncTask(req.getPrompt(), req.getModel(),
                    req.getTimeoutMs(), correlationId, capImages(req.getImages()));
            long latency = System.currentTimeMillis() - start;
            return ResponseEntity.ok(InvokeResponse.success(text, latency, correlationId));

        } catch (LlmCapacityException e) {
            log.warn("Sync invoke capacity exceeded: corr={}", correlationId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(InvokeResponse.capacity(e.getMessage()));

        } catch (LlmTimeoutException e) {
            log.warn("Sync invoke timed out: corr={}", correlationId);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(InvokeResponse.timeout(correlationId));

        } catch (ClaudeCodeException e) {
            log.error("Claude CLI error on sync invoke: code={}, corr={}", e.getExitCode(), correlationId);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(InvokeResponse.claudeError(e.getMessage(), e.getExitCode(), e.isThrottled()));

        } catch (Exception e) {
            log.error("Unexpected error on sync invoke: corr={}", correlationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(InvokeResponse.claudeError(e.getMessage(), -1, false));
        }
    }

    /**
     * POST /v1/invocations — 취소 가능 invocation 생성.
     * 202 Accepted + invocationId를 즉시 반환. 결과는 GET /v1/invocations/{id}/result로 폴링.
     */
    @PostMapping("/invocations")
    public ResponseEntity<CreateInvocationResponse> createInvocation(
            @RequestBody CreateInvocationRequest req) {
        try {
            CancelableInvocation inv = pool.submitCancelableTask(
                    req.getPrompt(), req.getModel(), req.getSessionId(), req.getTimeoutMs());
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new CreateInvocationResponse(inv.getInvocationId()));

        } catch (LlmCapacityException e) {
            log.warn("Cancelable invocation rejected: queue full (sessionId={})", req.getSessionId());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
    }

    /**
     * GET /v1/invocations/{id}/result?waitMs= — long-poll 결과 조회.
     * 우선순위:
     * 1. resultFuture 이미 완료 → DONE/CANCELED/FAILED 즉시 반환
     * 2. partialContent 있음(스트리밍 진행 중) → STREAMING 즉시 반환
     * 3. 위 없음 → waitMs 동안 대기, 완료·첫partial·타임아웃 중 선착 반환
     * STREAMING 수신 클라이언트는 2~3s 후 재폴 — BE 자체 재폴 간격 조절 책임.
     */
    @GetMapping("/invocations/{id}/result")
    public CompletableFuture<ResponseEntity<InvocationResultResponse>> getResult(
            @PathVariable("id") String invocationId,
            @RequestParam(defaultValue = "25000") long waitMs) {

        CancelableInvocation inv = pool.getInvocation(invocationId);
        if (inv == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        // 1. resultFuture 이미 완료
        if (inv.getResultFuture().isDone()) {
            return CompletableFuture.completedFuture(buildDoneResponse(inv));
        }

        // 2. 스트리밍 partial 이미 있음
        String currentPartial = inv.getPartialContent();
        if (!currentPartial.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.ok(InvocationResultResponse.streaming(currentPartial)));
        }

        // 3. 아직 아무것도 없음 — 대기
        long effectiveWait = Math.min(waitMs, 30_000L);
        CompletableFuture<ResponseEntity<InvocationResultResponse>> responseFuture = new CompletableFuture<>();

        pollScheduler.schedule(() -> {
            if (!responseFuture.isDone()) {
                responseFuture.complete(ResponseEntity.ok(InvocationResultResponse.pending()));
            }
        }, effectiveWait, TimeUnit.MILLISECONDS);

        inv.getResultFuture().whenComplete((result, ex) -> {
            if (!responseFuture.isDone()) {
                responseFuture.complete(buildDoneResponse(inv));
            }
        });

        // 첫 partial 도착 시 즉시 STREAMING 반환 (전체 대기 없이)
        inv.getPartialReadyFuture().whenComplete((partial, ex) -> {
            if (!responseFuture.isDone() && partial != null && !partial.isEmpty()) {
                responseFuture.complete(ResponseEntity.ok(InvocationResultResponse.streaming(partial)));
            }
        });

        return responseFuture;
    }

    private ResponseEntity<InvocationResultResponse> buildDoneResponse(CancelableInvocation inv) {
        try {
            String result = inv.getResultFuture().getNow(null);
            if (result != null) {
                return ResponseEntity.ok(InvocationResultResponse.done(result));
            }
        } catch (Exception ex) {
            if (isCanceled(ex)) {
                return ResponseEntity.ok(InvocationResultResponse.canceled());
            }
            String errorType = resolveErrorType(ex);
            return ResponseEntity.ok(InvocationResultResponse.failed(ex.getMessage(), errorType));
        }
        // future completed exceptionally — extract cause
        try {
            inv.getResultFuture().get();
            return ResponseEntity.ok(InvocationResultResponse.pending()); // won't reach
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (isCanceled(cause)) return ResponseEntity.ok(InvocationResultResponse.canceled());
            String errorType = resolveErrorType(cause);
            return ResponseEntity.ok(InvocationResultResponse.failed(cause.getMessage(), errorType));
        }
    }

    /**
     * DELETE /v1/invocations/{id} — 원격 취소. 멱등.
     * 로컬 Process.destroyForcibly() + resultFuture.completeExceptionally(InvocationCanceledException).
     */
    @DeleteMapping("/invocations/{id}")
    public ResponseEntity<Void> cancelInvocation(@PathVariable("id") String invocationId) {
        pool.cancelInvocation(invocationId);  // 이미 완료됐거나 없는 경우 no-op → 멱등
        return ResponseEntity.noContent().build();
    }

    private boolean isCanceled(Throwable ex) {
        Throwable cause = ex instanceof java.util.concurrent.ExecutionException ? ex.getCause() : ex;
        return cause instanceof InvocationCanceledException;
    }

    /** Haiku vision: at most one image. */
    static List<InvokeImage> capImages(List<InvokeImage> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return List.of(images.get(0));
    }

    private String resolveErrorType(Throwable ex) {
        Throwable cause = ex instanceof java.util.concurrent.ExecutionException ? ex.getCause() : ex;
        if (cause instanceof LlmCapacityException) return "CAPACITY";
        if (cause instanceof LlmTimeoutException) return "TIMEOUT";
        if (cause instanceof ClaudeCodeException cce) return cce.isThrottled() ? "THROTTLED" : "CLAUDE_ERROR";
        return "UNKNOWN";
    }
}
