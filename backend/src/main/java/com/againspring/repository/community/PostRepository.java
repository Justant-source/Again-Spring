package com.againspring.repository.community;

import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import java.util.List;
import java.util.Optional;
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
}
