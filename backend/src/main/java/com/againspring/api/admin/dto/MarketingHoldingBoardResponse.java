package com.againspring.api.admin.dto;

import com.againspring.domain.marketing.MarketingHoldingStatus;
import com.againspring.marketing.holding.MarketingHoldingService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingHoldingBoardResponse {

    private List<Item> items;
    private Meta meta;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String postId;
        private String title;
        private MarketingHoldingStatus status;
        private String pinFormat;
        private Double scoreSnapshot;
        private Integer rankSnapshot;
        private Map<String, Integer> platformRankSnapshot;
        private String projectedFormat;
        private Instant postCreatedAt;
        private Instant lockedAt;
        private Instant createdAt;
        private Instant updatedAt;
        private Map<String, Object> draft;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private long remainingPool;
        private int cutlineN;
        private int dailyTextCap;
        private int dailyVideoCap;
        private long videosToday;
        private long textsToday;
        private double weightViews;
        private double weightComments;
        private double weightVotes;
    }

    public static MarketingHoldingBoardResponse from(MarketingHoldingService.HoldingBoard board) {
        List<Item> items = board.items().stream()
            .map(i -> Item.builder()
                .postId(i.postId())
                .title(i.title())
                .status(i.status())
                .pinFormat(i.pinFormat())
                .scoreSnapshot(i.scoreSnapshot())
                .rankSnapshot(i.rankSnapshot())
                .platformRankSnapshot(i.platformRankSnapshot())
                .projectedFormat(i.projectedFormat())
                .postCreatedAt(i.postCreatedAt())
                .lockedAt(i.lockedAt())
                .createdAt(i.createdAt())
                .updatedAt(i.updatedAt())
                .draft(i.draft())
                .build())
            .toList();
        MarketingHoldingService.BoardMeta m = board.meta();
        Meta meta = Meta.builder()
            .remainingPool(m.remainingPool())
            .cutlineN(m.cutlineN())
            .dailyTextCap(m.dailyTextCap())
            .dailyVideoCap(m.dailyVideoCap())
            .videosToday(m.videosToday())
            .textsToday(m.textsToday())
            .weightViews(m.weightViews())
            .weightComments(m.weightComments())
            .weightVotes(m.weightVotes())
            .build();
        return MarketingHoldingBoardResponse.builder().items(items).meta(meta).build();
    }
}
