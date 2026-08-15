package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Per-post marketing waiting-board row: seeded draft, score/rank snapshot, lock after commit.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_holding")
@EntityListeners(AuditingEntityListener.class)
public class MarketingHolding {

    @Id
    @Column(name = "post_id", length = 32, nullable = false)
    private String postId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MarketingHoldingStatus status = MarketingHoldingStatus.IN_POOL;

    @Enumerated(EnumType.STRING)
    @Column(name = "pin_format", length = 10)
    private MarketingPinFormat pinFormat;

    @Column(name = "draft_json", columnDefinition = "JSON")
    private String draftJson;

    @Column(name = "score_snapshot")
    private Double scoreSnapshot;

    @Column(name = "rank_snapshot")
    private Integer rankSnapshot;

    /** Actual 1-based rank for each platform selected at T+24h, serialized as JSON. */
    @Column(name = "platform_rank_snapshot", columnDefinition = "JSON")
    private String platformRankSnapshot;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isDraftLocked() {
        return lockedAt != null;
    }
}
