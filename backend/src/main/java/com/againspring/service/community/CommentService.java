package com.againspring.service.community;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.community.PostLike;
import com.againspring.domain.enums.CommentStatus;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostLikeRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.safety.CrisisDetectedEvent;
import com.againspring.safety.CrisisKeywordGuard;
import com.againspring.safety.CrisisScanResult;
import com.againspring.service.notification.event.NewCommentEvent;
import com.againspring.service.notification.event.NewReplyEvent;
import com.againspring.service.ai.AiUserOutboxWriter;
import com.againspring.service.ai.SyntheticOutputGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final CrisisKeywordGuard crisisKeywordGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final AiUserOutboxWriter aiUserOutboxWriter;
    private final SyntheticOutputGuard syntheticOutputGuard;

    /** Internal idempotency replay lookup; not a public API read path. */
    @Transactional(readOnly = true)
    public PostComment findCommentById(Long commentId) {
        return commentRepository.findById(commentId).orElse(null);
    }

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
        syntheticOutputGuard.assertPublishable(authorId, body);
        if (!syntheticOutputGuard.isSynthetic(authorId)) {
            CrisisScanResult crisis = crisisKeywordGuard.scan(body);
            if (crisis.crisis()) {
                eventPublisher.publishEvent(new CrisisDetectedEvent(this, authorId, null, crisis.patterns()));
            }
        }

        // 부모 댓글 존재 확인 (nullable). UI는 2단만 지원 — 대댓글의 대댓글(depth≥2) 금지.
        PostComment parent = null;
        if (parentCommentId != null) {
            parent = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new BusinessException("COMMENT_NOT_FOUND", "Parent comment not found: " + parentCommentId, 404));

            if (!parent.getPostId().equals(postId)) {
                throw new BusinessException("COMMENT_MISMATCH", "Parent comment does not belong to this post", 400);
            }
            if (parent.getParentCommentId() != null) {
                throw new BusinessException("COMMENT_DEPTH_EXCEEDED",
                        "대댓글에는 답글을 달 수 없습니다. 최상위 댓글에만 답글이 가능합니다.", 400);
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

        // 사람/AI 여부의 판별 및 inbox 투입은 downstream worker 책임이다.
        // 여기서는 lifecycle 사실만 원 트랜잭션에 기록한다.
        aiUserOutboxWriter.commentCreated(post, saved);

        if (parent == null) {
            // 최상위 댓글: 글 작성자에게 알림 (자기 자신 제외) — refCommentId = 새 댓글 ID
            if (!authorId.equals(post.getAuthorId())) {
                eventPublisher.publishEvent(new NewCommentEvent(this, post.getAuthorId(), postId, saved.getId(), "새 댓글이 달렸어요"));
            }
        } else {
            // 대댓글: 부모 댓글 작성자에게 알림 (자기 자신 제외) — refCommentId = 부모 댓글 ID
            if (!authorId.equals(parent.getAuthorId())) {
                eventPublisher.publishEvent(new NewReplyEvent(this, parent.getAuthorId(), postId, parent.getId(), "내 댓글에 답글이 달렸어요"));
            }
        }

        return saved;
    }

    /**
     * 댓글 수정 — 본인 댓글만 가능
     *
     * @param commentId 댓글 ID
     * @param userId 요청자 ID
     * @param body 새 댓글 내용
     * @return 수정된 PostComment
     * @throws BusinessException COMMENT_NOT_FOUND, ACCESS_DENIED, CRISIS_DETECTED
     */
    @Transactional
    public PostComment updateComment(Long commentId, String userId, String body) {
        PostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("COMMENT_NOT_FOUND", "Comment not found: " + commentId, 404));

        if (userId == null || !userId.equals(comment.getAuthorId())) {
            throw new BusinessException("ACCESS_DENIED", "본인 댓글만 수정할 수 있습니다.", 403);
        }

        // 위기 감지
        syntheticOutputGuard.assertPublishable(userId, body);
        if (!syntheticOutputGuard.isSynthetic(userId)) {
            CrisisScanResult crisis = crisisKeywordGuard.scan(body);
            if (crisis.crisis()) {
                eventPublisher.publishEvent(new CrisisDetectedEvent(this, userId, null, crisis.patterns()));
            }
        }

        comment.setBody(body);
        comment.setUpdatedAt(Instant.now());
        comment.advanceContentRevision();
        PostComment saved = commentRepository.save(comment);
        Post post = postRepository.findById(saved.getPostId())
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + saved.getPostId(), 404));
        aiUserOutboxWriter.commentUpdated(post, saved);
        log.info("Comment updated: {} by user {}", commentId, userId);
        return saved;
    }

    /**
     * 댓글 삭제 — 본인 댓글만 가능. 최상위 댓글이면 대댓글까지 함께 삭제.
     *
     * @param commentId 댓글 ID
     * @param userId 요청자 ID
     * @throws BusinessException COMMENT_NOT_FOUND, ACCESS_DENIED
     */
    @Transactional
    public void deleteComment(Long commentId, String userId) {
        PostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("COMMENT_NOT_FOUND", "Comment not found: " + commentId, 404));

        if (userId == null || !userId.equals(comment.getAuthorId())) {
            throw new BusinessException("ACCESS_DENIED", "본인 댓글만 삭제할 수 있습니다.", 403);
        }

        Post post = postRepository.findById(comment.getPostId())
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + comment.getPostId(), 404));

        // 최상위 댓글이면 대댓글까지 정리 (orphan 방지)
        if (comment.getParentCommentId() == null) {
            List<PostComment> replies = commentRepository.findByParentCommentIdOrderByCreatedAtAsc(commentId);
            for (PostComment reply : replies) {
                aiUserOutboxWriter.commentLifecycleChanged(post, reply, "REPLY_DELETED", "AUTHOR_DELETED_PARENT");
                postLikeRepository.deleteByCommentId(reply.getId());
                commentRepository.delete(reply);
            }
        }

        aiUserOutboxWriter.commentLifecycleChanged(post, comment,
                comment.getParentCommentId() == null ? "COMMENT_DELETED" : "REPLY_DELETED", "AUTHOR_DELETED");
        postLikeRepository.deleteByCommentId(commentId);
        commentRepository.delete(comment);
        log.info("Comment deleted: {} by user {}", commentId, userId);
    }

    /**
     * 최상위 댓글 목록 조회 (부모 댓글 없는 댓글들, 최신순)
     *
     * @param postId 포스트 ID
     * @return 최상위 댓글 목록 (createdAt DESC)
     */
    public List<PostComment> getTopLevelComments(String postId) {
        // 공개 피드: 차단(BLOCKED)·삭제(deletedAt)된 댓글은 제외 — ACTIVE & deletedAt IS NULL만, 최신순
        List<PostComment> comments = commentRepository
                .findByPostIdAndParentCommentIdIsNullAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                        postId, CommentStatus.ACTIVE);
        log.info("Listed {} top-level comments for post {}", comments.size(), postId);
        return comments;
    }

    /**
     * 특정 댓글의 대댓글 조회 (최신순)
     *
     * @param parentCommentId 부모 댓글 ID
     * @return 대댓글 목록 (createdAt DESC)
     */
    public List<PostComment> getReplies(Long parentCommentId) {
        // 공개 피드: 차단(BLOCKED)·삭제(deletedAt)된 답글은 제외 — ACTIVE & deletedAt IS NULL만, 최신순
        List<PostComment> replies = commentRepository
                .findByParentCommentIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                        parentCommentId, CommentStatus.ACTIVE);
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

    /**
     * 포스트 full delete 시 댓글·댓글좋아요·글좋아요 hard delete.
     * (소프트 tombstone 경로에서는 호출하지 않음)
     */
    @Transactional
    public void hardDeleteAllForPost(String postId) {
        List<PostComment> comments = commentRepository.findByPostId(postId);
        for (PostComment comment : comments) {
            postLikeRepository.deleteByCommentId(comment.getId());
        }
        postLikeRepository.deleteByPostId(postId);
        commentRepository.deleteByPostId(postId);
        log.info("Hard-deleted {} comments (and likes) for post {}", comments.size(), postId);
    }
}
