package com.againspring.api.dto.response;

import com.againspring.api.dto.response.CalendarItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 마케팅 대시보드 요약 응답 (marketing DashboardService 전용)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryResponse {

    @Schema(description = "최근 7일 발행 수")
    private long weeklyPublished;

    @Schema(description = "누적 노출 수")
    private long cumulativeImpressions;

    @Schema(description = "평균 참여율")
    private double averageEngagementRate;

    @Schema(description = "최근 7일 LLM 비용(USD)")
    private BigDecimal weeklyCostUsd;

    @Schema(description = "플랫폼별 성과 통계")
    private List<PlatformStat> platformStats;

    @Schema(description = "예정된 발행 목록")
    private List<CalendarItemResponse> upcomingPublishes;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlatformStat {
        private String platform;
        private long publishedCount;
        private long impressions;
        private long likes;
        private long comments;
    }
}
