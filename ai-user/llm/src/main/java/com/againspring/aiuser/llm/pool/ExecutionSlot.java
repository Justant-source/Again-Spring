package com.againspring.aiuser.llm.pool;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 워커 스레드 하나가 실행 중인 CLI 프로세스를 밖(스케줄러 타임아웃)에서 잡을 수 있게 하는 핸들.
 * 인보커 시그니처를 바꾸지 않으려고 ThreadLocal로 전달한다.
 * 이유: 2026-09-03 감사 — 600s 타임아웃이 caller future만 실패시키고 CLI 자식은 살아 슬롯이 영구 소진됐다.
 */
public final class ExecutionSlot {

    private static final ThreadLocal<ExecutionSlot> CURRENT = new ThreadLocal<>();

    private final String correlationId;
    private final AtomicReference<Process> process = new AtomicReference<>();
    private volatile boolean terminated = false;

    private ExecutionSlot(String correlationId) { this.correlationId = correlationId; }

    public static ExecutionSlot open(String correlationId) {
        ExecutionSlot slot = new ExecutionSlot(correlationId);
        CURRENT.set(slot);
        return slot;
    }

    public static void attachCurrent(Process p) {
        ExecutionSlot slot = CURRENT.get();
        if (slot != null) slot.process.set(p);
    }

    public void close() { CURRENT.remove(); }

    public boolean isTerminated() { return terminated; }

    public boolean terminate(ProcessTerminator terminator, String reason) {
        Process p = process.get();
        if (p == null) return false;
        terminated = true;
        terminator.terminate(p, reason, correlationId);
        return true;
    }
}
