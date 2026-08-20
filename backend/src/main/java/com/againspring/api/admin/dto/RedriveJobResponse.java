package com.againspring.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response from redrive endpoint: status of each redrive attempt.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedriveJobResponse {
    /**
     * Number of jobs requested for redrive.
     */
    private int requested;

    /**
     * List of per-job results.
     */
    private List<JobRedriveResult> results;

    /**
     * Per-job redrive attempt result.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JobRedriveResult {
        /**
         * Source job ID (the original failed job).
         */
        private Long sourceId;

        /**
         * Target job ID (the new child job, if created; null if skipped).
         */
        private Long targetId;

        /**
         * Action taken: REGENERATED (via regenerate endpoint), RECREATED (via createJob fallback),
         * SKIPPED (all platforms already published), ERROR (exception occurred).
         */
        private String action;

        /**
         * Reason for action (e.g., reason for skip or error message).
         */
        private String reason;

        /**
         * List of platform publication states (only if targetId is not null):
         * Map with keys like "youtube_shorts", "instagram_reels", values like "PUBLISHED", "RUNNING", "FAILED".
         */
        private Map<String, String> platformStates;
    }
}
