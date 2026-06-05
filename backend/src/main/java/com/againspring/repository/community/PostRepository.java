package com.againspring.repository.community;

import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 포스트 저장소 (V17 커뮤니티)
 */
@Repository
public interface PostRepository extends JpaRepository<Post, String> {

    /**
     * 공개 여부 및 상태로 포스트 조회 (생성순 역순)
     */
    List<Post> findByVisibilityAndStatusOrderByCreatedAtDesc(
            PostVisibility visibility, PostStatus status, Pageable pageable);

    /**
     * 공개 여부, 상태, 카테고리로 포스트 조회 (생성순 역순)
     */
    List<Post> findByVisibilityAndStatusAndCategoryOrderByCreatedAtDesc(
            PostVisibility visibility, PostStatus status, PostCategory category, Pageable pageable);

    /**
     * 작성자별 포스트 조회 (생성순 역순)
     */
    List<Post> findByAuthorIdOrderByCreatedAtDesc(String authorId);

    /**
     * 초대 토큰으로 포스트 조회 (C3 파트너 초대)
     */
    Optional<Post> findByInviteToken(String inviteToken);

    /** 전체 건수 — 페이지네이션 total count */
    long countByVisibilityAndStatus(PostVisibility visibility, PostStatus status);

    /** 카테고리별 전체 건수 */
    long countByVisibilityAndStatusAndCategory(PostVisibility visibility, PostStatus status, PostCategory category);

    /**
     * 추천순 조회 (전체) — Hacker News 스타일 시간 감쇠 + 재부상 보너스
     * score = (4×likes + 3×comments + 2.5×votes + 0.2×views + 1) / (age_h+2)^1.5 + recency_bonus
     */
    @Query(value = """
            SELECT p.* FROM posts p
            LEFT JOIN (
                SELECT post_id, COUNT(*) cnt, MAX(created_at) last_at
                FROM post_likes WHERE post_id IS NOT NULL GROUP BY post_id
            ) pl ON pl.post_id = p.id
            LEFT JOIN (
                SELECT post_id, COUNT(*) cnt, MAX(created_at) last_at
                FROM post_comments GROUP BY post_id
            ) pc ON pc.post_id = p.id
            LEFT JOIN (
                SELECT post_id, COUNT(*) cnt, MAX(created_at) last_at
                FROM votes GROUP BY post_id
            ) v ON v.post_id = p.id
            WHERE p.visibility = 'PUBLIC' AND p.status = 'VOTING'
            ORDER BY (
                (4.0*COALESCE(pl.cnt,0) + 3.0*COALESCE(pc.cnt,0) + 2.5*COALESCE(v.cnt,0) + 0.2*p.view_count + 1.0)
                / POWER(TIMESTAMPDIFF(SECOND, p.created_at, NOW())/3600.0 + 2, 1.5)
                + CASE
                    WHEN TIMESTAMPDIFF(SECOND,
                           GREATEST(p.created_at, COALESCE(pc.last_at, p.created_at), COALESCE(v.last_at, p.created_at)),
                           NOW()) / 3600.0 < 6
                    THEN 5.0 / (TIMESTAMPDIFF(SECOND,
                           GREATEST(p.created_at, COALESCE(pc.last_at, p.created_at), COALESCE(v.last_at, p.created_at)),
                           NOW()) / 3600.0 + 1)
                    ELSE 0
                  END
            ) DESC, p.created_at DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Post> findRecommended(@Param("limit") int limit, @Param("offset") int offset);

    /** 추천순 조회 (카테고리 필터) */
    @Query(value = """
            SELECT p.* FROM posts p
            LEFT JOIN (
                SELECT post_id, COUNT(*) cnt, MAX(created_at) last_at
                FROM post_likes WHERE post_id IS NOT NULL GROUP BY post_id
            ) pl ON pl.post_id = p.id
            LEFT JOIN (
                SELECT post_id, COUNT(*) cnt, MAX(created_at) last_at
                FROM post_comments GROUP BY post_id
            ) pc ON pc.post_id = p.id
            LEFT JOIN (
                SELECT post_id, COUNT(*) cnt, MAX(created_at) last_at
                FROM votes GROUP BY post_id
            ) v ON v.post_id = p.id
            WHERE p.visibility = 'PUBLIC' AND p.status = 'VOTING' AND p.category = :category
            ORDER BY (
                (4.0*COALESCE(pl.cnt,0) + 3.0*COALESCE(pc.cnt,0) + 2.5*COALESCE(v.cnt,0) + 0.2*p.view_count + 1.0)
                / POWER(TIMESTAMPDIFF(SECOND, p.created_at, NOW())/3600.0 + 2, 1.5)
                + CASE
                    WHEN TIMESTAMPDIFF(SECOND,
                           GREATEST(p.created_at, COALESCE(pc.last_at, p.created_at), COALESCE(v.last_at, p.created_at)),
                           NOW()) / 3600.0 < 6
                    THEN 5.0 / (TIMESTAMPDIFF(SECOND,
                           GREATEST(p.created_at, COALESCE(pc.last_at, p.created_at), COALESCE(v.last_at, p.created_at)),
                           NOW()) / 3600.0 + 1)
                    ELSE 0
                  END
            ) DESC, p.created_at DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Post> findRecommendedByCategory(@Param("category") String category,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :postId")
    void incrementViewCount(@Param("postId") String postId);

    /** 배심원이 부족한 포스트 ID 목록 (startup 복구용, native 쿼리로 enum 변환 오류 회피) */
    @Query(value = """
            SELECT p.id FROM posts p
            LEFT JOIN jurors j ON p.id = j.post_id
            WHERE p.juror_count > 0
            GROUP BY p.id, p.juror_count
            HAVING COUNT(j.id) < p.juror_count
            """, nativeQuery = true)
    List<String> findPostIdsNeedingJury();

    /**
     * 관리자용: 상태별 포스트 조회 (삭제되지 않은 포스트만)
     */
    Page<Post> findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            PostStatus status, Pageable pageable);

    /**
     * 관리자용: 여러 상태의 포스트 조회
     */
    @Query("SELECT p FROM Post p WHERE p.status IN :statuses AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    Page<Post> findByStatusInAndDeletedAtIsNull(
            @Param("statuses") List<PostStatus> statuses, Pageable pageable);

    /**
     * 공개 피드: 삭제되고 차단되지 않은 포스트만 표시
     */
    Page<Post> findByVisibilityAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            PostVisibility visibility, PostStatus status, Pageable pageable);

    /** 관리자용: 삭제되지 않은 게시글 총 건수 */
    long countByDeletedAtIsNull();

    /** 관리자용: 지정된 기간에 생성된 게시글 건수 */
    long countByDeletedAtIsNullAndCreatedAtBetween(java.time.Instant from, java.time.Instant to);
}
