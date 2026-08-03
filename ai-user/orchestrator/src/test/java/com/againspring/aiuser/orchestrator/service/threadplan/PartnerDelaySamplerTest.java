package com.againspring.aiuser.orchestrator.service.threadplan;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerDelaySamplerTest {

    @Test
    void sample_staysWithinInclusiveBounds() {
        Random rng = new Random(42);
        for (int i = 0; i < 5_000; i++) {
            Duration d = PartnerDelaySampler.sample(rng);
            long minutes = d.toMinutes();
            assertThat(minutes)
                    .as("sample %d", i)
                    .isBetween(
                            (long) PartnerDelaySampler.DEFAULT_MIN_MINUTES,
                            (long) PartnerDelaySampler.DEFAULT_MAX_MINUTES);
        }
    }

    @Test
    void sample_medianRoughlyInFiftyToSixty() {
        Random rng = new Random(20260804);
        List<Long> samples = new ArrayList<>(20_000);
        for (int i = 0; i < 20_000; i++) {
            samples.add(PartnerDelaySampler.sample(rng).toMinutes());
        }
        Collections.sort(samples);
        long median = samples.get(samples.size() / 2);
        // Power transform targets ~55; allow a few minutes of sampling noise.
        assertThat(median).isBetween(50L, 60L);
    }

    @Test
    void sample_isNotUniform_moreMassBelowMidpoint() {
        Random rng = new Random(7);
        int belowMid = 0;
        int n = 10_000;
        long mid = (PartnerDelaySampler.DEFAULT_MIN_MINUTES + PartnerDelaySampler.DEFAULT_MAX_MINUTES) / 2; // 65
        for (int i = 0; i < n; i++) {
            if (PartnerDelaySampler.sample(rng).toMinutes() < mid) belowMid++;
        }
        // Right-skew → more than half of samples below arithmetic midpoint.
        assertThat(belowMid).isGreaterThan(n / 2);
    }

    @Test
    void configurableBounds_respected() {
        Random rng = new Random(1);
        for (int i = 0; i < 1_000; i++) {
            long m = PartnerDelaySampler.sample(rng, 20, 90, 50).toMinutes();
            assertThat(m).isBetween(20L, 90L);
        }
    }

    @Test
    void skewExponent_forDefaultMedianIsGreaterThanOne() {
        // median 55 in [10,120] → medianFrac < 0.5 → k > 1 (right skew)
        double k = PartnerDelaySampler.skewExponent(10, 120, 55);
        assertThat(k).isGreaterThan(1.0);
    }
}
