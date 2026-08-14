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

    /** Last status reported by ASM. Local status may be WAITING_EXTERNAL/SLA_BREACHED. */
    @Column(length = 32)
    private String remoteStatus;

    /** Last phase reported by ASM; retained independently of the local display phase. */
    @Column(length = 128)
    private String remotePhase;

    /** Detail of a transient remote processing delay. It is not a terminal publish error. */
    @Column(columnDefinition = "TEXT")
    private String processingDetail;

    /** First time AS started waiting for a remote job after its transient timeout. */
    @Column
    private Instant waitingExternalSince;

    /** Set once the remote generation has exceeded the operational processing SLA. */
    @Column
    private Instant slaBreachedAt;

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

    /** Actor id; force path uses {@code admin:force:} + JWT subject (UUID → up to ~48). */
    @Column(length = 128)
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
    public void applyRemote(String localStatus, String remoteStatus, String remotePhase, Double remoteProgress,
                           String remoteArtifacts, String remotePublications) {
        this.status = localStatus;
        if (remoteStatus != null && !remoteStatus.isBlank()) this.remoteStatus = remoteStatus;
        if (remotePhase != null && !remotePhase.isBlank()) {
            this.remotePhase = remotePhase;
            this.phase = remotePhase.length() > 20 ? remotePhase.substring(0, 20) : remotePhase;
        }
        if (remoteProgress != null) this.progress = remoteProgress;
        if (remoteArtifacts != null) this.artifacts = remoteArtifacts;
        if (remotePublications != null) this.publications = remotePublications;
        this.pollFailCount = 0;
        this.lastPolledAt = Instant.now();
    }

    /** True when ASM already returned render artifacts (preview usable without further poll). */
    public boolean hasArtifacts() {
        if (artifacts == null) {
            return false;
        }
        String trimmed = artifacts.trim();
        return !trimmed.isEmpty() && !"null".equalsIgnoreCase(trimmed) && !"[]".equals(trimmed)
                && !"{}".equals(trimmed);
    }

    /**
     * Mark a polling attempt failure with detail message.
     * If fail count reaches 5, mark job as STALE — unless artifacts already exist
     * (ASM outage must not erase a completed READY preview).
     */
    public void markPollFailure(String detail) {
        this.pollFailCount++;
        this.lastPolledAt = Instant.now();
        this.errorMessage = "Poll failure #" + this.pollFailCount + ": " + detail;
        if (this.pollFailCount >= 5) {
            if (hasArtifacts()) {
                this.status = "READY";
            } else {
                this.status = "STALE";
            }
        }
    }
}
