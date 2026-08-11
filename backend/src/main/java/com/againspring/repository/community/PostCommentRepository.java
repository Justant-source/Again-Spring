package com.againspring.repository.community;

import com.againspring.domain.community.PostComment;
import com.againspring.domain.enums.CommentStatus;
import java.util.Collection;
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
     * 포스트의 최상위 댓글 조회 (레거시·무필터, 생성 오름차순)
     */
    List<PostComment> findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(String postId);

    /**
     * 댓글의 답글 조회 (무필터 — 삭제 cascade용, 순서 무관)
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
     * — 관리/통계·레거시용. 목록과 맞추려면 {@link #countVisibleByPostId} 사용.
     */
    long countByPostIdAndStatusAndDeletedAtIsNull(String postId, CommentStatus status);

    /**
     * 공개 목록에 노출 가능한 댓글 수 (최상위 + visible parent 아래 대댓글).
     * 부모 soft-delete/BLOCKED 로 목록에 안 나오는 ACTIVE 고아 대댓글은 제외.
     */
    @Query("""
            SELECT COUNT(pc) FROM PostComment pc
            WHERE pc.postId = :postId
              AND pc.status = :status
              AND pc.deletedAt IS NULL
              AND (
                pc.parentCommentId IS NULL
                OR EXISTS (
                  SELECT 1 FROM PostComment parent
                  WHERE parent.id = pc.parentCommentId
                    AND parent.status = :status
                    AND parent.deletedAt IS NULL
                )
              )
            """)
    long countVisibleByPostId(@Param("postId") String postId, @Param("status") CommentStatus status);

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
     * 공개 피드: 최상위 댓글 — 차단/삭제 제외, 최신순 (status=ACTIVE, deletedAt IS NULL)
     */
    List<PostComment> findByPostIdAndParentCommentIdIsNullAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            String postId, CommentStatus status);

    /**
     * 공개 피드: 답글 — 차단/삭제 제외, 최신순 (status=ACTIVE, deletedAt IS NULL)
     */
    List<PostComment> findByParentCommentIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long parentCommentId, CommentStatus status);

    /**
     * 관리자용: 포스트의 모든 댓글 (상태 무관, 삭제 포함)
     */
    Page<PostComment> findByPostIdOrderByCreatedAtDesc(String postId, Pageable pageable);

    /** 관리자 스레드 편집: 미삭제 댓글·대댓글 (작성 시각 ASC) */
    List<PostComment> findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(String postId);

    /** 목록용: 포스트별 미삭제 댓글 수 */
    @Query("SELECT pc.postId, COUNT(pc) FROM PostComment pc WHERE pc.postId IN :postIds AND pc.deletedAt IS NULL GROUP BY pc.postId")
    List<Object[]> countUndeletedByPostIds(@Param("postIds") Collection<String> postIds);

    /** 포스트의 모든 댓글 (상태·삭제 무관 — full delete cascade용) */
    List<PostComment> findByPostId(String postId);

    /** 포스트 full delete 시 댓글 hard delete */
    void deleteByPostId(String postId);
}
