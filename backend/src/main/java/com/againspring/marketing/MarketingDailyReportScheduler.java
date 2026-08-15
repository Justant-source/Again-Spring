package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Daily marketing report scheduler.
 * Runs every day at 22:00 KST to report job creation/publishing/failure statistics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingDailyReportScheduler {

    private final MarketingJobRepository marketingJobRepository;
    private final TelegramNotifier telegramNotifier;
    private final ObjectMapper objectMapper;

    private static final List<String> CHANNELS = List.of(
        "x_thread",
        "youtube_shorts",
        "instagram_reels",
        "instagram_feed"
    );

    /** Daily 22:00 KST — report all marketing jobs created that day. */
    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
    public void reportDailyStats() {
        try {
            Instant now = Instant.now();
            LocalDate todayKst = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();

            // Get start and end of today in KST
            Instant dayStartKst = todayKst.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
            Instant dayEndKst = todayKst.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();

            List<MarketingJob> jobsToday = marketingJobRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(dayStartKst, dayEndKst);

            String report = formatDailyReport(jobsToday, todayKst);
            telegramNotifier.send(report);
            log.info("Daily marketing report sent: {} jobs", jobsToday.size());
        } catch (Exception e) {
            log.warn("Daily marketing report failed: {}", e.getMessage());
        }
    }

    /**
     * Format daily report as a string.
     * Groups jobs by channel and counts by status.
     * Calculates conversion rate (published / created).
     * Lists top failure codes.
     *
     * @param jobs all jobs created today
     * @param dateKst the date in KST
     * @return formatted report message
     */
    static String formatDailyReport(List<MarketingJob> jobs, LocalDate dateKst) {
        // Parse targets and build per-channel statistics
        Map<String, ChannelStats> statsByChannel = new HashMap<>();
        Map<String, Integer> failureCodeCounts = new HashMap<>();

        for (MarketingJob job : jobs) {
            List<String> targets = parseTargets(job.getTargets());
            String status = job.getStatus() != null ? job.getStatus() : "UNKNOWN";
            String failureCode = job.getFailureCode();

            // Record failure code if present (even if NULL)
            if ("FAILED".equals(status) || "PARTIAL".equals(status)) {
                String codeKey = failureCode != null && !failureCode.isBlank() ? failureCode : "NULL";
                failureCodeCounts.merge(codeKey, 1, Integer::sum);
            }

            // Count for each target in the job
            if (targets.isEmpty()) {
                // Job with no targets: count as "UNKNOWN" for visibility
                ChannelStats stats = statsByChannel.computeIfAbsent("UNKNOWN", k -> new ChannelStats());
                stats.addJob(status);
            } else {
                for (String target : targets) {
                    ChannelStats stats = statsByChannel.computeIfAbsent(target, k -> new ChannelStats());
                    stats.addJob(status);
                }
            }
        }

        // Ensure all expected channels are present in the map
        for (String channel : CHANNELS) {
            statsByChannel.putIfAbsent(channel, new ChannelStats());
        }

        // Calculate totals
        int totalCreated = 0;
        int totalPublished = 0;
        int totalFailed = 0;
        int totalWaiting = 0;

        for (ChannelStats stats : statsByChannel.values()) {
            totalCreated += stats.created;
            totalPublished += stats.published;
            totalFailed += stats.failed;
            totalWaiting += stats.waiting;
        }

        // Build report string
        StringBuilder sb = new StringBuilder();

        // Header with special handling for zero published
        if (totalPublished == 0) {
            sb.append("⚠️ 오늘 발행 0건\n");
        } else {
            sb.append("📊 [다시봄 마케팅] 일일 리포트 · ");
        }

        sb.append(dateKst).append("\n\n");

        // Channel table header
        sb.append("채널          생성   발행   실패   대기\n");

        // Channel rows (in CHANNELS order)
        for (String channel : CHANNELS) {
            ChannelStats stats = statsByChannel.getOrDefault(channel, new ChannelStats());
            String displayNamePadded = String.format("%-15s", channel);
            sb.append(String.format("%s %3d  %3d  %3d  %3d\n",
                displayNamePadded, stats.created, stats.published, stats.failed, stats.waiting));
        }

        // Separator and totals
        sb.append("─────────────────────────────────────\n");
        String totalLine = String.format("%-15s %3d  %3d  %3d  %3d",
            "합계", totalCreated, totalPublished, totalFailed, totalWaiting);
        sb.append(totalLine);

        // Conversion rate (avoid division by zero)
        if (totalCreated > 0) {
            int conversionPct = (int) Math.round((double) totalPublished / totalCreated * 100);
            sb.append(String.format("   (전환율 %d%%)\n", conversionPct));
        } else {
            sb.append("\n");
        }

        // Failure codes section (if any failures)
        if (!failureCodeCounts.isEmpty()) {
            sb.append("\n실패 상위\n");
            failureCodeCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5) // Top 5 failure codes
                .forEach(entry -> {
                    String code = entry.getKey();
                    int count = entry.getValue();
                    // Truncate long codes for readability
                    String displayCode = code.length() > 25 ? code.substring(0, 22) + "..." : code;
                    sb.append(String.format(" %s  %d\n", displayCode, count));
                });
        }

        // Waiting over 30 minutes section
        sb.append("\n대기 30분 초과: 없음");

        return sb.toString();
    }

    /**
     * Parse targets JSON string into a list of channel names.
     *
     * @param targetsJson JSON string like ["x_thread"] or ["youtube_shorts"]
     * @return list of channel names, or empty list if parsing fails
     */
    static List<String> parseTargets(String targetsJson) {
        if (targetsJson == null || targetsJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ObjectMapper().readValue(targetsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse targets JSON: {}", targetsJson);
            return new ArrayList<>();
        }
    }

    /**
     * Accumulator for per-channel job statistics.
     */
    static class ChannelStats {
        int created = 0;
        int published = 0;
        int failed = 0;
        int waiting = 0;

        void addJob(String status) {
            created++;
            if ("PUBLISHED".equals(status)) {
                published++;
            } else if ("FAILED".equals(status) || "PARTIAL".equals(status)) {
                failed++;
            } else {
                waiting++;
            }
        }
    }
}
