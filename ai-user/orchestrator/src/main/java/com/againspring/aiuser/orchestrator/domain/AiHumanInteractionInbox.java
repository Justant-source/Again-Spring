package com.againspring.aiuser.orchestrator.domain;

import com.againspring.aiuser.orchestrator.domain.enums.HumanInteractionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Idempotent inbox for human comments/replies consumed by the 30-minute batch generator. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_human_interaction_inbox", uniqueConstraints = @UniqueConstraint(
        name = "uk_human_inbox_source_comment", columnNames = "source_comment_id"))
public class AiHumanInteractionInbox {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "post_id", length = 32, nullable = false)
    private String postId;

    @Column(name = "source_comment_id", length = 64, nullable = false)
    private String sourceCommentId;

    @Column(name = "parent_comment_id", length = 64)
    private String parentCommentId;

    @Column(name = "author_id", length = 32, nullable = false)
    private String authorId;

    @Column(name = "interaction_type", length = 16, nullable = false)
    private String interactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private HumanInteractionStatus status = HumanInteractionStatus.PENDING;

    @Column(name = "observed_at", nullable = false)
    @Builder.Default
    private Instant observedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "response_item_id", length = 36)
    private String responseItemId;

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
