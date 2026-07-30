package com.againspring.aiuser.orchestrator.service.threadplan;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveExposureCalculatorTest {
    @Test
    void weightsEachKstHourSeparatelyAcrossBoundary() {
        Instant from = Instant.parse("2026-07-30T10:30:00Z"); // 19:30 KST
        Instant to = Instant.parse("2026-07-30T11:30:00Z");   // 20:30 KST

        long seconds = EffectiveExposureCalculator.weightedSeconds(from, to, Map.of(19, 2d, 20, 0.5d));

        assertThat(seconds).isEqualTo(4_500); // 30m * 2 + 30m * .5
    }

    @Test
    void returnsZeroForEmptyOrReverseWindow() {
        Instant now = Instant.parse("2026-07-30T00:00:00Z");
        assertThat(EffectiveExposureCalculator.weightedSeconds(now, now, Map.of())).isZero();
        assertThat(EffectiveExposureCalculator.weightedSeconds(now, now.minusSeconds(1), Map.of())).isZero();
    }
}
