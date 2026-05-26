package com.againspring.domain.marketing;

import java.math.BigDecimal;
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
 * 마케팅 소스 스토리 엔티티 (MariaDB JPA)
 * 외부 플랫폼(SNS/블로그 등)에서 수집한 원본 텍스트 및 익명화 결과
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_source_stories")
@EntityListeners(AuditingEntityListener.class)
public class MarketingSourceStory {

    public enum Status {
        PENDING, APPROVED, REJECTED, USED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "source_platform", nullable = false, length = 50)
    private String sourcePlatform;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "anonymized_text", nullable = false, columnDefinition = "TEXT")
    private String anonymizedText;

    @Column(name = "rewrite_ratio", precision = 5, scale = 2)
    private BigDecimal rewriteRatio;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "relation_type", nullable = false, length = 64)
    private String relationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "blocked_reason", length = 255)
    private String blockedReason;

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
