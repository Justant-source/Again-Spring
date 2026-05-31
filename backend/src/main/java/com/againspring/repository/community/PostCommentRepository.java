package com.againspring.repository.community;

import com.againspring.domain.community.PostComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
