package com.againspring.domain.marketing;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 마케팅 콘텐츠 감사 로그 엔티티
 * 콘텐츠 승인/거부 등의 관리 작업을 추적
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_audit_logs")
@EntityListeners(AuditingEntityListener.class)
public class MarketingAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id")
    private Long contentId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "actor_user_id", nullable = false, length = 32)
    private String actorUserId;

    @Column(name = "payload_json", columnDefinition = "JSON")
    private String payloadJson;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
