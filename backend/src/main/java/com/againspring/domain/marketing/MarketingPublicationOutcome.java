package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** 게시 48시간 성과 점수(V132). (job, platform)당 1행. status = SCORED | MISSING. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_publication_outcome")
public class MarketingPublicationOutcome {

    public static final String SCORED = "SCORED";
    public static final String MISSING = "MISSING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "post_id", nullable = false, length = 32)
    private String postId;

    @Column(name = "platform", nullable = false, length = 40)
    private String platform;

    @Column(name = "format_arm", length = 32)
    private String formatArm;

    @Column(name = "category", length = 20)
    private String category;

    @Column(name = "hook_emotion", length = 16)
    private String hookEmotion;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "snapshot_at")
    private Instant snapshotAt;

    @Column(name = "primary_metric")
    private Long primaryMetric;

    @Column(name = "engagement")
    private Long engagement;

    @Column(name = "view_ratio")
    private Double viewRatio;

    @Column(name = "eng_ratio")
    private Double engRatio;

    @Column(name = "reward")
    private Double reward;

    @Column(name = "breakout", nullable = false)
    private boolean breakout;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public boolean isScored() {
        return SCORED.equals(status);
    }
}
