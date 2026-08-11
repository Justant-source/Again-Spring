package com.againspring.service.admin;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.marketing.MarketingUtmUrls;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for marketing job statistics and analytics.
 * Aggregates platform performance, publication timeline, and traffic metrics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingStatsService {

    private final MarketingJobRepository marketingJobRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Get platform performance metrics for marketing jobs created in the last N days.
     * Aggregates by platform, counting attempted publishes and successful publishes.
     */
    public List<PlatformStatsDto> getPlatformPerformance(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        Timestamp sinceTs = new Timestamp(since.toEpochMilli());

        // Fetch jobs created since the cutoff date with terminal statuses
        List<MarketingJob> jobs = marketingJobRepository.findByStatusIn(
            Arrays.asList("PUBLISHED", "PARTIAL", "FAILED", "STALE", "READY")
        ).stream()
            .filter(job -> job.getCreatedAt() != null && job.getCreatedAt().isAfter(since))
            .collect(Collectors.toList());

        // Aggregate by platform
        Map<String, PlatformStats> platformStats = new HashMap<>();

        for (MarketingJob job : jobs) {
            // Parse targets JSON
            List<String> targets = parseJsonArray(job.getTargets(), new TypeReference<List<String>>() {});
            if (targets == null) targets = new ArrayList<>();

            // Parse publications JSON
            List<Map<String, Object>> publications = parseJsonArray(
                job.getPublications(), new TypeReference<List<Map<String, Object>>>() {});
            if (publications == null) publications = new ArrayList<>();

            // Track which platforms have publications
            Set<String> publishedPlatforms = new HashSet<>();
            String lastPublishedUrl = null;
            String lastPublishedAt = null;

            for (Map<String, Object> pub : publications) {
                String platform = (String) pub.get("platform");
                String state = (String) pub.get("state");
                if (platform != null && ("published".equalsIgnoreCase(state) || "PUBLISHED".equals(state))) {
                    publishedPlatforms.add(platform);
                    lastPublishedUrl = (String) pub.get("url");
                    lastPublishedAt = (String) pub.get("publishedAt");
                }
            }

            // For each target platform, increment attempted count
            for (String target : targets) {
                PlatformStats stats = platformStats.computeIfAbsent(target, k -> new PlatformStats());
                stats.attempted++;

                // If it was published, increment published count
                if (publishedPlatforms.contains(target)) {
                    stats.published++;
                }

                // Track last published URL and time
                if (lastPublishedUrl != null && publishedPlatforms.contains(target)) {
                    stats.lastPublishedUrl = lastPublishedUrl;
                    stats.lastPublishedAt = lastPublishedAt;
                }
            }

            // Count failures (FAILED or STALE status with failed publications)
            if ("FAILED".equals(job.getStatus()) || "STALE".equals(job.getStatus()) || "PARTIAL".equals(job.getStatus())) {
                for (String target : targets) {
                    if (!publishedPlatforms.contains(target)) {
                        PlatformStats stats = platformStats.computeIfAbsent(target, k -> new PlatformStats());
                        stats.failed++;
                    }
                }
            }
        }

        // Convert to DTOs
        return platformStats.entrySet().stream()
            .map(entry -> {
                String platform = entry.getKey();
                PlatformStats stats = entry.getValue();
                double successRate = stats.attempted > 0
                    ? (double) stats.published / stats.attempted * 100
                    : 0.0;

                return new PlatformStatsDto(
                    platform,
                    stats.attempted,
                    stats.published,
                    stats.failed,
                    successRate,
                    stats.lastPublishedUrl,
                    stats.lastPublishedAt
                );
            })
            .sorted(Comparator.comparing(PlatformStatsDto::getPlatform))
            .collect(Collectors.toList());
    }

    /**
     * Get publication timeline — flattened list of published artifacts, sorted by most recent.
     */
    public List<TimelineEventDto> getPublicationTimeline(int limit) {
        if (limit <= 0) limit = 20;

        // Fetch jobs with publications, ordered by updated_at DESC
        List<MarketingJob> jobs = marketingJobRepository.findAll().stream()
            .filter(job -> job.getPublications() != null && !job.getPublications().isBlank())
            .sorted(Comparator.comparing(MarketingJob::getUpdatedAt).reversed())
            .limit(limit)
            .collect(Collectors.toList());

        List<TimelineEventDto> events = new ArrayList<>();

        for (MarketingJob job : jobs) {
            List<Map<String, Object>> publications = parseJsonArray(
                job.getPublications(), new TypeReference<List<Map<String, Object>>>() {});

            if (publications == null) continue;

            for (Map<String, Object> pub : publications) {
                String platform = (String) pub.get("platform");
                String url = (String) pub.get("url");
                String state = (String) pub.get("state");
                Object publishedAtObj = pub.get("publishedAt");
                String publishedAt = publishedAtObj != null ? publishedAtObj.toString() : job.getUpdatedAt().toString();

                if (platform != null && url != null) {
                    events.add(new TimelineEventDto(
                        job.getId(),
                        job.getPostId(),
                        platform,
                        url,
                        state != null ? state.toUpperCase() : "UNKNOWN",
                        publishedAt
                    ));
                }
            }
        }

        return events;
    }

    /**
     * Get traffic metrics for a specific marketing job.
     * Queries visit_events with utm_campaign = "story_{jobId}".
     */
    public JobTrafficDto getJobTraffic(long jobId) {
        String utmCampaign = MarketingUtmUrls.campaignForJob(jobId);

        // Query total visits and unique sessions
        Integer visits = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM visit_events WHERE utm_campaign = ?",
            Integer.class,
            utmCampaign
        );
        visits = visits != null ? visits : 0;

        Integer uniqueSessions = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT session_key) FROM visit_events WHERE utm_campaign = ?",
            Integer.class,
            utmCampaign
        );
        uniqueSessions = uniqueSessions != null ? uniqueSessions : 0;

        // Query visits by source
        List<Map<String, Object>> sourceData = jdbcTemplate.queryForList(
            "SELECT utm_source as source, COUNT(*) as visits " +
            "FROM visit_events WHERE utm_campaign = ? " +
            "GROUP BY utm_source " +
            "ORDER BY visits DESC LIMIT 10",
            utmCampaign
        );

        List<Map<String, Object>> bySources = sourceData.stream()
            .map(row -> {
                Map<String, Object> m = new HashMap<>();
                m.put("source", row.get("source"));
                m.put("visits", ((Number) row.get("visits")).intValue());
                return m;
            })
            .collect(Collectors.toList());

        return new JobTrafficDto(jobId, visits, uniqueSessions, bySources);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private <T> T parseJsonArray(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Failed to parse JSON: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helper class
    // ─────────────────────────────────────────────────────────────────────────

    @Getter
    private static class PlatformStats {
        int attempted = 0;
        int published = 0;
        int failed = 0;
        String lastPublishedUrl;
        String lastPublishedAt;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────────────────────────────────

    @Getter
    @AllArgsConstructor
    public static class PlatformStatsDto {
        private final String platform;
        private final int attempted;
        private final int published;
        private final int failed;
        private final double successRate;
        private final String lastPublishedUrl;
        private final String lastPublishedAt;
    }

    @Getter
    @AllArgsConstructor
    public static class TimelineEventDto {
        private final long jobId;
        private final String postId;
        private final String platform;
        private final String url;
        private final String state;
        private final String publishedAt;
    }

    @Getter
    @AllArgsConstructor
    public static class JobTrafficDto {
        private final long jobId;
        private final int visits;
        private final int uniqueSessions;
        private final List<Map<String, Object>> bySources;
    }
}
