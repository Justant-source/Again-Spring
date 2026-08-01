package com.againspring.aiuser.orchestrator.domain;

import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemStatus;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Durable scheduled unit. A unique idempotency key prevents duplicate posting after a lease retry. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_thread_plan_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_thread_plan_item_idempotency", columnNames = "idempotency_key"))
public class AiThreadPlanItem {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "plan_id", length = 36, nullable = false)
    private String planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 20, nullable = false)
    private ThreadPlanItemType itemType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ThreadPlanItemStatus status = ThreadPlanItemStatus.RESERVED;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "parent_item_id", length = 36)
    private String parentItemId;

    @Column(name = "persona_id", length = 32)
    private String personaId;

    @Column(name = "target_post_id", length = 32, nullable = false)
    private String targetPostId;

    @Column(name = "target_comment_id", length = 64)
    private String targetCommentId;

    /**
     * Human user this reply answers ({@code users.id}, loose ref). Only set on human-reply items.
     * The 3 personas / 5 per persona / 15 total budget is scoped to (target_post_id, human_author_id),
     * so different humans on the same post never consume each other's budget.
     */
    @Column(name = "human_author_id", length = 32)
    private String humanAuthorId;

    @Lob
    @Column(name = "body", columnDefinition = "LONGTEXT")
    private String body;

    /** Perspective label for 80% stance-cap measurement (AUTHOR|COUNTERPART|NEUTRAL|CONTRARIAN). */
    @Column(name = "stance", length = 16)
    private String stance;

    /** Optional crawled/source example provenance; no hard FK to backend tables. */
    @Column(name = "source_example_id")
    private Long sourceExampleId;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "not_before")
    private Instant notBefore;

    @Column(name = "idempotency_key", length = 128, nullable = false)
    private String idempotencyKey;

    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "posted_target_id", length = 64)
    private String postedTargetId;

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
