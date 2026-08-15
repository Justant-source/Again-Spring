package com.againspring.aiuser.orchestrator.safety;

/**
 * Structural sanity check for the pre-publish proofreading pass (2026-08-16
 * shortform-content-quality fix). The proofreading LLM call is instructed to fix only
 * spelling/typos/jamo-combination errors and preserve meaning, facts, relationships, and
 * paragraph structure — this is a cheap, deterministic backstop against a corrected body
 * that drifted too far, which would mean the instruction wasn't actually followed.
 *
 * <p>The line-count invariant also protects a real downstream dependency: PLAN-generated
 * posts compute {@code capture_split_after_lines} (screenshot-capture cut points) from the
 * original body's line count — if proofreading changed the number of lines, those indices
 * would silently point at the wrong place.
 */
public final class ProofreadQualityGate {

    private static final double MIN_LENGTH_RATIO = 0.7;
    private static final double MAX_LENGTH_RATIO = 1.3;

    private ProofreadQualityGate() {
    }

    public record Result(boolean passed, String reason) {
        public static Result ok() {
            return new Result(true, null);
        }

        public static Result rejected(String reason) {
            return new Result(false, reason);
        }
    }

    public static Result validate(String original, String corrected) {
        if (original == null || original.isBlank()) {
            // Nothing to compare against — treat as a code defect upstream, not a proofread failure.
            return Result.rejected("PROOFREAD_MISSING_ORIGINAL");
        }
        if (corrected == null || corrected.isBlank()) {
            return Result.rejected("PROOFREAD_EMPTY");
        }
        int originalLines = original.split("\n", -1).length;
        int correctedLines = corrected.split("\n", -1).length;
        if (originalLines != correctedLines) {
            return Result.rejected("PROOFREAD_STRUCTURE_CHANGED");
        }
        double ratio = (double) corrected.length() / original.length();
        if (ratio < MIN_LENGTH_RATIO || ratio > MAX_LENGTH_RATIO) {
            return Result.rejected("PROOFREAD_LENGTH_DRIFT");
        }
        return Result.ok();
    }
}
