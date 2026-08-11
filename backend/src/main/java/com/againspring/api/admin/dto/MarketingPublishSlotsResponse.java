package com.againspring.api.admin.dto;

import com.againspring.marketing.MarketingPublishSlotService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingPublishSlotsResponse {

    private String instagramFeed;
    private String instagramReels;
    private String youtubeShorts;
    private String xThread;

    public static MarketingPublishSlotsResponse from(MarketingPublishSlotService.Slots slots) {
        return MarketingPublishSlotsResponse.builder()
            .instagramFeed(slots.instagramFeed())
            .instagramReels(slots.instagramReels())
            .youtubeShorts(slots.youtubeShorts())
            .xThread(slots.xThread())
            .build();
    }
}
