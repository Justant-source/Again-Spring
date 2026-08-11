package com.againspring.api.admin.dto;

import com.againspring.marketing.MarketingQuotaService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingQuotaResponse {

    /** @deprecated Phase 1 — derived as x_thread + instagram_feed caps. */
    private int dailyTextCap;
    /** @deprecated Phase 1 — derived as instagram_reels + youtube_shorts caps. */
    private int dailyVideoCap;
    private long videosToday;
    private long textsToday;
    private long remainingPool;

    /** Phase 2 per-platform caps + usage. */
    private Map<String, PlatformQuotaDto> platforms;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformQuotaDto {
        private int cap;
        private long usedToday;
        private long remaining;
    }

    public static MarketingQuotaResponse from(MarketingQuotaService.QuotaStatus status) {
        Map<String, PlatformQuotaDto> platforms = new LinkedHashMap<>();
        if (status.platformCaps() != null) {
            for (Map.Entry<String, Integer> e : status.platformCaps().asMap().entrySet()) {
                String id = e.getKey();
                long used = status.usedTodayByPlatform() != null
                    ? status.usedTodayByPlatform().getOrDefault(id, 0L) : 0L;
                long rem = status.remainingByPlatform() != null
                    ? status.remainingByPlatform().getOrDefault(id, 0L)
                    : Math.max(0, e.getValue() - used);
                platforms.put(id, PlatformQuotaDto.builder()
                    .cap(e.getValue())
                    .usedToday(used)
                    .remaining(rem)
                    .build());
            }
        }
        return MarketingQuotaResponse.builder()
            .dailyTextCap(status.dailyTextCap())
            .dailyVideoCap(status.dailyVideoCap())
            .videosToday(status.videosToday())
            .textsToday(status.textsToday())
            .remainingPool(status.remainingPool())
            .platforms(platforms)
            .build();
    }
}
