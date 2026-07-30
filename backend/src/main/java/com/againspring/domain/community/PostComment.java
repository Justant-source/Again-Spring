package com.againspring.domain.community;

import com.againspring.domain.enums.CommentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 포스트 댓글 (V17 커뮤니티)
 * 포스트에 대한 사용자 댓글 (중첩 가능)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "post_comments")
@EntityListeners(AuditingEntityListener.class)
public class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String postId;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(length = 32)
    private String authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CommentStatus status = CommentStatus.ACTIVE;

    @Column(nullable = false)
    @Builder.Default
    private Integer likeCount = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "content_revision", nullable = false)
    @Builder.Default
    private Integer contentRevision = 1;

    /** 댓글 본문 변경 시 증가. 예약된 답글 후보의 무효화 기준이다. */
    public void advanceContentRevision() {
        contentRevision = contentRevision == null ? 1 : contentRevision + 1;
    }

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_admin_id", length = 32)
    private String deletedByAdminId;
}
