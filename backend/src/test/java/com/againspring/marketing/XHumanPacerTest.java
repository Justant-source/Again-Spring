package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class XHumanPacerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static Instant kst(int y, int m, int d, int h, int min) {
        return LocalDate.of(y, m, d).atTime(h, min).atZone(KST).toInstant();
    }

    @Test
    void dailyTargetStaysInRangeAndIsStablePerDate() {
        for (int d = 1; d <= 28; d++) {
            LocalDate day = LocalDate.of(2026, 11, d);
            int t = XDailyQuota.pick(day, 200, 250);
            assertThat(t).isBetween(200, 250);
            assertThat(XDailyQuota.pick(day, 200, 250)).isEqualTo(t);
        }
    }

    @Test
    void dailyTargetVariesAcrossDays() {
        long distinct = java.util.stream.IntStream.rangeClosed(1, 28)
            .map(d -> XDailyQuota.pick(LocalDate.of(2026, 11, d), 200, 250)).distinct().count();
        assertThat(distinct).isGreaterThan(10);
    }

    @Test
    void gapIsAtLeastAMinuteAndAtMostNinetyAndNeverConstant() {
        Random rng = new Random(7);
        Instant now = kst(2026, 11, 3, 13, 10);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            Duration g = XHumanPacer.nextReplyGap(120, now, rng);
            assertThat(g.toSeconds()).isBetween(60L, 90L * 60);
            seen.add(g.toSeconds());
        }
        assertThat(seen.size()).isGreaterThan(100);
    }

    @Test
    void busyHoursAreFasterThanQuietHours() {
        Random a = new Random(1);
        Random b = new Random(1);
        double evening = 0;
        double morning = 0;
        for (int i = 0; i < 300; i++) {
            evening += XHumanPacer.nextReplyGap(150, kst(2026, 11, 3, 20, 5), a).toSeconds();
            morning += XHumanPacer.nextReplyGap(150, kst(2026, 11, 3, 8, 5), b).toSeconds();
        }
        assertThat(evening).isLessThan(morning);
    }

    @Test
    void sleepingHoursHaveZeroWeight() {
        assertThat(XHumanPacer.weight(kst(2026, 11, 3, 3, 0))).isZero();
        assertThat(XHumanPacer.weight(kst(2026, 11, 3, 20, 0))).isPositive();
    }

    @Test
    void expectedRepliesFitTheDay() {
        // Simulated day from 07:00: should place a plausible number of replies, never a burst.
        Random rng = new Random(3);
        Instant t = kst(2026, 11, 3, 7, 0);
        Instant end = kst(2026, 11, 4, 0, 0);
        int target = 225;
        int posted = 0;
        long minGap = Long.MAX_VALUE;
        while (posted < target && t.isBefore(end)) {
            if (XHumanPacer.weight(t) <= 0) {
                t = t.plusSeconds(60);
                continue;
            }
            Duration g = XHumanPacer.nextReplyGap(target - posted, t, rng);
            minGap = Math.min(minGap, g.toSeconds());
            t = t.plus(g);
            posted++;
        }
        assertThat(posted).isBetween(150, 225);
        assertThat(minGap).isGreaterThanOrEqualTo(60);
    }
}
