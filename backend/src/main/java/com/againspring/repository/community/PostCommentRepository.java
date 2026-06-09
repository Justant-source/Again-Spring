package com.againspring.repository.community;

import com.againspring.domain.community.PostComment;
import com.againspring.domain.enums.CommentStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 포스트 댓글 저장소 (V17 커뮤니티)
 */
@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    /**
     * 포스트의 최상위 댓글 조회 (생성순)
     */
    List<PostComment> findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(String postId);

    /**
     * 댓글의 답글 조회 (생성순)
     */
    List<PostComment> findByParentCommentIdOrderByCreatedAtAsc(Long parentCommentId);

    /**
     * 포스트의 전체 댓글 수 (최상위 + 대댓글, 상태 무관 — 관리/통계용)
     */
    long countByPostId(String postId);

    /** 특정 작성자의 댓글 수 (소프트 삭제 제외) */
    long countByAuthorIdAndDeletedAtIsNull(String authorId);

    /**
     * 공개용 댓글 수: 차단/삭제 제외 (status=ACTIVE, deletedAt IS NULL)
     */
    long countByPostIdAndStatusAndDeletedAtIsNull(String postId, CommentStatus status);

    /**
     * 관리자용: 상태별 댓글 조회 (삭제되지 않은 댓글만)
     */
    Page<PostComment> findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            CommentStatus status, Pageable pageable);

    /**
     * 관리자용: 여러 상태의 댓글 조회
     */
    @Query("SELECT pc FROM PostComment pc WHERE pc.status IN :statuses AND pc.deletedAt IS NULL ORDER BY pc.createdAt DESC")
    Page<PostComment> findByStatusInAndDeletedAtIsNull(
            @Param("statuses") List<CommentStatus> statuses, Pageable pageable);

    /**
     * 공개 피드: 최상위 댓글 — 차단/삭제 제외 (status=ACTIVE, deletedAt IS NULL)
     */
    List<PostComment> findByPostIdAndParentCommentIdIsNullAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
            String postId, CommentStatus status);

    /**
     * 공개 피드: 답글 — 차단/삭제 제외 (status=ACTIVE, deletedAt IS NULL)
     */
    List<PostComment> findByParentCommentIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
            Long parentCommentId, CommentStatus status);

    /**
     * 관리자용: 포스트의 모든 댓글 (상태 무관, 삭제 포함)
     */
    Page<PostComment> findByPostIdOrderByCreatedAtDesc(String postId, Pageable pageable);
}
