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
}
