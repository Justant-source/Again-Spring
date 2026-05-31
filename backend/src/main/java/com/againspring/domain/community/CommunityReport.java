package com.againspring.domain.community;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 커뮤니티 신고 (V17 커뮤니티)
 * 부적절한 포스트, 댓글 신고
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "community_reports")
@EntityListeners(AuditingEntityListener.class)
public class CommunityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String targetType;

    @Column(nullable = false, length = 64)
    private String targetId;

    @Column(name = "reporter_user_id", length = 32)
    private String reporterUserId;

    @Column(length = 100)
    private String reason;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
