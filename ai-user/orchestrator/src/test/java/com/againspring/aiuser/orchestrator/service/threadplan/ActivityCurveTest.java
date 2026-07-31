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
        assertThat(inPeakHour).isGreaterThan(40); // overwhelming majority land in the peak hour
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
