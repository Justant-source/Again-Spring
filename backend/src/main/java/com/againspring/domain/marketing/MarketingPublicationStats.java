package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Platform engagement snapshot for a marketing job publication (Phase 2.6).
 * Collected via ASM best-effort APIs; used for weekly reports and optional weight nudge.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_publication_stats", indexes = {
    @Index(name = "idx_mps_job", columnList = "job_id"),
    @Index(name = "idx_mps_post_platform", columnList = "post_id,platform"),
    @Index(name = "idx_mps_collected", columnList = "collected_at")
})
@EntityListeners(AuditingEntityListener.class)
public class MarketingPublicationStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "post_id", nullable = false, length = 32)
    private String postId;

    @Column(nullable = false, length = 40)
    private String platform;

    @Column(name = "remote_job_id", length = 64)
    private String remoteJobId;

    @Column(name = "remote_id", length = 120)
    private String remoteId;

    @Column(length = 500)
    private String url;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "metrics_json", nullable = false, columnDefinition = "JSON")
    private String metricsJson;

    @Column(nullable = false)
    @Builder.Default
    private Boolean partial = true;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
