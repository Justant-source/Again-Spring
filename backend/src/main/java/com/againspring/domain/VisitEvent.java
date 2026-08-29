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

    /** 30일 쿠키 기반 고유 방문자 키. 세션(session_key)보다 상위 — 재방문을 센다. */
    @Column(name = "visitor_key", length = 64)
    private String visitorKey;

    /** 봇 판정 근거. 규칙이 바뀌어도 과거 행을 재분류할 수 있게 원문을 남긴다. */
    @Column(name = "user_agent", length = 300)
    private String userAgent;

    /** 집계는 항상 isBot=false로 필터한다. 봇 행도 버리지 않고 남겨 오탐을 추적한다. */
    @Column(name = "is_bot", nullable = false)
    @Builder.Default
    private boolean bot = false;

    @Column(length = 8)
    private String country;

    @Column(name = "device_type", length = 16)
    private String deviceType;

    /** 로그인/게스트 상태면 기록 — 방문에서 투표·가입까지 이어 붙이기 위한 고리. */
    @Column(name = "user_id", length = 32)
    private String userId;

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
