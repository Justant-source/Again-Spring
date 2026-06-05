package com.againspring.domain.audit;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 관리자 감사 로그 (V63)
 * 관리자의 모든 작업(포스트/댓글 삭제, 사용자 정지, 신고 처리 등)을 기록
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "admin_audit_logs")
@EntityListeners(AuditingEntityListener.class)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false, length = 32)
    private String actorUserId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "target_type", length = 40)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "before_json", columnDefinition = "JSON")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "JSON")
    private String afterJson;

    @Column(length = 45)
    private String ip;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
