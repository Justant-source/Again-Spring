package com.againspring.marketing;

/**
 * Marketing job failure stages — traced through AS subsystems.
 * Each stage has a distinct operational meaning and recovery strategy.
 *
 * <p>Tags are prefixed with "AS:" to distinguish from ASM/WaggleBot stages in cross-system logs.
 */
public enum MarketingFailureStage {
    /**
     * Failed during brief assembly from post metadata.
     * This is rare and indicates data inconsistency, not infrastructure failure.
     */
    BRIEF_BUILD,

    /**
     * LLM call for channel-specific variants (hooks, scripts, sibom plan).
     * Covers transient errors, timeouts, and format/parse errors.
     */
    VARIANT_LLM,

    /**
     * Sibom plan guard: dedupe, swap_group removal, soft-fill, or peak position checks.
     * May escalate to QUALITY_GATE if the final count is still below minimum.
     */
    SIBOM_GUARD,

    /**
     * Quality gate validation after guarding:
     * - Reels: final count < 4
     * - Shorts: final count < 4
     * Not retryable; indicates structural content insufficiency.
     */
    QUALITY_GATE,

    /**
     * ASM job creation request failed.
     * Network, serialization, or ASM-side validation errors.
     */
    ASM_CREATE,

    /**
     * Polling ASM for job status.
     * Includes 24-hour timeout expiry and transient ASM fetch failures.
     */
    ASM_POLL,

    /**
     * Publish slot assignment or publish trigger request.
     * Late-stage integration failure.
     */
    PUBLISH_TRIGGER;

    /**
     * Return the tagged stage name with "AS:" prefix.
     * Used for cross-system failure tracking.
     */
    public String tagged() {
        return "AS:" + this.name();
    }
}
