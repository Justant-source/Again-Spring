package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Operator voice sample for Justant-Bot. TIMELINE = operator-typed X replies.
 * TIMELINE_POST = operator original posts. DELETED_AUTO = bot comments the operator removed.
 * DRILL is unused leftover (Telegram drill removed).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "x_persona_example", indexes = {
    @Index(name = "idx_xpe_source_created", columnList = "source, created_at")
})
public class XPersonaExample {

    public enum Source {
        DRILL,
        TIMELINE,
        TIMELINE_POST,
        DELETED_AUTO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private Source source;

    @Column(name = "tweet_id", length = 64, unique = true)
    private String tweetId;

    @Column(name = "post_text", columnDefinition = "TEXT")
    private String postText;

    @Column(name = "has_photo", nullable = false)
    @Builder.Default
    private boolean hasPhoto = false;

    @Column(name = "operator_body", nullable = false, columnDefinition = "TEXT")
    private String operatorBody;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
