package com.againspring.api.admin.dto;

import com.againspring.marketing.MarketingQuotaService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingQuotaResponse {

    private int dailyTextCap;
    private int dailyVideoCap;
    private long videosToday;
    private long textsToday;
    private long remainingPool;

    public static MarketingQuotaResponse from(MarketingQuotaService.QuotaStatus status) {
        return MarketingQuotaResponse.builder()
            .dailyTextCap(status.dailyTextCap())
            .dailyVideoCap(status.dailyVideoCap())
            .videosToday(status.videosToday())
            .textsToday(status.textsToday())
            .remainingPool(status.remainingPool())
            .build();
    }
}
