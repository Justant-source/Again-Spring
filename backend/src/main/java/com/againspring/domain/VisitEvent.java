package com.againspring.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 방문 이벤트 추적 (마케팅 캠페인·전환 분석용)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "visit_events", indexes = {
    @Index(name = "idx_ve_occurred_at", columnList = "occurred_at"),
    @Index(name = "idx_ve_campaign", columnList = "utm_campaign, occurred_at"),
    @Index(name = "idx_ve_path", columnList = "path")
})
public class VisitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(length = 100)
    private String utmSource;

    @Column(length = 100)
    private String utmMedium;

    @Column(length = 100)
    private String utmCampaign;

    @Column(length = 100)
    private String utmContent;

    @Column(length = 500)
    private String referrer;

    @Column(length = 64)
    private String sessionKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
