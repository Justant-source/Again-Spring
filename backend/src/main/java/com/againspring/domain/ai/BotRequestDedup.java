package com.againspring.domain.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Internal, synthetic-user-only write idempotency mapping.
 *
 * <p>The key belongs to the orchestrator's plan item and maps to the post or
 * comment that was committed by the backend. It intentionally does not live on
 * the public community entities.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bot_request_dedup")
public class BotRequestDedup {

    @Id
    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;

    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "bot_user_id", nullable = false, length = 32)
    private String botUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
