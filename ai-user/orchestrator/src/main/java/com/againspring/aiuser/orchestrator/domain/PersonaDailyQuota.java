package com.againspring.aiuser.orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "persona_daily_quota")
@IdClass(PersonaDailyQuotaId.class)
public class PersonaDailyQuota {

    @Id
    @Column(name = "persona_id", length = 32, nullable = false)
    private String personaId;

    @Id
    @Column(name = "day_bucket", nullable = false)
    private LocalDate dayBucket;

    @Column(name = "target_posts", nullable = false)
    @Builder.Default
    private Integer targetPosts = 0;

    @Column(name = "target_comments", nullable = false)
    @Builder.Default
    private Integer targetComments = 0;

    @Column(name = "target_replies", nullable = false)
    @Builder.Default
    private Integer targetReplies = 0;

    @Column(name = "target_votes", nullable = false)
    @Builder.Default
    private Integer targetVotes = 0;

    @Column(name = "target_likes", nullable = false)
    @Builder.Default
    private Integer targetLikes = 0;

    @Column(name = "target_views", nullable = false)
    @Builder.Default
    private Integer targetViews = 0;

    @Column(name = "done_posts", nullable = false)
    @Builder.Default
    private Integer donePosts = 0;

    @Column(name = "done_comments", nullable = false)
    @Builder.Default
    private Integer doneComments = 0;

    @Column(name = "done_replies", nullable = false)
    @Builder.Default
    private Integer doneReplies = 0;

    @Column(name = "done_votes", nullable = false)
    @Builder.Default
    private Integer doneVotes = 0;

    @Column(name = "done_likes", nullable = false)
    @Builder.Default
    private Integer doneLikes = 0;

    @Column(name = "done_views", nullable = false)
    @Builder.Default
    private Integer doneViews = 0;
}
