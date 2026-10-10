package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Per-account rollup of outbound candidates seen and replies posted.
 * Handle is lowercase without '@'. Written by native upserts in the repository.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "x_target_account")
public class XTargetAccount {

    @Id
    @Column(name = "handle", length = 64)
    private String handle;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "seen_count", nullable = false)
    private int seenCount;

    @Column(name = "posted_count", nullable = false)
    private int postedCount;

    @Column(name = "last_posted_at")
    private Instant lastPostedAt;
}
