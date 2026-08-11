package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingPublicationStats;
import com.againspring.repository.marketing.MarketingPublicationStatsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phase 3 sprint 3.1 — marketing stats dashboard aggregation (KPI, UTM, health).
 * Week windows are KST Monday–Sunday (exclusive end), matching {@link MarketingWeeklyReportService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingStatsDashboardService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_WEEKS_AGO = 12;
    private static final Set<Integer> ALLOWED_RANGE_DAYS = Set.of(7, 14, 28);

    private final MarketingPublicationStatsRepository statsRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Build the admin stats dashboard payload.
     *
     * @param platformFilter optional ranked platform; null/blank = all four
     * @param weeksAgo       0 = current KST week
     * @param rangeDays      series length (7 / 14 / 28); other values clamp to 7
     * @param primaryMetric  optional metrics_json key override for all platforms
     */
    public DashboardDto dashboard(
        String platformFilter,
        int weeksAgo,
        int rangeDays,
        String primaryMetric
    ) {
        weeksAgo = Math.max(0, Math.min(weeksAgo, MAX_WEEKS_AGO));
        rangeDays = ALLOWED_RANGE_DAYS.contains(rangeDays) ? rangeDays : 7;
        String metricOverride = blankToNull(primaryMetric);
        String platformOnly = blankToNull(platformFilter);
        if (platformOnly != null) {
            platformOnly = MarketingPopularityScorer.normalizePlatform(platformOnly);
        }

        WeekWindow week = weekWindow(weeksAgo);
        WeekWindow prev = new WeekWindow(
            week.start().minusWeeks(1),
            week.start()
        );

        Instant fetchSince = prev.start().toInstant();
        Instant seriesStart = week.end().minusDays(rangeDays).toInstant();
        if (seriesStart.isBefore(fetchSince)) {
            fetchSince = seriesStart;
        }

        List<MarketingPublicationStats> rows = statsRepository.findCollectedSince(fetchSince);
        List<String> platforms = resolvePlatforms(platformOnly);

        List<PlatformKpiDto> platformDtos = new ArrayList<>();
        for (String platform : platforms) {
            String metric = resolvePrimaryMetric(platform, metricOverride);
            long value = sumPrimary(
                latestInWindow(rows, platform, week.startInstant(), week.endInstant()),
                platform,
                metricOverride
            );
            long prevValue = sumPrimary(
                latestInWindow(rows, platform, prev.startInstant(), prev.endInstant()),
                platform,
                metricOverride
            );
            List<DayValueDto> series = buildSeries(
                rows, platform, metricOverride, week.end(), rangeDays
            );
            platformDtos.add(new PlatformKpiDto(
                platform,
                metric,
                value,
                prevValue,
                deltaPct(value, prevValue),
                series
            ));
        }

        UtmDto utm = buildUtm(week.startInstant(), week.endInstant());
        HealthDto health = buildHealth(rows, week.startInstant(), week.endInstant(), platforms);
        UnknownCountsDto unknown = buildUnknownCounts(
            latestInWindowAllPlatforms(rows, week.startInstant(), week.endInstant(), platforms)
        );

        List<String> todoHints = new ArrayList<>();
        if (unknown.missingEmotion() > 0 || unknown.missingCategory() > 0) {
            todoHints.add("unknown emotion/category posts need tagging");
        }
        if (health.partialCount() > 0) {
            todoHints.add("partial platform stats collect rows present");
        }

        return new DashboardDto(
            week.start().toLocalDate().toString(),
            week.end().toLocalDate().toString(),
            prev.start().toLocalDate().toString(),
            prev.end().toLocalDate().toString(),
            platformDtos,
            utm,
            health,
            unknown,
            todoHints
        );
    }

    // ── week helpers (KST Mon–Sun, exclusive end) ──

    static WeekWindow weekWindow(int weeksAgo) {
        ZonedDateTime nowKst = ZonedDateTime.now(KST);
        ZonedDateTime weekEnd = nowKst.minusWeeks(weeksAgo)
            .with(java.time.DayOfWeek.MONDAY)
            .truncatedTo(ChronoUnit.DAYS)
            .plusWeeks(1);
        ZonedDateTime weekStart = weekEnd.minusWeeks(1);
        return new WeekWindow(weekStart, weekEnd);
    }

    record WeekWindow(ZonedDateTime start, ZonedDateTime end) {
        Instant startInstant() {
            return start.toInstant();
        }

        Instant endInstant() {
            return end.toInstant();
        }
    }

    // ── primary metric ──

    /** Default primary metric name per platform (plan §4.3.1). */
    public static String defaultPrimaryMetric(String platform) {
        return switch (MarketingPopularityScorer.normalizePlatform(platform)) {
            case MarketingPopularityScorer.PLATFORM_X_THREAD -> "impressions";
            case MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED -> "reach";
            case MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS -> "plays";
            case MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS -> "views";
            default -> "views";
        };
    }

    static String resolvePrimaryMetric(String platform, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim().toLowerCase(Locale.ROOT);
        }
        return defaultPrimaryMetric(platform);
    }

    /**
     * Pick the primary metric value from metrics_json.
     * Defaults: x_thread impressions→views; ig_feed reach; ig_reels plays→views; yt views.
     */
    long pickMetricValue(String platform, String override, String metricsJson) {
        if (metricsJson == null || metricsJson.isBlank()) {
            return 0L;
        }
        try {
            JsonNode n = objectMapper.readTree(metricsJson);
            if (override != null && !override.isBlank()) {
                return firstNumber(n, override.trim().toLowerCase(Locale.ROOT));
            }
            return switch (MarketingPopularityScorer.normalizePlatform(platform)) {
                case MarketingPopularityScorer.PLATFORM_X_THREAD ->
                    firstNumber(n, "impressions", "views");
                case MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED ->
                    firstNumber(n, "reach");
                case MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS ->
                    firstNumber(n, "plays", "views");
                case MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS ->
                    firstNumber(n, "views");
                default -> firstNumber(n, "views", "impressions", "plays", "reach");
            };
        } catch (Exception e) {
            log.debug("metrics parse failed: {}", e.getMessage());
            return 0L;
        }
    }

    private static long firstNumber(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull() && v.isNumber()) {
                return v.asLong();
            }
            if (v != null && v.isTextual()) {
                try {
                    return Long.parseLong(v.asText());
                } catch (NumberFormatException ignored) {
                    // continue
                }
            }
        }
        return 0L;
    }

    // ── aggregation ──

    private List<String> resolvePlatforms(String platformOnly) {
        if (platformOnly == null) {
            return new ArrayList<>(MarketingPopularityScorer.RANKED_PLATFORMS);
        }
        if (MarketingPopularityScorer.isRankedPlatform(platformOnly)) {
            return List.of(platformOnly);
        }
        return new ArrayList<>(MarketingPopularityScorer.RANKED_PLATFORMS);
    }

    /**
     * Latest snapshot per (jobId, platform) with collectedAt in [since, until).
     */
    private List<MarketingPublicationStats> latestInWindow(
        List<MarketingPublicationStats> rows,
        String platform,
        Instant since,
        Instant until
    ) {
        String norm = MarketingPopularityScorer.normalizePlatform(platform);
        Map<Long, MarketingPublicationStats> latest = new HashMap<>();
        for (MarketingPublicationStats row : rows) {
            if (row.getCollectedAt() == null) {
                continue;
            }
            if (row.getCollectedAt().isBefore(since) || !row.getCollectedAt().isBefore(until)) {
                continue;
            }
            if (!norm.equals(MarketingPopularityScorer.normalizePlatform(row.getPlatform()))) {
                continue;
            }
            Long jobId = row.getJobId();
            if (jobId == null) {
                continue;
            }
            MarketingPublicationStats prev = latest.get(jobId);
            if (prev == null || row.getCollectedAt().isAfter(prev.getCollectedAt())) {
                latest.put(jobId, row);
            }
        }
        return new ArrayList<>(latest.values());
    }

    private List<MarketingPublicationStats> latestInWindowAllPlatforms(
        List<MarketingPublicationStats> rows,
        Instant since,
        Instant until,
        List<String> platforms
    ) {
        Set<String> wanted = platforms.stream()
            .map(MarketingPopularityScorer::normalizePlatform)
            .collect(Collectors.toSet());
        Map<String, MarketingPublicationStats> latest = new HashMap<>();
        for (MarketingPublicationStats row : rows) {
            if (row.getCollectedAt() == null) {
                continue;
            }
            if (row.getCollectedAt().isBefore(since) || !row.getCollectedAt().isBefore(until)) {
                continue;
            }
            String p = MarketingPopularityScorer.normalizePlatform(row.getPlatform());
            if (!wanted.contains(p) || row.getJobId() == null) {
                continue;
            }
            String key = row.getJobId() + "|" + p;
            MarketingPublicationStats prev = latest.get(key);
            if (prev == null || row.getCollectedAt().isAfter(prev.getCollectedAt())) {
                latest.put(key, row);
            }
        }
        return new ArrayList<>(latest.values());
    }

    private long sumPrimary(
        List<MarketingPublicationStats> latest,
        String platform,
        String override
    ) {
        long sum = 0L;
        for (MarketingPublicationStats row : latest) {
            sum += pickMetricValue(platform, override, row.getMetricsJson());
        }
        return sum;
    }

    private List<DayValueDto> buildSeries(
        List<MarketingPublicationStats> rows,
        String platform,
        String override,
        ZonedDateTime weekEnd,
        int rangeDays
    ) {
        List<DayValueDto> series = new ArrayList<>(rangeDays);
        for (int i = rangeDays; i >= 1; i--) {
            ZonedDateTime dayStart = weekEnd.minusDays(i).truncatedTo(ChronoUnit.DAYS);
            ZonedDateTime dayEnd = dayStart.plusDays(1);
            long value = sumPrimary(
                latestInWindow(rows, platform, dayStart.toInstant(), dayEnd.toInstant()),
                platform,
                override
            );
            series.add(new DayValueDto(dayStart.toLocalDate().toString(), value));
        }
        return series;
    }

    static Double deltaPct(long value, long prevValue) {
        if (prevValue == 0L) {
            if (value == 0L) {
                return 0.0;
            }
            return null;
        }
        return ((value - prevValue) * 100.0) / prevValue;
    }

    // ── UTM ──

    private UtmDto buildUtm(Instant since, Instant until) {
        Integer visits = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM visit_events WHERE utm_campaign LIKE 'story_%' "
                + "AND occurred_at >= ? AND occurred_at < ?",
            Integer.class,
            java.sql.Timestamp.from(since),
            java.sql.Timestamp.from(until)
        );
        Integer sessions = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT session_key) FROM visit_events WHERE utm_campaign LIKE 'story_%' "
                + "AND occurred_at >= ? AND occurred_at < ?",
            Integer.class,
            java.sql.Timestamp.from(since),
            java.sql.Timestamp.from(until)
        );
        List<Map<String, Object>> bySourceRows = jdbcTemplate.queryForList(
            "SELECT COALESCE(utm_source,'(none)') AS source, COUNT(*) AS visits "
                + "FROM visit_events WHERE utm_campaign LIKE 'story_%' "
                + "AND occurred_at >= ? AND occurred_at < ? "
                + "GROUP BY utm_source ORDER BY visits DESC LIMIT 10",
            java.sql.Timestamp.from(since),
            java.sql.Timestamp.from(until)
        );
        List<UtmSourceDto> bySource = bySourceRows.stream()
            .map(r -> new UtmSourceDto(
                String.valueOf(r.get("source")),
                ((Number) r.get("visits")).intValue()
            ))
            .toList();
        return new UtmDto(
            visits != null ? visits : 0,
            sessions != null ? sessions : 0,
            bySource
        );
    }

    // ── health ──

    private HealthDto buildHealth(
        List<MarketingPublicationStats> rows,
        Instant weekSince,
        Instant weekUntil,
        List<String> platforms
    ) {
        Instant lastCollectAt = rows.stream()
            .map(MarketingPublicationStats::getCollectedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);

        int partialCount = 0;
        int errorCount = 0;
        Map<String, ChannelHealthAgg> byPlatform = new LinkedHashMap<>();
        for (String p : platforms) {
            byPlatform.put(p, new ChannelHealthAgg());
        }

        for (MarketingPublicationStats row : rows) {
            if (row.getCollectedAt() == null) {
                continue;
            }
            if (row.getCollectedAt().isBefore(weekSince) || !row.getCollectedAt().isBefore(weekUntil)) {
                continue;
            }
            String p = MarketingPopularityScorer.normalizePlatform(row.getPlatform());
            ChannelHealthAgg agg = byPlatform.get(p);
            if (agg == null) {
                continue;
            }
            agg.rows++;
            if (Boolean.TRUE.equals(row.getPartial())) {
                partialCount++;
                agg.partial++;
            }
            if (row.getErrorMessage() != null && !row.getErrorMessage().isBlank()) {
                errorCount++;
                agg.errors++;
            }
            if (agg.lastCollectAt == null || row.getCollectedAt().isAfter(agg.lastCollectAt)) {
                agg.lastCollectAt = row.getCollectedAt();
            }
        }

        List<ChannelHealthDto> channels = new ArrayList<>();
        for (String p : platforms) {
            ChannelHealthAgg agg = byPlatform.get(p);
            // TODO: wire AsmClient credential status APIs when cheap enough for dashboard poll;
            // for now infer from recent stats rows only (healthy / degraded / unknown).
            String status;
            String message;
            if (agg.rows == 0) {
                status = "unknown";
                message = "no stats in selected week";
            } else if (agg.errors > 0) {
                status = "degraded";
                message = agg.errors + " error row(s)";
            } else if (agg.partial > 0) {
                status = "degraded";
                message = agg.partial + " partial row(s)";
            } else {
                status = "healthy";
                message = "ok";
            }
            channels.add(new ChannelHealthDto(p, status, message));
        }

        return new HealthDto(
            lastCollectAt != null ? lastCollectAt.toString() : null,
            partialCount,
            errorCount,
            channels
        );
    }

    private static final class ChannelHealthAgg {
        int rows;
        int partial;
        int errors;
        Instant lastCollectAt;
    }

    // ── unknown emotion/category ──

    private UnknownCountsDto buildUnknownCounts(List<MarketingPublicationStats> latest) {
        Set<String> postIds = latest.stream()
            .map(MarketingPublicationStats::getPostId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toCollection(HashSet::new));
        if (postIds.isEmpty()) {
            return new UnknownCountsDto(0, 0);
        }

        int missingEmotion = 0;
        int missingCategory = 0;
        for (String postId : postIds) {
            try {
                Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT hook_emotion, category FROM posts WHERE id = ?",
                    postId
                );
                String emotion = str(row.get("hook_emotion"));
                if (emotion == null || emotion.isBlank()) {
                    missingEmotion++;
                }
                String category = str(row.get("category"));
                if (category == null || category.isBlank() || isUnknownCategory(category)) {
                    missingCategory++;
                }
            } catch (Exception e) {
                missingEmotion++;
                missingCategory++;
                log.debug("unknownCounts post miss {}: {}", postId, e.getMessage());
            }
        }
        return new UnknownCountsDto(missingEmotion, missingCategory);
    }

    private boolean isUnknownCategory(String categoryJson) {
        try {
            JsonNode n = objectMapper.readTree(categoryJson);
            if (n.has("major")) {
                String major = n.get("major").asText(null);
                return major == null || major.isBlank() || "unknown".equalsIgnoreCase(major);
            }
            if (n.isTextual()) {
                String t = n.asText();
                return t == null || t.isBlank() || "unknown".equalsIgnoreCase(t);
            }
        } catch (Exception ignored) {
            // treat raw non-empty as present
            return false;
        }
        return false;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    // ── DTOs ──

    public record DashboardDto(
        String weekStart,
        String weekEnd,
        String prevWeekStart,
        String prevWeekEnd,
        List<PlatformKpiDto> platforms,
        UtmDto utm,
        HealthDto health,
        UnknownCountsDto unknownCounts,
        List<String> todoHints
    ) {}

    public record PlatformKpiDto(
        String platform,
        String primaryMetric,
        long value,
        long prevValue,
        Double deltaPct,
        List<DayValueDto> series
    ) {}

    public record DayValueDto(
        String day,
        long value
    ) {}

    public record UtmDto(
        int visits,
        int uniqueSessions,
        List<UtmSourceDto> bySource
    ) {}

    public record UtmSourceDto(
        String source,
        int visits
    ) {}

    public record HealthDto(
        String lastCollectAt,
        int partialCount,
        int errorCount,
        List<ChannelHealthDto> channels
    ) {}

    public record ChannelHealthDto(
        String platform,
        String status,
        String message
    ) {}

    public record UnknownCountsDto(
        int missingEmotion,
        int missingCategory
    ) {}
}
