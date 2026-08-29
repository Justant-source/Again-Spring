package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Post excluded from the marketing holding pool by {@code MarketingHoldingContentGuard}
 * (V121). One row per post — records why an auto-generated candidate looked like
 * something other than an A-vs-B conflict story (e.g. a historical trivia repost or a
 * pros/cons listicle) so an admin can audit false positives later instead of the post
 * silently never appearing on the waiting board.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_holding_exclusion")
public class MarketingHoldingExclusion {

    @Id
    @Column(name = "post_id", length = 32, nullable = false)
    private String postId;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    @Column(name = "detected_at", nullable = false)
    @Builder.Default
    private Instant detectedAt = Instant.now();
}
