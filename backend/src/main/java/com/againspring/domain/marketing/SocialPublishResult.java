package com.againspring.domain.marketing;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 소셜 플랫폼별 발행 결과 엔티티
 * content_id + platform 별로 발행 상태/URL/에러 추적
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "social_publish_results")
@EntityListeners(AuditingEntityListener.class)
@Slf4j
public class SocialPublishResult {

    public enum ResultState {
        PENDING, SUCCEEDED, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketingContent.Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ResultState state = ResultState.PENDING;

    @Column(name = "published_url", length = 500)
    private String publishedUrl;

    @Column(name = "error_reason", columnDefinition = "TEXT")
    private String errorReason;

    @CreatedDate
    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;
}
