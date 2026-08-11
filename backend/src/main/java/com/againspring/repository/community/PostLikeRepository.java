package com.againspring.repository.community;

import com.againspring.domain.community.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 포스트/댓글 좋아요 저장소 (V17 커뮤니티)
 */
@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    /**
     * 포스트 좋아요 존재 여부
     */
    boolean existsByPostIdAndUserId(String postId, String userId);

    /**
     * 댓글 좋아요 존재 여부
     */
    boolean existsByCommentIdAndUserId(Long commentId, String userId);

    /**
     * 포스트 좋아요 삭제
     */
    void deleteByPostIdAndUserId(String postId, String userId);

    /** 포스트 full delete 시 글 단위 좋아요 hard delete */
    void deleteByPostId(String postId);

    /**
     * 댓글 좋아요 삭제
     */
    void deleteByCommentIdAndUserId(Long commentId, String userId);

    /**
     * 특정 댓글의 모든 좋아요 삭제 (댓글 삭제 시 정리)
     */
    void deleteByCommentId(Long commentId);

    /**
     * 포스트 좋아요 수
     */
    Long countByPostId(String postId);

    /**
     * 댓글 좋아요 수
     */
    Long countByCommentId(Long commentId);

    /**
     * 포스트의 특정 유저가 소유한 좋아요 찾기
     */
    java.util.Optional<PostLike> findByPostIdAndUserId(String postId, String userId);

    /**
     * 댓글의 특정 유저가 소유한 좋아요 찾기
     */
    java.util.Optional<PostLike> findByCommentIdAndUserId(Long commentId, String userId);

    /**
     * 포스트의 모든 좋아요 중 특정 유저 목록에 속하는 것 찾기
     */
    java.util.List<PostLike> findByPostIdAndUserIdIn(String postId, java.util.Collection<String> userIds);

    /**
     * 댓글의 모든 좋아요 중 특정 유저 목록에 속하는 것 찾기
     */
    java.util.List<PostLike> findByCommentIdAndUserIdIn(Long commentId, java.util.Collection<String> userIds);
}
