package com.againspring.aiuser.orchestrator.service.threadplan;

/**
 * Shared cap on generation invocations after a successful claim (empty claims do not count).
 * One {@code generateAndHold} / paired Call1 that reaches the LLM counts as 1, even if
 * micro-batch follow-ups add extra HTTP calls inside that job.
 */
public final class LlmCallBudget {
    private final int max;
    private int used;

    public LlmCallBudget(int max) {
        this.max = Math.max(0, max);
    }

    public static LlmCallBudget ofMultiplier(int n, int multiplier) {
        int safeN = Math.max(0, n);
        int mul = Math.max(0, multiplier);
        return new LlmCallBudget(safeN * mul);
    }

    public boolean hasRemaining() {
        return used < max;
    }

    public void consume() {
        used++;
    }

    public int used() {
        return used;
    }

    public int max() {
        return max;
    }

    public int remaining() {
        return Math.max(0, max - used);
    }
}
