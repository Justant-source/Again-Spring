package com.againspring.domain.community;

import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.enums.PublishMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import io.hypersistence.utils.hibernate.type.json.JsonType;

import java.time.Instant;
import java.util.List;

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

    /** IG 훅 제목. 원제 복제+의미줄바꿈(\\n). 생성 시 1회 LLM/PLAN. */
    @Column(name = "promo_title", length = 500)
    private String promoTitle;

    /**
     * 메타포 일러스트 ID (60종 카탈로그). AI PLAN이 사연 생성 시 매칭.
     * 예: empty-chair, tangled-thread. Shorts intro / FE 카드용.
     */
    @Column(name = "metaphor_id", length = 64)
    private String metaphorId;

    /**
     * X/IG 캡쳐 전반부 마지막 개행 블록(1-based).
     * @deprecated use {@link #captureSplitAfterLines}; kept for read fallback.
     */
    @Deprecated
    @Column(name = "capture_split_after_line")
    private Integer captureSplitAfterLine; // prefer captureSplitAfterLines; kept for DB/API fallback

    /**
     * X/IG 캡쳐 컷 목록(1-based). 각 원소 = 해당 장(마지막 장 제외)의 마지막 개행 블록.
     * null/empty = 1장(미분할) 또는 마케팅 잡 시 휴리스틱.
     */
    @Type(JsonType.class)
    @Column(name = "capture_split_after_lines", columnDefinition = "JSON")
    private List<Integer> captureSplitAfterLines;

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

    /**
     * Partner body capture cuts (same semantics as {@link #captureSplitAfterLines}).
     */
    @Type(JsonType.class)
    @Column(name = "partner_capture_split_after_lines", columnDefinition = "JSON")
    private List<Integer> partnerCaptureSplitAfterLines;

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
