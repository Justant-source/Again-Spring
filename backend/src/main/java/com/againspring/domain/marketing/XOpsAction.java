package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Append-only ledger of X ops actions (ritual / inbound / outbound).
 * One row per attempt; any row for a target_tweet_id blocks a double-reply.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "x_ops_action", indexes = {
    @Index(name = "idx_xoa_kind_created", columnList = "kind, created_at"),
    @Index(name = "idx_xoa_target_tweet", columnList = "target_tweet_id"),
    @Index(name = "idx_xoa_our_post_created", columnList = "our_post_tweet_id, created_at")
})
public class XOpsAction {

    public enum Kind {
        RITUAL,
        INBOUND,
        OUTBOUND
    }

    public enum Status {
        POSTED,
        SKIPPED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private Kind kind;

    @Column(name = "target_tweet_id", length = 64)
    private String targetTweetId;

    @Column(name = "parent_tweet_id", length = 64)
    private String parentTweetId;

    @Column(name = "our_post_tweet_id", length = 64)
    private String ourPostTweetId;

    @Column(name = "posted_tweet_id", length = 64)
    private String postedTweetId;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "skip_reason", length = 32)
    private String skipReason;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
