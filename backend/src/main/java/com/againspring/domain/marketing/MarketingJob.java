package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Marketing job for ASM (Again-Spring-Marketing) service
 * Tracks the state of marketing content generation and publishing
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_job")
@EntityListeners(AuditingEntityListener.class)
public class MarketingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 64)
    private String remoteJobId;

    @Column(nullable = false, length = 32)
    private String postId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "REQUESTED";

    @Column(length = 20)
    private String phase;

    @Column
    @Builder.Default
    private Double progress = 0.0;

    @Column(columnDefinition = "JSON")
    private String targets;

    @Column
    @Builder.Default
    private Boolean autoPublish = false;

    @Column(columnDefinition = "JSON")
    private String artifacts;

    @Column(columnDefinition = "JSON")
    private String publications;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(length = 32)
    private String requestedBy;

    @Column
    @Builder.Default
    private Integer pollFailCount = 0;

    @Column
    private Instant lastPolledAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Apply remote job state to this entity
     */
    public void applyRemote(String remoteStatus, String remotePhase, Double remoteProgress,
                           String remoteArtifacts, String remotePublications) {
        this.status = remoteStatus;
        this.phase = remotePhase;
        this.progress = remoteProgress;
        this.artifacts = remoteArtifacts;
        this.publications = remotePublications;
        this.pollFailCount = 0;
        this.lastPolledAt = Instant.now();
    }

    /**
     * Mark a polling attempt failure
     * If fail count reaches 5, mark job as STALE
     */
    public void markPollFailure() {
        this.pollFailCount++;
        if (this.pollFailCount >= 5) {
            this.status = "STALE";
        }
    }
}
