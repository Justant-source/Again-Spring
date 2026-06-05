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
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_admin_id", length = 32)
    private String deletedByAdminId;
}
