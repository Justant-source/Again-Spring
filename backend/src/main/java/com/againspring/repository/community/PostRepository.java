package com.againspring.repository.community;

import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * 작성자별 포스트 조회 (생성순 역순)
     */
    List<Post> findByAuthorIdOrderByCreatedAtDesc(String authorId);
}
