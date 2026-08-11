package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingPublicationStats;
import com.againspring.repository.marketing.MarketingPublicationStatsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Optional weekly nudge of {@code marketing.score.weights.{platform}.*} from recent platform stats.
 *
 * <p>Default {@code marketing.score.auto_adjust=false} → report-only (no weight writes).
 * When true: conservative caps on delta (±5% relative, absolute max ±0.05 per signal per week).
 * Never patches generation prompts (plan M4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingScoreAutoAdjustService {

    public static final double RELATIVE_CAP = 0.05;
    public static final double ABSOLUTE_CAP = 0.05;

    private final MarketingScoreWeightService scoreWeightService;
    private final MarketingPublicationStatsRepository statsRepository;
    private final ObjectMapper objectMapper;

    public record AdjustResult(
        boolean enabled,
        boolean applied,
        Map<String, Map<String, Double>> before,
        Map<String, Map<String, Double>> after,
        String reason
    ) {}

    @Transactional
    public AdjustResult runWeeklyAdjust() {
        var allBefore = scoreWeightService.getPlatformWeights();
        Map<String, Map<String, Double>> beforeMap = scoreWeightService.toNestedMap(allBefore);
        if (!scoreWeightService.isAutoAdjustEnabled()) {
            return new AdjustResult(false, false, beforeMap, beforeMap, "auto_adjust disabled — report-only");
        }

        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        List<MarketingPublicationStats> latest = statsRepository.findLatestPerJobPlatformSince(since);
        if (latest.isEmpty()) {
            return new AdjustResult(true, false, beforeMap, beforeMap, "no recent platform stats");
        }

        Map<String, Mix> byPlatform = new HashMap<>();
        for (MarketingPublicationStats row : latest) {
            String platform = MarketingPopularityScorer.normalizePlatform(row.getPlatform());
            if (!MarketingPopularityScorer.isRankedPlatform(platform)) {
                continue;
            }
            Metrics m = parse(row.getMetricsJson());
            Mix mix = byPlatform.computeIfAbsent(platform, k -> new Mix());
            mix.views += m.views;
            mix.comments += m.comments + m.replies;
            mix.likes += m.likes;
        }
        if (byPlatform.isEmpty()) {
            return new AdjustResult(true, false, beforeMap, beforeMap, "no ranked-platform stats");
        }

        Map<String, MarketingPopularityScorer.PlatformWeights> partial = new LinkedHashMap<>();
        StringBuilder reason = new StringBuilder("nudged:");
        boolean any = false;

        for (Map.Entry<String, Mix> e : byPlatform.entrySet()) {
            String platform = e.getKey();
            Mix mix = e.getValue();
            long total = mix.views + mix.comments + mix.likes;
            if (total <= 0) {
                continue;
            }
            double commentShare = (double) mix.comments / total;
            double viewShare = (double) mix.views / total;

            MarketingPopularityScorer.PlatformWeights cur = allBefore.forPlatform(platform);
            double dComments = 0.0;
            double dViews = 0.0;
            if (commentShare > 0.15) {
                dComments = +ABSOLUTE_CAP * Math.min(1.0, (commentShare - 0.15) / 0.35);
            } else if (commentShare < 0.05 && mix.views > 100) {
                dComments = -ABSOLUTE_CAP * 0.5;
            }
            if (viewShare > 0.7) {
                dViews = +ABSOLUTE_CAP * 0.5;
            } else if (viewShare < 0.3 && mix.comments > 10) {
                dViews = -ABSOLUTE_CAP * 0.5;
            }

            double nextComments = clampDelta(cur.comments(), dComments);
            double nextViews = clampDelta(cur.views(), dViews);
            if (almostEqual(nextComments, cur.comments()) && almostEqual(nextViews, cur.views())) {
                continue;
            }
            partial.put(platform, new MarketingPopularityScorer.PlatformWeights(
                cur.hook(), cur.voteSkew(), nextComments, cur.votes(), nextViews, cur.hasPartner()));
            reason.append(' ').append(platform)
                .append("(c").append(round(commentShare))
                .append(",v").append(round(viewShare)).append(')');
            any = true;
        }

        if (!any) {
            return new AdjustResult(true, false, beforeMap, beforeMap, "deltas below threshold");
        }

        var afterAll = scoreWeightService.updatePlatformWeightsPartial(partial, "marketing.score.auto_adjust");
        Map<String, Map<String, Double>> afterMap = scoreWeightService.toNestedMap(afterAll);
        log.info("marketing.score.auto_adjust applied platforms={} reason={}", partial.keySet(), reason);
        return new AdjustResult(true, true, beforeMap, afterMap, reason.toString());
    }

    private double clampDelta(double current, double delta) {
        if (delta == 0.0) {
            return current;
        }
        double relCap = Math.abs(current) * RELATIVE_CAP;
        double capped = Math.max(-ABSOLUTE_CAP, Math.min(ABSOLUTE_CAP, delta));
        if (relCap > 0) {
            capped = Math.max(-relCap, Math.min(relCap, capped));
        }
        double next = current + capped;
        return Math.max(MarketingScoreWeightService.MIN_WEIGHT,
            Math.min(MarketingScoreWeightService.MAX_WEIGHT, next));
    }

    private Metrics parse(String json) {
        Metrics m = new Metrics();
        if (json == null || json.isBlank()) {
            return m;
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            long v = num(n, "views");
            long p = num(n, "plays");
            long i = num(n, "impressions");
            m.views = Math.max(v, Math.max(p, i));
            m.likes = num(n, "likes");
            m.comments = num(n, "comments");
            m.replies = num(n, "replies");
        } catch (Exception ignored) {
            // keep zeros
        }
        return m;
    }

    private static long num(JsonNode n, String key) {
        JsonNode v = n.get(key);
        if (v == null || v.isNull()) {
            return 0;
        }
        if (v.isNumber()) {
            return v.asLong();
        }
        try {
            return Long.parseLong(v.asText());
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean almostEqual(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static String round(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static class Metrics {
        long views, likes, comments, replies;
    }

    private static class Mix {
        long views, comments, likes;
    }
}
