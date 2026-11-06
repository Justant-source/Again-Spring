package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Append-only exchange log with a followed account. Unique on (handle, direction, kind, occurred_at). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "x_follow_event")
public class XFollowEvent {

    /** IN = they did it to us, OUT = we did it to them. */
    public enum Direction { IN, OUT }

    public enum Kind { FOLLOW, UNFOLLOW, FOLLOW_BACK, LIKE, REPOST, REPLY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handle", nullable = false, length = 64)
    private String handle;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 8)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24)
    private Kind kind;

    @Column(name = "tweet_id", length = 64)
    private String tweetId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
