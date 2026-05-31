package com.againspring.service.community;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.community.PostLike;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostLikeRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.safety.KeywordGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * CommentService - 포스트 댓글 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final PostCommentRepository commentRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final KeywordGuard keywordGuard;

    /**
     * 댓글 작성
     *
     * @param postId 포스트 ID
     * @param parentCommentId 부모 댓글 ID (nullable, null이면 최상위 댓글)
     * @param authorId 작성자 ID
     * @param body 댓글 내용
     * @return 생성된 PostComment
     * @throws BusinessException POST_NOT_FOUND 또는 CRISIS_DETECTED
     */
    public PostComment addComment(String postId, Long parentCommentId, String authorId, String body) {
        // 포스트 존재 확인
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + postId, 404));

        // 위기 감지
        keywordGuard.scanUserInput(body, authorId);

        // 부모 댓글 존재 확인 (nullable)
        if (parentCommentId != null) {
            PostComment parent = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new BusinessException("COMMENT_NOT_FOUND", "Parent comment not found: " + parentCommentId, 404));

            if (!parent.getPostId().equals(postId)) {
                throw new BusinessException("COMMENT_MISMATCH", "Parent comment does not belong to this post", 400);
            }
        }

        PostComment comment = PostComment.builder()
                .postId(postId)
                .parentCommentId(parentCommentId)
                .authorId(authorId)
                .body(body)
                .likeCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        PostComment saved = commentRepository.save(comment);
        log.info("Comment added for post {}: comment {}", postId, saved.getId());
        return saved;
    }

    /**
     * 최상위 댓글 목록 조회 (부모 댓글 없는 댓글들)
     *
     * @param postId 포스트 ID
     * @return 최상위 댓글 목록
     */
    public List<PostComment> getTopLevelComments(String postId) {
        List<PostComment> comments = commentRepository.findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(postId);
        log.info("Listed {} top-level comments for post {}", comments.size(), postId);
        return comments;
    }

    /**
     * 특정 댓글의 대댓글 조회
     *
     * @param parentCommentId 부모 댓글 ID
     * @return 대댓글 목록
     */
    public List<PostComment> getReplies(Long parentCommentId) {
        List<PostComment> replies = commentRepository.findByParentCommentIdOrderByCreatedAtAsc(parentCommentId);
        log.info("Listed {} replies for comment {}", replies.size(), parentCommentId);
        return replies;
    }

    /**
     * 포스트 좋아요 토글
     *
     * @param postId 포스트 ID
     * @param userId 사용자 ID
     * @return true if liked, false if unliked
     */
    @Transactional
    public boolean togglePostLike(String postId, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + postId, 404));

        boolean liked = postLikeRepository.existsByPostIdAndUserId(postId, userId);

        if (liked) {
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            log.info("Post unliked: {} by user {}", postId, userId);
            return false;
        } else {
            PostLike like = PostLike.builder()
                    .postId(postId)
                    .userId(userId)
                    .createdAt(Instant.now())
                    .build();
            postLikeRepository.save(like);
            log.info("Post liked: {} by user {}", postId, userId);
            return true;
        }
    }

    /**
     * 댓글 좋아요 토글
     *
     * @param commentId 댓글 ID
     * @param userId 사용자 ID
     * @return true if liked, false if unliked
     */
    @Transactional
    public boolean toggleCommentLike(Long commentId, String userId) {
        PostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("COMMENT_NOT_FOUND", "Comment not found: " + commentId, 404));

        boolean liked = postLikeRepository.existsByCommentIdAndUserId(commentId, userId);

        if (liked) {
            postLikeRepository.deleteByCommentIdAndUserId(commentId, userId);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            commentRepository.save(comment);
            log.info("Comment unliked: {} by user {}", commentId, userId);
            return false;
        } else {
            PostLike like = PostLike.builder()
                    .commentId(commentId)
                    .userId(userId)
                    .createdAt(Instant.now())
                    .build();
            postLikeRepository.save(like);
            comment.setLikeCount(comment.getLikeCount() + 1);
            commentRepository.save(comment);
            log.info("Comment liked: {} by user {}", commentId, userId);
            return true;
        }
    }

    /**
     * 포스트 좋아요 수 조회
     *
     * @param postId 포스트 ID
     * @return 좋아요 수
     */
    public long getPostLikeCount(String postId) {
        return postLikeRepository.countByPostId(postId);
    }

    /**
     * 댓글 좋아요 수 조회
     *
     * @param commentId 댓글 ID
     * @return 좋아요 수
     */
    public long getCommentLikeCount(Long commentId) {
        return postLikeRepository.countByCommentId(commentId);
    }
}
