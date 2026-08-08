package com.againspring.api.admin.dto;

import com.againspring.domain.marketing.MarketingHoldingStatus;
import com.againspring.marketing.holding.MarketingHoldingCommitService.CompletedItem;
import com.againspring.marketing.holding.MarketingHoldingCommitService.ForceResult;
import com.againspring.marketing.holding.MarketingHoldingCommitService.JobSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingCompletedListResponse {

    private List<Item> items;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String postId;
        private MarketingHoldingStatus status;
        private String pinFormat;
        private Double scoreSnapshot;
        private Instant lockedAt;
        private Instant createdAt;
        private Instant updatedAt;
        private List<JobItem> jobs;

        public static Item from(CompletedItem c) {
            return Item.builder()
                .postId(c.postId())
                .status(c.status())
                .pinFormat(c.pinFormat())
                .scoreSnapshot(c.scoreSnapshot())
                .lockedAt(c.lockedAt())
                .createdAt(c.createdAt())
                .updatedAt(c.updatedAt())
                .jobs(c.jobs().stream().map(JobItem::from).toList())
                .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobItem {
        private Long id;
        private String status;
        private List<String> targets;
        private Instant createdAt;

        public static JobItem from(JobSummary j) {
            return JobItem.builder()
                .id(j.id())
                .status(j.status())
                .targets(j.targets())
                .createdAt(j.createdAt())
                .build();
        }
    }

    public static MarketingCompletedListResponse from(List<CompletedItem> items) {
        return MarketingCompletedListResponse.builder()
            .items(items.stream().map(Item::from).toList())
            .build();
    }
}
