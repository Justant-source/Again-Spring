package com.againspring.domain.community;

import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.enums.PublishMode;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 커뮤니티 포스트 (V17 커뮤니티)
 * 사용자가 공개한 관계 이야기
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "posts")
@EntityListeners(AuditingEntityListener.class)
public class Post {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 32)
    private String authorId;

    @Column(length = 36)
    private String sessionId;

    @Column(length = 200)
    private String title;

    @Column(length = 200)
    private String userTitle;

    /** 마케팅 훅 제목 (IG 등). 생성 시 1회 LLM, ≤20자. */
    @Column(name = "promo_title", length = 20)
    private String promoTitle;

    /**
     * X/IG 캡쳐 전반부 마지막 개행 블록(1-based).
     * 본문 비어 있지 않은 줄이 13개 이상일 때만 설정. null = 분할 없음.
     */
    @Column(name = "capture_split_after_line")
    private Integer captureSplitAfterLine;

    @Column(nullable = false)
    @Builder.Default
    private Integer jurorCount = 3;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private PostCategory category;

    @Column(columnDefinition = "LONGTEXT")
    private String bodyRaw;

    @Column(columnDefinition = "LONGTEXT")
    private String bodyPublished;

    @Column(unique = true, length = 64)
    private String inviteToken;

    @Column(length = 32)
    private String partnerUserId;

    @Column(columnDefinition = "LONGTEXT")
    private String partnerBodyRaw;

    @Column(columnDefinition = "LONGTEXT")
    private String partnerBodyPublished;

    private Instant partnerAnsweredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PublishMode publishMode = PublishMode.PUBLISH_NOW;

    private Integer voteDurationHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PostVisibility visibility = PostVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PostStatus status = PostStatus.DRAFT;

    @Column(nullable = false)
    @Builder.Default
    private Boolean neutralizationPassed = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "vote_close_at")
    private Instant voteCloseAt;

    @CreatedDate
    @Column(nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    /** 본문 또는 상대방 입장 변경 시 증가. 계획형 AI-user의 무효화 기준이다. */
    @Column(name = "content_revision", nullable = false)
    @Builder.Default
    private Integer contentRevision = 1;

    public void advanceContentRevision() {
        contentRevision = contentRevision == null ? 1 : contentRevision + 1;
    }

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_admin_id", length = 32)
    private String deletedByAdminId;

    @Column(name = "created_by_admin", nullable = false)
    @Builder.Default
    private Boolean createdByAdmin = false;

    // ── 원본 비교 기능: 재구성 출처 스냅샷 (재구성 모드 생성 시만 비-null) ─────────────────
    /** example_bank.id — 재구성 원본 크롤 행 ID. null = 일반(창작) 생성 */
    @Column(name = "source_example_id")
    private Long sourceExampleId;

    /** 크롤 커뮤니티 식별자 (예: natepan, dcinside) */
    @Column(name = "source_community", length = 64)
    private String sourceCommunity;

    /** 크롤 원본 URL */
    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    /** 크롤 원본 제목 스냅샷 */
    @Column(name = "source_original_title", length = 512)
    private String sourceOriginalTitle;

    /** 크롤 원본 본문 스냅샷 (최대 2000자) */
    @Column(name = "source_original_body", columnDefinition = "LONGTEXT")
    private String sourceOriginalBody;
}
