package com.againspring.aiuser.orchestrator.engine;

/**
 * 대댓글 대상: synthetic 글에 달린 댓글 중 봇이 반응해야 할 대상.
 */
public record ReplyTarget(
    String postId,
    String postTitle,
    Long commentId,           // post_comments.id (BIGINT)
    String commentExcerpt,    // max 200 chars
    String threadContext      // brief context
) {}
