package com.againspring.aiuser.orchestrator.engine;

import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.domain.enums.ActionType;

/**
 * 단일 봇 행동 계획 — ActionPlanner가 생성, ActionExecutor가 실행.
 */
public record PlannedAction(
    ActionType type,
    PostDto targetPost,       // LIKE/VOTE/COMMENT 대상 글 (POST/REPLY 시 null 가능)
    Long voteOptionId,        // VOTE 시 선택할 option id (vote_options.id BIGINT)
    Long parentCommentId,     // REPLY 시 대상 comment id
    String parentCommentExcerpt, // REPLY 시 원댓글 발췌
    String threadContext,     // REPLY 시 맥락
    String siblingComments    // REPLY 시 다른 댓글들
) {
    /** Convenience: non-reply action */
    public static PlannedAction like(PostDto post) {
        return new PlannedAction(ActionType.LIKE, post, null, null, null, null, null);
    }
    public static PlannedAction vote(PostDto post, Long optionId) {
        return new PlannedAction(ActionType.VOTE, post, optionId, null, null, null, null);
    }
    public static PlannedAction comment(PostDto post) {
        return new PlannedAction(ActionType.COMMENT, post, null, null, null, null, null);
    }
    public static PlannedAction reply(String postId, String postTitle, Long commentId, String excerpt, String ctx) {
        return reply(postId, postTitle, commentId, excerpt, ctx, null, null);
    }
    public static PlannedAction reply(String postId, String postTitle, Long commentId, String excerpt, String ctx, String postBodyExcerpt, String siblingComments) {
        PostDto stub = new PostDto();
        stub.setId(postId);
        stub.setUserTitle(postTitle);
        stub.setBodyPublished(postBodyExcerpt);
        return new PlannedAction(ActionType.REPLY, stub, null, commentId, excerpt, ctx, siblingComments);
    }
    public static PlannedAction newPost() {
        return new PlannedAction(ActionType.POST, null, null, null, null, null, null);
    }
    public static PlannedAction view(PostDto post) {
        return new PlannedAction(ActionType.VIEW, post, null, null, null, null, null);
    }
    /** 독립적 댓글 좋아요 — LLM 0콜, like_score 기반. targetPost에서 댓글 조회 후 좋아요. */
    public static PlannedAction commentLike(PostDto post) {
        return new PlannedAction(ActionType.COMMENT_LIKE, post, null, null, null, null, null);
    }
    /**
     * 타깃 댓글 지정 좋아요 — LLM 0콜, fetchReactableComments의 페이지(5)/cap(8) 제한을
     * 우회한다(엔진 스캐너 재사용, PlanEngagementDispatcher 전용). commentLike(post)와
     * 달리 like_score 확률 게이트 없이 무조건 시도한다.
     */
    public static PlannedAction commentLike(PostDto post, Long commentId) {
        return new PlannedAction(ActionType.COMMENT_LIKE, post, null, commentId, null, null, null);
    }
}
