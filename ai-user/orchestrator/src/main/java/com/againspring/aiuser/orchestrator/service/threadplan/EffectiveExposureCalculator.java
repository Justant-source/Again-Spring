package com.againspring.aiuser.orchestrator.service.threadplan;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Converts elapsed wall time to KST human-activity-weighted exposure.
 * AI events must never be used as input to the supplied hourly weights.
 */
public final class EffectiveExposureCalculator {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private EffectiveExposureCalculator() { }

    public static long weightedSeconds(Instant fromInclusive, Instant toExclusive,
                                       Map<Integer, Double> kstHourlyHumanWeights) {
        if (fromInclusive == null || toExclusive == null || !toExclusive.isAfter(fromInclusive)) return 0;
        Instant cursor = fromInclusive;
        double total = 0;
        while (cursor.isBefore(toExclusive)) {
            ZonedDateTime local = cursor.atZone(KST);
            Instant nextHour = local.plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant();
            Instant end = nextHour.isBefore(toExclusive) ? nextHour : toExclusive;
            double weight = Math.max(0d, kstHourlyHumanWeights.getOrDefault(local.getHour(), 1d));
            total += Duration.between(cursor, end).toMillis() / 1000d * weight;
            cursor = end;
        }
        return Math.round(total);
    }
}
