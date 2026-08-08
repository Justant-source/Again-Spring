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
        /**
         * Unique job identifier
         */
        private Long id;

        /**
         * Job status (REQUESTED, IN_PROGRESS, COMPLETED, FAILED, etc.)
         */
        private String status;

        /**
         * Target platforms for this job (e.g., instagram_reels, youtube_shorts)
         */
        private List<String> targets;

        /**
         * Job creation timestamp
         */
        private Instant createdAt;

        /**
         * Scheduled publish time for this job (if applicable)
         */
        private Instant scheduledPublishAt;

        /**
         * Number of times this job was rescheduled (deferred).
         * 0 = published at originally scheduled time or immediately.
         * Used in UI to show "이월 1회" (rescheduled once), etc.
         */
        private Integer rescheduledCount;

        /**
         * Reason for the most recent reschedule.
         * Examples: "scheduled_time_passed", "capacity_exhausted", "daily_quota_exceeded".
         * Nullable; only populated if rescheduledCount > 0.
         */
        private String rescheduledReason;

        /**
         * The original scheduled publish time before any reschedules.
         * Used to show when the job was originally supposed to be published.
         * Nullable; set at job creation if scheduledPublishAt was planned.
         */
        private Instant originalScheduledAt;

        public static JobItem from(JobSummary j) {
            return JobItem.builder()
                .id(j.id())
                .status(j.status())
                .targets(j.targets())
                .createdAt(j.createdAt())
                .scheduledPublishAt(j.scheduledPublishAt())
                .rescheduledCount(j.rescheduledCount())
                .rescheduledReason(j.rescheduledReason())
                .originalScheduledAt(j.originalScheduledAt())
                .build();
        }
    }

    public static MarketingCompletedListResponse from(List<CompletedItem> items) {
        return MarketingCompletedListResponse.builder()
            .items(items.stream().map(Item::from).toList())
            .build();
    }
}
