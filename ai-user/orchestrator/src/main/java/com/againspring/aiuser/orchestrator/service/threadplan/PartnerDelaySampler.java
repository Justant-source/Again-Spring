package com.againspring.aiuser.orchestrator.service.threadplan;

import java.time.Duration;
import java.util.Random;

/**
 * Samples partner-answer delay Δ after author PUBLIC at T0.
 *
 * <p>Contract (grilled plan): Δ ∈ [{@code minMinutes}, {@code maxMinutes}] minutes,
 * median ≈ {@code medianMinutes} (default 10 / 120 / 55), right-skewed via power transform
 * — not uniform.</p>
 *
 * <p>{@code delay = min + (max - min) * U^k} where {@code k = log(medianFrac) / log(0.5)}
 * so that the median of U maps to {@code medianMinutes}.</p>
 */
public final class PartnerDelaySampler {
    public static final int DEFAULT_MIN_MINUTES = 10;
    public static final int DEFAULT_MAX_MINUTES = 120;
    public static final int DEFAULT_MEDIAN_MINUTES = 55;

    private PartnerDelaySampler() { }

    public static Duration sample(Random rng) {
        return sample(rng, DEFAULT_MIN_MINUTES, DEFAULT_MAX_MINUTES, DEFAULT_MEDIAN_MINUTES);
    }

    public static Duration sample(Random rng, int minMinutes, int maxMinutes, int medianMinutes) {
        if (rng == null) throw new IllegalArgumentException("rng required");
        int min = Math.max(0, minMinutes);
        int max = Math.max(min, maxMinutes);
        int median = Math.max(min, Math.min(max, medianMinutes));
        if (min == max) return Duration.ofMinutes(min);

        double medianFrac = (median - min) / (double) (max - min);
        // Degenerate: median at endpoint → use mild skew toward mid-left.
        if (medianFrac <= 1e-9) medianFrac = 1e-9;
        if (medianFrac >= 1.0 - 1e-9) medianFrac = 1.0 - 1e-9;
        double k = Math.log(medianFrac) / Math.log(0.5);

        double u = rng.nextDouble();
        // Avoid exact 0/1 edge cases for extreme k.
        if (u <= 0d) u = Double.MIN_NORMAL;
        if (u >= 1d) u = Math.nextDown(1d);
        double skewed = Math.pow(u, k);
        long minutes = Math.round(min + (max - min) * skewed);
        minutes = Math.max(min, Math.min(max, minutes));
        return Duration.ofMinutes(minutes);
    }

    /** Exposes the power exponent used for tests / diagnostics. */
    public static double skewExponent(int minMinutes, int maxMinutes, int medianMinutes) {
        int min = Math.max(0, minMinutes);
        int max = Math.max(min, maxMinutes);
        int median = Math.max(min, Math.min(max, medianMinutes));
        if (min == max) return 1d;
        double medianFrac = (median - min) / (double) (max - min);
        if (medianFrac <= 1e-9) medianFrac = 1e-9;
        if (medianFrac >= 1.0 - 1e-9) medianFrac = 1.0 - 1e-9;
        return Math.log(medianFrac) / Math.log(0.5);
    }
}
