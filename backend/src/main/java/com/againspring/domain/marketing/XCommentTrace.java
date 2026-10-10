package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One Justant-Bot comment attempt: source text, model call, and publish outcome.
 * Append-only. Photo bytes stay out; {@code hasPhoto} is the only image signal.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "x_comment_trace", indexes = {
    @Index(name = "idx_xct_kind_created", columnList = "kind, created_at"),
    @Index(name = "idx_xct_status_created", columnList = "status, created_at"),
    @Index(name = "idx_xct_target", columnList = "target_tweet_id"),
    @Index(name = "idx_xct_action", columnList = "action_id"),
    @Index(name = "idx_xct_author_created", columnList = "target_author_handle, created_at")
})
public class XCommentTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_id")
    private Long actionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private XOpsAction.Kind kind;

    @Column(name = "target_tweet_id", length = 64)
    private String targetTweetId;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "effort", length = 16)
    private String effort;

    @Column(name = "input_text", columnDefinition = "MEDIUMTEXT")
    private String inputText;

    @Column(name = "context_text", columnDefinition = "MEDIUMTEXT")
    private String contextText;

    @Column(name = "llm_prompt", columnDefinition = "LONGTEXT")
    private String llmPrompt;

    @Column(name = "llm_response", columnDefinition = "LONGTEXT")
    private String llmResponse;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "has_photo", nullable = false)
    @Builder.Default
    private boolean hasPhoto = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private XOpsAction.Status status;

    @Column(name = "skip_reason", length = 32)
    private String skipReason;

    /** OUTBOUND target author (lowercase, no @). Null for other kinds / pre-V128 rows. */
    @Column(name = "target_author_handle", length = 64)
    private String targetAuthorHandle;

    @Column(name = "posted_tweet_id", length = 64)
    private String postedTweetId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
