package com.againspring.service.marketing;

import com.againspring.api.dto.response.CalendarItemResponse;
import com.againspring.api.dto.response.DashboardSummaryResponse;
import com.againspring.domain.marketing.MarketingContent;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.againspring.repository.marketing.MarketingUsageLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final MarketingContentRepository contentRepo;
    private final MarketingUsageLogRepository usageLogRepo;
    private final ObjectMapper objectMapper;

    public DashboardSummaryResponse getSummary() {
        Instant now = Instant.now();
        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);
        Instant dayAhead = now.plus(24, ChronoUnit.HOURS);

        List<MarketingContent> allContents = contentRepo.findAll();

        long weeklyPublished = allContents.stream()
                .filter(c -> c.getPublishedAt() != null && c.getPublishedAt().isAfter(weekAgo))
                .count();

        List<MarketingContent> exportedContents = allContents.stream()
                .filter(c -> c.getStatus() == MarketingContent.Status.EXPORTED)
                .collect(Collectors.toList());

        long cumulativeImpressions = 0;
        long totalLikesAndComments = 0;

        for (MarketingContent content : exportedContents) {
            if (content.getPerformanceJson() != null && !content.getPerformanceJson().isEmpty()) {
                try {
                    Map<String, Object> perfMap = objectMapper.readValue(content.getPerformanceJson(), Map.class);
                    Object impressionsObj = perfMap.get("impressions");
                    Object likesObj = perfMap.get("likes");
                    Object commentsObj = perfMap.get("comments");

                    if (impressionsObj instanceof Number) {
                        cumulativeImpressions += ((Number) impressionsObj).longValue();
                    }
                    if (likesObj instanceof Number) {
                        totalLikesAndComments += ((Number) likesObj).longValue();
                    }
                    if (commentsObj instanceof Number) {
                        totalLikesAndComments += ((Number) commentsObj).longValue();
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse performanceJson for content {}: {}", content.getId(), e.getMessage());
                }
            }
        }

        double avgEngagementRate = cumulativeImpressions > 0
                ? (double) totalLikesAndComments / cumulativeImpressions
                : 0.0;

        BigDecimal weeklyCost = usageLogRepo.sumCostByCreatedAtBetween(weekAgo, now);

        Map<String, DashboardSummaryResponse.PlatformStat> platformStatsMap = new HashMap<>();
        for (MarketingContent content : exportedContents) {
            String platform = content.getPlatform().toString();
            DashboardSummaryResponse.PlatformStat stat = platformStatsMap.getOrDefault(platform,
                    DashboardSummaryResponse.PlatformStat.builder()
                            .platform(platform)
                            .publishedCount(0)
                            .impressions(0)
                            .likes(0)
                            .comments(0)
                            .build());

            long publishedCount = stat.getPublishedCount() + 1;
            long impressions = stat.getImpressions();
            long likes = stat.getLikes();
            long comments = stat.getComments();

            if (content.getPerformanceJson() != null && !content.getPerformanceJson().isEmpty()) {
                try {
                    Map<String, Object> perfMap = objectMapper.readValue(content.getPerformanceJson(), Map.class);
                    Object impressionsObj = perfMap.get("impressions");
                    Object likesObj = perfMap.get("likes");
                    Object commentsObj = perfMap.get("comments");

                    if (impressionsObj instanceof Number) {
                        impressions += ((Number) impressionsObj).longValue();
                    }
                    if (likesObj instanceof Number) {
                        likes += ((Number) likesObj).longValue();
                    }
                    if (commentsObj instanceof Number) {
                        comments += ((Number) commentsObj).longValue();
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse performanceJson for platform stats: {}", e.getMessage());
                }
            }

            platformStatsMap.put(platform,
                    DashboardSummaryResponse.PlatformStat.builder()
                            .platform(platform)
                            .publishedCount(publishedCount)
                            .impressions(impressions)
                            .likes(likes)
                            .comments(comments)
                            .build());
        }

        List<CalendarItemResponse> upcomingPublishes = allContents.stream()
                .filter(c -> c.getScheduledAt() != null &&
                        c.getScheduledAt().isAfter(now) &&
                        c.getScheduledAt().isBefore(dayAhead))
                .map(c -> CalendarItemResponse.builder()
                        .id(c.getId())
                        .platform(c.getPlatform().toString())
                        .status(c.getStatus().toString())
                        .scheduledAt(c.getScheduledAt())
                        .publishedAt(c.getPublishedAt())
                        .title(c.getTitle())
                        .build())
                .collect(Collectors.toList());

        return DashboardSummaryResponse.builder()
                .weeklyPublished(weeklyPublished)
                .cumulativeImpressions(cumulativeImpressions)
                .averageEngagementRate(avgEngagementRate)
                .weeklyCostUsd(weeklyCost)
                .platformStats(List.copyOf(platformStatsMap.values()))
                .upcomingPublishes(upcomingPublishes)
                .build();
    }
}
