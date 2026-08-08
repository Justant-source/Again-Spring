package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Marketing job for ASM (Again-Spring-Marketing) service
 * Tracks the state of marketing content generation and publishing
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_job")
@EntityListeners(AuditingEntityListener.class)
public class MarketingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 64)
    private String remoteJobId;

    @Column(nullable = false, length = 32)
    private String postId;

    @Column(unique = true, length = 80)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "REQUESTED";

    @Column(length = 20)
    private String phase;

    @Column
    @Builder.Default
    private Double progress = 0.0;

    @Column(columnDefinition = "JSON")
    private String targets;

    @Column
    @Builder.Default
    private Boolean autoPublish = false;

    @Column(columnDefinition = "JSON")
    private String artifacts;

    @Column(columnDefinition = "JSON")
    private String publications;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(length = 32)
    private String requestedBy;

    @Column
    @Builder.Default
    private Integer pollFailCount = 0;

    @Column
    private Instant lastPolledAt;

    /**
     * Scheduled publish time for this job.
     * If null, the job has no specific publish schedule.
     * Updated when the job is deferred to a later time.
     */
    @Column
    private Instant scheduledPublishAt;

    /**
     * Number of times this job was rescheduled (count of deferrals).
     * Default = 0 (published at original scheduled time or immediately).
     * Incremented each time the job is deferred due to capacity constraints.
     */
    @Column
    @Builder.Default
    private Integer rescheduledCount = 0;

    /**
     * Reason for the most recent reschedule.
     * Examples: "scheduled_time_passed", "capacity_exhausted", "daily_quota_exceeded".
     * Nullable; populated only if rescheduledCount > 0.
     */
    @Column(length = 255)
    private String rescheduledReason;

    /**
     * The original scheduled publish time before any reschedules.
     * Nullable; set at job creation if scheduledPublishAt is planned.
     * Used to track when the job was originally supposed to be published,
     * regardless of how many times it was rescheduled.
     */
    @Column
    private Instant originalScheduledAt;

    /**
     * The timestamp of the most recent reschedule event.
     * Nullable; updated whenever the job is deferred.
     */
    @Column
    private Instant lastRescheduledAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Apply remote job state to this entity
     */
    public void applyRemote(String remoteStatus, String remotePhase, Double remoteProgress,
                           String remoteArtifacts, String remotePublications) {
        this.status = remoteStatus;
        this.phase = remotePhase;
        this.progress = remoteProgress;
        this.artifacts = remoteArtifacts;
        this.publications = remotePublications;
        this.pollFailCount = 0;
        this.lastPolledAt = Instant.now();
    }

    /**
     * Mark a polling attempt failure with detail message
     * If fail count reaches 5, mark job as STALE
     */
    public void markPollFailure(String detail) {
        this.pollFailCount++;
        this.lastPolledAt = Instant.now();
        this.errorMessage = "Poll failure #" + this.pollFailCount + ": " + detail;
        if (this.pollFailCount >= 5) {
            this.status = "STALE";
        }
    }
}
