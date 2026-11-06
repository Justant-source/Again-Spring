package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One account we follow (or used to): follow timing, reciprocity, follower size, last inbound action. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "x_follow_account")
public class XFollowAccount {

    public enum Status { FOLLOWING, UNFOLLOWED }

    public enum FollowedAtSource { LEDGER, ESTIMATED, FIRST_SEEN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** lowercase, no @ */
    @Column(name = "handle", nullable = false, length = 64, unique = true)
    private String handle;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private Status status = Status.FOLLOWING;

    @Column(name = "followed_at")
    private Instant followedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "followed_at_source", length = 16)
    private FollowedAtSource followedAtSource;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_following_at", nullable = false)
    private Instant lastSeenFollowingAt;

    @Column(name = "follows_us", nullable = false)
    private boolean followsUs;

    @Column(name = "follows_us_since")
    private Instant followsUsSince;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "follower_count")
    private Integer followerCount;

    @Column(name = "follower_count_checked_at")
    private Instant followerCountCheckedAt;

    @Column(name = "last_inbound_interaction_at")
    private Instant lastInboundInteractionAt;

    @Column(name = "unfollowed_at")
    private Instant unfollowedAt;

    @Column(name = "unfollow_reason", length = 32)
    private String unfollowReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
