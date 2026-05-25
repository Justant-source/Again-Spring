package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardSummaryResponse {

    private long weeklyPublished;

    private long cumulativeImpressions;

    private double averageEngagementRate;

    private BigDecimal weeklyCostUsd;

    private List<PlatformStat> platformStats;

    private List<CalendarItemResponse> upcomingPublishes;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlatformStat {

        private String platform;

        private long publishedCount;

        private long impressions;

        private long likes;

        private long comments;
    }
}
