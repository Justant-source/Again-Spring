package com.againspring.service.admin;

import com.againspring.repository.ai.AiUserGenerationConfigRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for monitoring AI user generation metrics and performance.
 * Queries persona_action_log for real-time feed, performance metrics, and hourly distribution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiUserMonitorService {

    private final JdbcTemplate jdbcTemplate;
    private final AiUserGenerationConfigRepository configRepository;

    private static final int MAX_LIMIT = 100;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * Get action feed with optional filtering by status and action type.
     * Returns feed items sorted by created_at DESC, with total count.
     */
    public ActionFeedDto getActionFeed(int limit, String statusFilter, String actionTypeFilter) {
        // Clamp limit to max
        limit = Math.min(limit, MAX_LIMIT);

        // Build query for feed
        StringBuilder feedQuery = new StringBuilder(
            "SELECT pal.id, pal.persona_id, p.nickname, p.tier, " +
            "       pal.action_type, pal.status, pal.target_type, pal.target_id, " +
            "       pal.detail, pal.created_at " +
            "FROM persona_action_log pal " +
            "LEFT JOIN personas p ON pal.persona_id = p.id " +
            "WHERE 1=1"
        );

        List<Object> params = new ArrayList<>();

        if (statusFilter != null && !statusFilter.isBlank()) {
            feedQuery.append(" AND pal.status = ?");
            params.add(statusFilter);
        }

        if (actionTypeFilter != null && !actionTypeFilter.isBlank()) {
            feedQuery.append(" AND pal.action_type = ?");
            params.add(actionTypeFilter);
        }

        feedQuery.append(" ORDER BY pal.created_at DESC LIMIT ?");
        params.add(limit);

        // Query for feed items
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(feedQuery.toString(), params.toArray());
        List<ActionFeedDto.FeedItem> feedItems = rows.stream().map(row -> {
            String status = (String) row.get("status");
            return new ActionFeedDto.FeedItem(
                ((Number) row.get("id")).longValue(),
                (String) row.get("persona_id"),
                (String) row.get("nickname"),
                (String) row.get("tier"),
                (String) row.get("action_type"),
                status,
                (String) row.get("target_type"),
                (String) row.get("target_id"),
                (String) row.get("detail"),
                "FAILED".equals(status),
                "BLOCKED".equals(status),
                row.get("created_at") != null ? row.get("created_at").toString() : null
            );
        }).collect(Collectors.toList());

        // Query for total count
        StringBuilder countQuery = new StringBuilder(
            "SELECT COUNT(*) FROM persona_action_log WHERE 1=1"
        );
        List<Object> countParams = new ArrayList<>();

        if (statusFilter != null && !statusFilter.isBlank()) {
            countQuery.append(" AND status = ?");
            countParams.add(statusFilter);
        }

        if (actionTypeFilter != null && !actionTypeFilter.isBlank()) {
            countQuery.append(" AND action_type = ?");
            countParams.add(actionTypeFilter);
        }

        Integer total = jdbcTemplate.queryForObject(countQuery.toString(), Integer.class, countParams.toArray());
        total = total != null ? total : 0;

        return new ActionFeedDto(feedItems, total);
    }

    /**
     * Get performance metrics for all personas over a given time range.
     * range = "24h" or "7d" (default "24h")
     */
    public List<PersonaPerformanceDto> getPersonaPerformance(String range) {
        // Parse range
        Instant since;
        if ("7d".equals(range)) {
            since = Instant.now().minus(7, ChronoUnit.DAYS);
        } else {
            since = Instant.now().minus(24, ChronoUnit.HOURS);
        }

        java.sql.Timestamp sinceTs = new java.sql.Timestamp(since.toEpochMilli());

        // Query for action statistics grouped by persona
        List<Map<String, Object>> stats = jdbcTemplate.queryForList(
            "SELECT pal.persona_id, p.nickname, p.tier, p.active, " +
            "       SUM(CASE WHEN pal.status='POSTED' THEN 1 ELSE 0 END) as completed, " +
            "       SUM(CASE WHEN pal.status='FAILED' THEN 1 ELSE 0 END) as failed, " +
            "       SUM(CASE WHEN pal.status='BLOCKED' THEN 1 ELSE 0 END) as blocked " +
            "FROM persona_action_log pal " +
            "LEFT JOIN personas p ON pal.persona_id = p.id " +
            "WHERE pal.created_at >= ? " +
            "GROUP BY pal.persona_id, p.nickname, p.tier, p.active " +
            "ORDER BY completed DESC",
            sinceTs
        );

        return stats.stream().map(row -> {
            int completed = ((Number) row.getOrDefault("completed", 0)).intValue();
            int failed = ((Number) row.getOrDefault("failed", 0)).intValue();
            int blocked = ((Number) row.getOrDefault("blocked", 0)).intValue();
            int total = completed + failed + blocked;
            double failureRate = total > 0 ? (double) (failed + blocked) / total * 100 : 0.0;

            return new PersonaPerformanceDto(
                (String) row.get("persona_id"),
                (String) row.get("nickname"),
                (String) row.get("tier"),
                ((Number) row.getOrDefault("active", 0)).intValue() != 0,
                completed,
                failed,
                blocked,
                failureRate,
                0  // TODO: compute real user reactions from post_comments and post_likes
            );
        }).collect(Collectors.toList());
    }

    /**
     * Get hourly distribution of posted actions over the last N hours.
     * Returns all hours 0-23 with action type breakdown.
     */
    public HourlyDistributionDto getHourlyDistribution(int hours) {
        // Default to 24 hours
        if (hours <= 0) hours = 24;

        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        java.sql.Timestamp sinceTs = new java.sql.Timestamp(since.toEpochMilli());

        // Query for hourly distribution by action type
        List<Map<String, Object>> hourlyData = jdbcTemplate.queryForList(
            "SELECT HOUR(CONVERT_TZ(created_at, '+00:00', '+09:00')) as hr, " +
            "       action_type, COUNT(*) as cnt " +
            "FROM persona_action_log " +
            "WHERE created_at >= ? AND status = 'POSTED' " +
            "GROUP BY hr, action_type",
            sinceTs
        );

        // Build hour slots 0-23
        Map<Integer, Map<String, Integer>> hourMap = new TreeMap<>();
        for (int i = 0; i < 24; i++) {
            hourMap.put(i, new HashMap<>());
        }

        // Populate with data
        for (Map<String, Object> row : hourlyData) {
            int hour = ((Number) row.get("hr")).intValue();
            String actionType = (String) row.get("action_type");
            int cnt = ((Number) row.get("cnt")).intValue();

            if (hour >= 0 && hour < 24) {
                hourMap.get(hour).merge(actionType, cnt, Integer::sum);
            }
        }

        // Convert to HourSlots
        List<HourlyDistributionDto.HourSlot> slots = hourMap.entrySet().stream()
            .map(entry -> new HourlyDistributionDto.HourSlot(entry.getKey(), 0, entry.getValue()))
            .collect(Collectors.toList());

        return new HourlyDistributionDto(slots);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────────────────────────────────

    @Getter
    @AllArgsConstructor
    public static class ActionFeedDto {
        private final List<FeedItem> feeds;
        private final int total;

        @Getter
        @AllArgsConstructor
        public static class FeedItem {
            private final long id;
            private final String personaId;
            private final String personaNickname;
            private final String personaTier;
            private final String action;
            private final String status;
            private final String targetType;
            private final String targetId;
            private final String detail;
            private final boolean failed;
            private final boolean blocked;
            private final String createdAt;
        }
    }

    @Getter
    @AllArgsConstructor
    public static class PersonaPerformanceDto {
        private final String personaId;
        private final String nickname;
        private final String tier;
        private final boolean active;
        private final int actionsCompleted;
        private final int failed;
        private final int blocked;
        private final double failureRate;
        private final int realUserReactions;
    }

    @Getter
    @AllArgsConstructor
    public static class HourlyDistributionDto {
        private final List<HourSlot> hours;

        @Getter
        @AllArgsConstructor
        public static class HourSlot {
            private final int hour;
            private final int actual;
            private final Map<String, Integer> byType;
        }
    }
}
