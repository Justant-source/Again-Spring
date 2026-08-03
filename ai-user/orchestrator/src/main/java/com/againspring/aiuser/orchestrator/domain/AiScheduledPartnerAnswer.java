package com.againspring.aiuser.orchestrator.domain;

import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPartnerAnswerStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Partner answer scheduled for T0+Δ after a paired author post went PUBLIC.
 * Call2 body generation happens at fire time (not at hold time).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_scheduled_partner_answers")
public class AiScheduledPartnerAnswer {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "post_id", length = 32, nullable = false)
    private String postId;

    @Column(name = "invite_token", length = 64, nullable = false)
    private String inviteToken;

    @Column(name = "author_persona_id", length = 32, nullable = false)
    private String authorPersonaId;

    @Column(name = "partner_persona_id", length = 32, nullable = false)
    private String partnerPersonaId;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "author_title", length = 200)
    private String authorTitle;

    @Lob
    @Column(name = "author_body", columnDefinition = "LONGTEXT")
    private String authorBody;

    @Column(name = "correlation_id", length = 32)
    private String correlationId;

    @Column(name = "scheduled_post_id", length = 36)
    private String scheduledPostId;

    @Column(name = "scheduled_partner_at", nullable = false)
    private Instant scheduledPartnerAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ScheduledPartnerAnswerStatus status = ScheduledPartnerAnswerStatus.SCHEDULED;

    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

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
