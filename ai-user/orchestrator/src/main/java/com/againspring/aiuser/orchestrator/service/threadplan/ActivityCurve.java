package com.againspring.aiuser.orchestrator.service.threadplan;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Picks publish times inside a future window so they cluster where the KST hourly weight map
 * (see {@link com.againspring.aiuser.orchestrator.config.OrchestratorProperties.ThreadPlan
 * #getKstHourlyHumanWeights()}) says a Korean 20s/30s/40s community audience is actually reading —
 * not a fixed "N minutes after publish" offset that ignores the clock (2026-07-31: that gap made
 * every comment on a 03:00 batch land inside the same dead 03:00-06:00 window).
 *
 * <p>Two operations, both driven by the same weight map so a post's exposure accounting
 * ({@link EffectiveExposureCalculator}) and its actual publish schedule agree on what "active hours"
 * means:</p>
 * <ul>
 *   <li>{@link #sampleFutureInstants} — spread N new items across a window (used for the nightly
 *       post batch and, per item, its early comment/reply candidates).</li>
 *   <li>{@link #advanceByWeightedSeconds} — the inverse of {@link EffectiveExposureCalculator
 *       #weightedSeconds}: "walk forward from this instant until this much human-weighted exposure
 *       has accumulated," used to re-slot an overdue item instead of publishing it immediately.</li>
 * </ul>
 */
public final class ActivityCurve {
    public static final java.time.ZoneId KST = EffectiveExposureCalculator.KST;

    private ActivityCurve() { }

    /**
     * Samples {@code count} distinct instants in {@code [from, to)}, weighted so hours with a
     * higher {@code kstHourlyHumanWeights} value get proportionally more picks, then enforces
     * {@code minSpacing} between consecutive results (bidirectional nudge — see push-pass below).
     *
     * @throws IllegalArgumentException if the window cannot fit {@code count} slots at
     *         {@code minSpacing} apart (caller should widen the window or shrink minSpacing).
     */
    public static List<Instant> sampleFutureInstants(Instant from, Instant to, int count,
            Map<Integer, Double> kstHourlyHumanWeights, Duration minSpacing, Random rng) {
        if (count <= 0) return List.of();
        if (to == null || from == null || !to.isAfter(from)) return List.of();
        long windowSeconds = Duration.between(from, to).getSeconds();
        long requiredSeconds = minSpacing.getSeconds() * (count - 1);
        if (requiredSeconds > windowSeconds) {
            throw new IllegalArgumentException("window too small: need " + requiredSeconds
                    + "s for " + count + " slots at " + minSpacing.getSeconds() + "s spacing, have " + windowSeconds + "s");
        }

        List<HourSegment> segments = buildSegments(from, to, kstHourlyHumanWeights);
        double totalMass = segments.stream().mapToDouble(s -> s.weight * s.seconds).sum();
        if (totalMass <= 0d) {
            // Degenerate config (all-zero weights): fall back to a uniform spread so the caller
            // still gets a valid schedule instead of an exception.
            segments.forEach(s -> s.weight = 1d);
            totalMass = segments.stream().mapToDouble(s -> s.weight * s.seconds).sum();
        }

        List<Instant> picks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double target = rng.nextDouble() * totalMass;
            picks.add(pointAtMass(segments, target));
        }
        picks.sort(Instant::compareTo);
        return enforceSpacing(picks, minSpacing, from, to);
    }

    /**
     * Inverse of {@link EffectiveExposureCalculator#weightedSeconds}: returns the instant at which
     * cumulative weighted exposure since {@code from} first reaches {@code targetWeightedSeconds}.
     * Used to re-slot a comment/reply that missed its original window into the next equivalent
     * "active" moment rather than the literal next wall-clock second.
     */
    public static Instant advanceByWeightedSeconds(Instant from, long targetWeightedSeconds,
            Map<Integer, Double> kstHourlyHumanWeights) {
        if (targetWeightedSeconds <= 0) return from;
        Instant cursor = from;
        double accumulated = 0d;
        // Bounded to 30 days of wall-clock so a pathological all-zero weight map can't loop forever.
        Instant hardStop = from.plus(Duration.ofDays(30));
        while (cursor.isBefore(hardStop)) {
            ZonedDateTime local = cursor.atZone(KST);
            Instant nextHour = local.plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant();
            long segmentSeconds = Duration.between(cursor, nextHour).getSeconds();
            double weight = Math.max(0d, kstHourlyHumanWeights.getOrDefault(local.getHour(), 1d));
            double segmentWeighted = segmentSeconds * weight;
            if (accumulated + segmentWeighted >= targetWeightedSeconds) {
                double remaining = targetWeightedSeconds - accumulated;
                long realSeconds = weight <= 0d ? segmentSeconds : Math.round(remaining / weight);
                return cursor.plusSeconds(Math.min(realSeconds, segmentSeconds));
            }
            accumulated += segmentWeighted;
            cursor = nextHour;
        }
        return hardStop;
    }

    /**
     * If {@code from} falls in an hour whose weight is below {@code minWeight}, returns the start
     * of the next hour that meets it (scanning forward up to 48h; returns {@code from} unchanged
     * if nothing qualifies in that window — a caller should treat that as "curve is degenerate,
     * proceed anyway" rather than block forever). Unlike {@link #advanceByWeightedSeconds}, this
     * does not wait out a low-weight hour — a low-but-nonzero weight can otherwise accumulate the
     * target on its own given enough real time, which is correct for exposure budgeting but wrong
     * for "this item is about to publish into a dead hour, move it to the next real one."
     */
    public static Instant nextActiveHour(Instant from, double minWeight, Map<Integer, Double> kstHourlyHumanWeights) {
        ZonedDateTime local = from.atZone(KST);
        if (kstHourlyHumanWeights.getOrDefault(local.getHour(), 1d) >= minWeight) return from;
        for (int i = 1; i <= 48; i++) {
            ZonedDateTime candidateHour = local.plusHours(i).withMinute(0).withSecond(0).withNano(0);
            if (kstHourlyHumanWeights.getOrDefault(candidateHour.getHour(), 1d) >= minWeight) {
                return candidateHour.toInstant();
            }
        }
        return from;
    }

    // ══════════════════════ internals ══════════════════════

    private static final class HourSegment {
        final Instant start;
        final long seconds;
        double weight;
        HourSegment(Instant start, long seconds, double weight) {
            this.start = start; this.seconds = seconds; this.weight = weight;
        }
    }

    private static List<HourSegment> buildSegments(Instant from, Instant to, Map<Integer, Double> weights) {
        List<HourSegment> segments = new ArrayList<>();
        Instant cursor = from;
        while (cursor.isBefore(to)) {
            ZonedDateTime local = cursor.atZone(KST);
            Instant nextHour = local.plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant();
            Instant end = nextHour.isBefore(to) ? nextHour : to;
            long seconds = Duration.between(cursor, end).getSeconds();
            double weight = Math.max(0d, weights.getOrDefault(local.getHour(), 1d));
            if (seconds > 0) segments.add(new HourSegment(cursor, seconds, weight));
            cursor = end;
        }
        return segments;
    }

    private static Instant pointAtMass(List<HourSegment> segments, double targetMass) {
        double acc = 0d;
        for (HourSegment s : segments) {
            double segMass = s.weight * s.seconds;
            if (acc + segMass >= targetMass || s == segments.get(segments.size() - 1)) {
                double remaining = targetMass - acc;
                long offsetSeconds = s.weight <= 0d ? 0 : Math.round(remaining / s.weight);
                return s.start.plusSeconds(Math.min(Math.max(offsetSeconds, 0), s.seconds));
            }
            acc += segMass;
        }
        return segments.get(segments.size() - 1).start;
    }

    /** Forward push then backward pull so all gaps satisfy minSpacing while staying inside [from, to). */
    private static List<Instant> enforceSpacing(List<Instant> sorted, Duration minSpacing, Instant from, Instant to) {
        List<Instant> result = new ArrayList<>(sorted);
        for (int i = 1; i < result.size(); i++) {
            Instant floor = result.get(i - 1).plus(minSpacing);
            if (result.get(i).isBefore(floor)) result.set(i, floor);
        }
        int last = result.size() - 1;
        if (result.get(last).isAfter(to)) {
            result.set(last, to);
            for (int i = last - 1; i >= 0; i--) {
                Instant ceiling = result.get(i + 1).minus(minSpacing);
                if (result.get(i).isAfter(ceiling)) result.set(i, ceiling);
            }
        }
        if (result.get(0).isBefore(from)) result.set(0, from);
        return result;
    }
}
