package com.againspring.aiuser.orchestrator.domain;

import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A durable, revision-bound plan for one community post.
 *
 * It deliberately contains no generated content: candidate content belongs to
 * {@link AiThreadPlanItem}. This makes a post edit safe: cancel unpublished
 * items for the old revision and create exactly one new plan.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_thread_plans", uniqueConstraints = @UniqueConstraint(
        name = "uk_thread_plan_post_revision", columnNames = {"post_id", "post_revision"}))
public class AiThreadPlan {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "post_id", length = 32, nullable = false)
    private String postId;

    @Column(name = "post_revision", nullable = false)
    private int postRevision;

    /** AI_POST or HUMAN_POST; kept as text so this table remains forward-compatible. */
    @Column(name = "source_type", length = 16, nullable = false)
    private String sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ThreadPlanStatus status = ThreadPlanStatus.REQUESTED;

    /** Provider/model are immutable snapshots taken when the generation job starts. */
    @Column(name = "provider", length = 16)
    private String provider;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /** Immutable source snapshot used by the asynchronous generator after an outbox delivery. */
    @Column(name = "source_title", length = 255)
    private String sourceTitle;

    @Lob
    @Column(name = "source_body", columnDefinition = "LONGTEXT")
    private String sourceBody;

    @Column(name = "source_category", length = 32)
    private String sourceCategory;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    /** Weighted, human-activity-aware exposure accumulated by maintenance. */
    @Column(name = "effective_exposure_seconds", nullable = false)
    @Builder.Default
    private long effectiveExposureSeconds = 0;

    @Column(name = "exposure_calculated_at")
    private Instant exposureCalculatedAt;

    @Column(name = "generation_attempts", nullable = false)
    @Builder.Default
    private int generationAttempts = 0;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PrePersist
    void assignId() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
