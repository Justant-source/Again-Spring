package com.againspring.aiuser.orchestrator.service.threadplan;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityCurveTest {

    private static Map<Integer, Double> peakAt(int hour) {
        java.util.Map<Integer, Double> weights = new java.util.HashMap<>();
        for (int h = 0; h < 24; h++) weights.put(h, h == hour ? 1.0 : 0.01);
        return weights;
    }

    private static Map<Integer, Double> onlyAt(int hour) {
        java.util.Map<Integer, Double> weights = new java.util.HashMap<>();
        for (int h = 0; h < 24; h++) weights.put(h, h == hour ? 1.0 : 0.0);
        return weights;
    }

    @Test
    void samplesClusterInsideTheHighWeightHour() {
        // 2026-08-01 is a Saturday; window covers the full day in KST.
        Instant from = Instant.parse("2026-07-31T15:00:00Z"); // 2026-08-01 00:00 KST
        Instant to = Instant.parse("2026-08-01T15:00:00Z");   // 2026-08-02 00:00 KST
        Map<Integer, Double> weights = peakAt(22); // 22:00 KST dominates

        List<Instant> picks = ActivityCurve.sampleFutureInstants(
                from, to, 50, weights, Duration.ofSeconds(1), new Random(42));

        long inPeakHour = picks.stream()
                .filter(i -> i.atZone(ActivityCurve.KST).getHour() == 22)
                .count();
        assertThat(picks).hasSize(50);
        // Stratified mass ≈ 81% in peak hour → expect ~40+, still majority in peak.
        assertThat(inPeakHour).isGreaterThan(35);
    }

    @Test
    void stratifiedSpreadCoversMorningUnderDefaultCurve() {
        // Regression for 2026-08-11: iid sampling + spacing packed all 8–10 nightly slots
        // into ~15:00–22:00. Stratified inverse-CDF must keep the first slot in the morning
        // half of the 08–22 KST window across many seeds.
        Instant from = Instant.parse("2026-08-10T23:00:00Z"); // 08:00 KST 2026-08-11
        Instant to = Instant.parse("2026-08-11T13:00:00Z");   // 22:00 KST
        Map<Integer, Double> weights = defaultKstWeights();

        int firstGe15 = 0;
        for (int seed = 0; seed < 100; seed++) {
            List<Instant> picks = ActivityCurve.sampleFutureInstants(
                    from, to, 8, weights, Duration.ofMinutes(45), new Random(seed));
            assertThat(picks).hasSize(8);
            int firstHour = picks.get(0).atZone(ActivityCurve.KST).getHour();
            if (firstHour >= 15) firstGe15++;
            assertThat(firstHour).isLessThan(15);
        }
        assertThat(firstGe15).isZero();
    }

    @Test
    void enforcesMinimumSpacingAndStaysInWindow() {
        Instant from = Instant.parse("2026-07-31T15:00:00Z");
        Instant to = Instant.parse("2026-08-01T15:00:00Z");
        Map<Integer, Double> flat = java.util.stream.IntStream.range(0, 24).boxed()
                .collect(java.util.stream.Collectors.toMap(h -> h, h -> 1.0));

        List<Instant> picks = ActivityCurve.sampleFutureInstants(
                from, to, 8, flat, Duration.ofMinutes(30), new Random(7));

        assertThat(picks).hasSize(8);
        assertThat(picks).isSorted();
        assertThat(picks.get(0)).isAfterOrEqualTo(from);
        assertThat(picks.get(picks.size() - 1)).isBeforeOrEqualTo(to);
        for (int i = 1; i < picks.size(); i++) {
            Duration gap = Duration.between(picks.get(i - 1), picks.get(i));
            assertThat(gap.toSeconds()).isGreaterThanOrEqualTo(Duration.ofMinutes(30).toSeconds());
        }
    }

    private static Map<Integer, Double> defaultKstWeights() {
        double[] hourly = {
                0.45, 0.30, 0.15, 0.08, 0.05, 0.05, 0.10, 0.25, 0.40, 0.45, 0.50, 0.50,
                0.65, 0.60, 0.50, 0.50, 0.50, 0.55, 0.65, 0.75, 0.85, 0.95, 1.00, 0.75,
        };
        Map<Integer, Double> weights = new java.util.LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour++) weights.put(hour, hourly[hour]);
        return weights;
    }

    @Test
    void rejectsWindowTooSmallForRequestedSpacing() {
        Instant from = Instant.parse("2026-07-31T15:00:00Z");
        Instant to = from.plusSeconds(60); // 1 minute window
        Map<Integer, Double> flat = Map.of(15, 1.0);

        assertThatThrownBy(() -> ActivityCurve.sampleFutureInstants(
                from, to, 5, flat, Duration.ofMinutes(10), new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void advanceByWeightedSecondsMatchesForwardCalculation() {
        Instant from = Instant.parse("2026-07-30T10:30:00Z"); // 19:30 KST
        Map<Integer, Double> weights = Map.of(19, 2d, 20, 0.5d);

        // Forward: 30m@19:00(w=2) + 30m@20:00(w=.5) = 3600+900 = 4500 weighted seconds total.
        Instant result = ActivityCurve.advanceByWeightedSeconds(from, 4_500, weights);

        long forward = EffectiveExposureCalculator.weightedSeconds(from, result, weights);
        assertThat(forward).isEqualTo(4_500);
    }

    @Test
    void advanceByWeightedSecondsSkipsDeadHoursWhenWeightIsZero() {
        Instant from = Instant.parse("2026-07-30T18:00:00Z"); // 03:00 KST (dead hour)
        Map<Integer, Double> weights = onlyAt(9); // only 09:00 KST has nonzero weight

        Instant result = ActivityCurve.advanceByWeightedSeconds(from, 60, weights);

        assertThat(result.atZone(ActivityCurve.KST).getHour()).isEqualTo(9);
    }
}
