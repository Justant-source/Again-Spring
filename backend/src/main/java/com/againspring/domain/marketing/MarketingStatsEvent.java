package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Append-only marketing stats activity event (Phase 3).
 * Timeline rows for collect / propose / apply / shadow-toggle actions.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_stats_event", indexes = {
    @Index(name = "idx_mse_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class MarketingStatsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(length = 32)
    private String platform;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
