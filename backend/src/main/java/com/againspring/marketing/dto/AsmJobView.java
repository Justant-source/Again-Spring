package com.againspring.marketing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Job view from ASM (Again-Spring-Marketing) polling response.
 * Represents the current state and scheduling information of a marketing job.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsmJobView {

    /**
     * Unique job identifier from ASM service
     */
    @JsonProperty("job_id")
    private String jobId;

    /**
     * Current job status (REQUESTED, IN_PROGRESS, COMPLETED, FAILED, etc.)
     */
    private String status;

    /**
     * Current processing phase within the job
     */
    private String phase;

    /**
     * Job completion progress as percentage (0.0 - 100.0)
     */
    private Double progress;

    /**
     * Generated artifacts from the job (e.g., video/image outputs, metadata)
     */
    private Map<String, Object> artifacts;

    /**
     * Publication details and results per platform
     */
    private List<Map<String, Object>> publications;

    /**
     * Error message if job failed or encountered issues
     */
    private String error;

    /** Additive renderer quality facts; raw prompt/LLM output must never be included. */
    @JsonProperty("diagnostics")
    @JsonAlias("generation_diagnostics")
    private Map<String, Object> diagnostics;

    @JsonProperty("actual_duration_ms")
    private Long actualDurationMs;

    @JsonProperty("failure_code")
    private String failureCode;

    @JsonProperty("failure_stage")
    private String failureStage;

    private Boolean retryable;

    @JsonProperty("error_summary")
    private String errorSummary;

    /**
     * Scheduled publish time for this job on respective platforms.
     * If null, the job has no specific publish schedule.
     */
    private Instant scheduledPublishAt;

    /**
     * Number of times this job was rescheduled (deferred).
     * 0 = published at originally scheduled time or immediately.
     * Shown in admin UI as "이월 N회" (rescheduled N times).
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
     * Used to track when the job was originally supposed to be published.
     * Nullable; set at job creation if scheduledPublishAt was planned.
     */
    private Instant originalScheduledAt;
}
