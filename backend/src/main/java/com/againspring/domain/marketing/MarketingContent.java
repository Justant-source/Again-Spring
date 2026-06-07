package com.againspring.domain.marketing;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 마케팅 콘텐츠 엔티티 (MariaDB JPA)
 * 시뮬레이션 결과로부터 생성된 플랫폼별 마케팅 콘텐츠
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_contents")
@EntityListeners(AuditingEntityListener.class)
public class MarketingContent {

    public enum Platform {
        X, INSTAGRAM, NAVER_BLOG, THREADS, FACEBOOK
    }

    public enum Status {
        GENERATING, DRAFT, REVIEW, APPROVED, EXPORTED, REJECTED, PUBLISHING, PARTIAL, PUBLISHED, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_post_id", length = 32)
    private String sourcePostId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "body_text", columnDefinition = "MEDIUMTEXT")
    private String bodyText;

    @Column(name = "html_template", columnDefinition = "MEDIUMTEXT")
    private String htmlTemplate;

    @Column(name = "image_paths", columnDefinition = "TEXT")
    private String imagePaths;

    @Column(name = "hashtags", columnDefinition = "TEXT")
    private String hashtags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(name = "safety_check_json", columnDefinition = "TEXT")
    private String safetyCheckJson;

    @Column(name = "edited_by")
    private Long editedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_url", length = 500)
    private String publishedUrl;

    @Column(name = "performance_json", columnDefinition = "JSON")
    private String performanceJson;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "parent_content_id")
    private Long parentContentId;

    @Column(name = "repurpose_source_id")
    private Long repurposeSourceId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
