package com.againspring.aiuser.orchestrator.domain;

import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A post generated ahead of time (nightly batch or a retiming operation) and held here until
 * {@code scheduledPublishAt}. {@code candidatesJson} is the full structured-generation response
 * (post + comment/reply candidates) from the same LLM call, replayed into
 * {@code ai_thread_plans}/{@code ai_thread_plan_items} at publish time so publishing never
 * triggers a second LLM request.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_scheduled_posts")
public class AiScheduledPost {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "persona_id", length = 32, nullable = false)
    private String personaId;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Lob
    @Column(name = "body", columnDefinition = "LONGTEXT", nullable = false)
    private String body;

    @Lob
    @Column(name = "candidates_json", columnDefinition = "LONGTEXT")
    private String candidatesJson;

    @Column(name = "scheduled_publish_at", nullable = false)
    private Instant scheduledPublishAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ScheduledPostStatus status = ScheduledPostStatus.SCHEDULED;

    @Column(name = "published_post_id", length = 32)
    private String publishedPostId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "provider", length = 16)
    private String provider;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "origin", length = 24, nullable = false)
    @Builder.Default
    private String origin = "NIGHTLY_BATCH";

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
