package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingPublicationStats;
import com.againspring.repository.marketing.MarketingPublicationStatsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin weekly marketing report (Phase 2.7): top/bottom stories, by emotion/category, UTM inflow.
 * Report-only unless {@code marketing.score.auto_adjust} is enabled (separate service).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingWeeklyReportService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MarketingPublicationStatsRepository statsRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WeeklyReportDto buildReport(int weeksAgo) {
        weeksAgo = Math.max(0, Math.min(weeksAgo, 12));
        ZonedDateTime nowKst = ZonedDateTime.now(KST);
        ZonedDateTime weekEnd = nowKst.minusWeeks(weeksAgo).with(java.time.DayOfWeek.MONDAY)
            .truncatedTo(ChronoUnit.DAYS).plusWeeks(1);
        ZonedDateTime weekStart = weekEnd.minusWeeks(1);
        Instant since = weekStart.toInstant();
        Instant until = weekEnd.toInstant();

        List<MarketingPublicationStats> latest = MarketingStatsSnapshots
            .latestWithMetricsPerJobPlatform(statsRepository.findCollectedSince(since))
            .stream()
            .filter(s -> s.getCollectedAt() != null && s.getCollectedAt().isBefore(until))
            .collect(Collectors.toList());

        Map<String, StoryAgg> byPost = new LinkedHashMap<>();
        for (MarketingPublicationStats row : latest) {
            StoryAgg agg = byPost.computeIfAbsent(row.getPostId(), id -> new StoryAgg(id));
            Metrics m = parseMetrics(row.getMetricsJson());
            agg.addPlatform(row.getPlatform(), m, row.getJobId());
        }

        // Enrich with post title / emotion / category + UTM visits
        for (StoryAgg agg : byPost.values()) {
            enrichPost(agg);
            agg.utmVisits = countUtmVisits(agg.jobIds, since, until);
            agg.score = compositeScore(agg);
        }

        List<StoryAgg> ranked = byPost.values().stream()
            .sorted(Comparator.comparingDouble((StoryAgg s) -> s.score).reversed())
            .collect(Collectors.toList());

        int topN = Math.min(10, ranked.size());
        List<StoryRowDto> top = ranked.subList(0, topN).stream().map(this::toRow).toList();
        List<StoryRowDto> bottom = ranked.size() <= topN
            ? List.of()
            : ranked.subList(Math.max(0, ranked.size() - topN), ranked.size()).stream()
                .sorted(Comparator.comparingDouble(s -> s.score))
                .map(this::toRow)
                .toList();

        Map<String, EmotionBucket> byEmotion = new LinkedHashMap<>();
        Map<String, CategoryBucket> byCategory = new LinkedHashMap<>();
        for (StoryAgg s : ranked) {
            String emotion = s.hookEmotion != null ? s.hookEmotion : "unknown";
            EmotionBucket eb = byEmotion.computeIfAbsent(emotion, EmotionBucket::new);
            eb.stories++;
            eb.totalScore += s.score;
            eb.views += s.totalViews;
            eb.comments += s.totalComments;

            String cat = s.categoryMajor != null ? s.categoryMajor : "unknown";
            CategoryBucket cb = byCategory.computeIfAbsent(cat, CategoryBucket::new);
            cb.stories++;
            cb.totalScore += s.score;
            cb.views += s.totalViews;
            cb.utmVisits += s.utmVisits;
        }

        UtmInflowDto utm = buildUtmInflow(since, until);

        return new WeeklyReportDto(
            weekStart.toLocalDate().toString(),
            weekEnd.toLocalDate().toString(),
            top,
            bottom,
            byEmotion.values().stream()
                .map(e -> new EmotionRowDto(e.emotion, e.stories, e.views, e.comments,
                    e.stories > 0 ? e.totalScore / e.stories : 0))
                .sorted(Comparator.comparingDouble(EmotionRowDto::avgScore).reversed())
                .toList(),
            byCategory.values().stream()
                .map(c -> new CategoryRowDto(c.category, c.stories, c.views, c.utmVisits,
                    c.stories > 0 ? c.totalScore / c.stories : 0))
                .sorted(Comparator.comparingDouble(CategoryRowDto::avgScore).reversed())
                .toList(),
            utm,
            latest.size(),
            ranked.size()
        );
    }

    private void enrichPost(StoryAgg agg) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT title, promo_title, hook_emotion, category FROM posts WHERE id = ?",
                agg.postId
            );
            agg.title = str(row.get("promo_title"));
            if (agg.title == null || agg.title.isBlank()) {
                agg.title = str(row.get("title"));
            }
            agg.hookEmotion = str(row.get("hook_emotion"));
            agg.categoryMajor = categoryMajor(str(row.get("category")));
        } catch (Exception e) {
            log.debug("Post enrich miss {}: {}", agg.postId, e.getMessage());
        }
    }

    private String categoryMajor(String categoryJson) {
        if (categoryJson == null || categoryJson.isBlank()) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(categoryJson);
            if (n.has("major")) {
                return n.get("major").asText(null);
            }
            if (n.isTextual()) {
                return n.asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return categoryJson.length() > 40 ? categoryJson.substring(0, 40) : categoryJson;
    }

    private int countUtmVisits(Set<Long> jobIds, Instant since, Instant until) {
        if (jobIds == null || jobIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Long jobId : jobIds) {
            String campaign = MarketingUtmUrls.campaignForJob(jobId);
            Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visit_events WHERE utm_campaign = ? AND occurred_at >= ? AND occurred_at < ?",
                Integer.class,
                campaign,
                java.sql.Timestamp.from(since),
                java.sql.Timestamp.from(until)
            );
            total += c != null ? c : 0;
        }
        return total;
    }

    private UtmInflowDto buildUtmInflow(Instant since, Instant until) {
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
        List<Map<String, Object>> bySource = jdbcTemplate.queryForList(
            "SELECT COALESCE(utm_source,'(none)') AS source, COUNT(*) AS visits "
                + "FROM visit_events WHERE utm_campaign LIKE 'story_%' "
                + "AND occurred_at >= ? AND occurred_at < ? "
                + "GROUP BY utm_source ORDER BY visits DESC LIMIT 10",
            java.sql.Timestamp.from(since),
            java.sql.Timestamp.from(until)
        );
        List<Map<String, Object>> sources = bySource.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", r.get("source"));
            m.put("visits", ((Number) r.get("visits")).intValue());
            return m;
        }).toList();
        return new UtmInflowDto(
            visits != null ? visits : 0,
            sessions != null ? sessions : 0,
            sources
        );
    }

    private double compositeScore(StoryAgg s) {
        // Conservative blend: platform engagement + UTM inflow. No prompt patching.
        return s.totalViews * 0.01
            + s.totalLikes * 0.5
            + s.totalComments * 2.0
            + s.totalReplies * 2.0
            + s.utmVisits * 5.0;
    }

    private StoryRowDto toRow(StoryAgg s) {
        return new StoryRowDto(
            s.postId,
            s.title,
            s.hookEmotion,
            s.categoryMajor,
            s.score,
            s.totalViews,
            s.totalLikes,
            s.totalComments,
            s.utmVisits,
            new ArrayList<>(s.platforms)
        );
    }

    private Metrics parseMetrics(String json) {
        Metrics m = new Metrics();
        if (json == null || json.isBlank()) {
            return m;
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            m.views = firstNumber(n, "views", "plays", "impressions");
            m.likes = firstNumber(n, "likes");
            m.comments = firstNumber(n, "comments");
            m.replies = firstNumber(n, "replies");
            m.reach = firstNumber(n, "reach");
            m.saves = firstNumber(n, "saves");
            m.shares = firstNumber(n, "shares");
            m.avgViewDurationSec = firstNumber(n, "avg_view_duration_sec");
        } catch (Exception e) {
            log.debug("metrics parse failed: {}", e.getMessage());
        }
        return m;
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
        return 0;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    // ── internal ──

    private static class Metrics {
        long views, likes, comments, replies, reach, saves, shares, avgViewDurationSec;
    }

    private static class StoryAgg {
        final String postId;
        String title;
        String hookEmotion;
        String categoryMajor;
        final Set<String> platforms = new LinkedHashSet<>();
        final Set<Long> jobIds = new LinkedHashSet<>();
        long totalViews, totalLikes, totalComments, totalReplies;
        int utmVisits;
        double score;

        StoryAgg(String postId) {
            this.postId = postId;
        }

        void addPlatform(String platform, Metrics m, Long jobId) {
            platforms.add(platform);
            if (jobId != null) {
                jobIds.add(jobId);
            }
            totalViews += m.views;
            totalLikes += m.likes;
            totalComments += m.comments;
            totalReplies += m.replies;
        }
    }

    private static class EmotionBucket {
        final String emotion;
        int stories;
        long views, comments;
        double totalScore;

        EmotionBucket(String emotion) {
            this.emotion = emotion;
        }
    }

    private static class CategoryBucket {
        final String category;
        int stories;
        long views;
        int utmVisits;
        double totalScore;

        CategoryBucket(String category) {
            this.category = category;
        }
    }

    // ── DTOs ──

    public record WeeklyReportDto(
        String weekStart,
        String weekEnd,
        List<StoryRowDto> topStories,
        List<StoryRowDto> bottomStories,
        List<EmotionRowDto> byEmotion,
        List<CategoryRowDto> byCategory,
        UtmInflowDto utmInflow,
        int snapshotRows,
        int storyCount
    ) {}

    public record StoryRowDto(
        String postId,
        String title,
        String hookEmotion,
        String category,
        double score,
        long views,
        long likes,
        long comments,
        int utmVisits,
        List<String> platforms
    ) {}

    public record EmotionRowDto(
        String emotion,
        int stories,
        long views,
        long comments,
        double avgScore
    ) {}

    public record CategoryRowDto(
        String category,
        int stories,
        long views,
        int utmVisits,
        double avgScore
    ) {}

    public record UtmInflowDto(
        int visits,
        int uniqueSessions,
        List<Map<String, Object>> bySource
    ) {}
}
