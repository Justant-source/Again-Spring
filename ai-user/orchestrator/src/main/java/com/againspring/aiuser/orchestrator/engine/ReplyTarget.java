package com.againspring.aiuser.orchestrator.engine;

/**
 * 대댓글 대상: synthetic 글에 달린 댓글 중 봇이 반응해야 할 대상.
 */
public record ReplyTarget(
    String postId,
    String postTitle,
    Long commentId,           // post_comments.id (BIGINT)
    String commentExcerpt,    // max 200 chars
    String threadContext,     // brief context
    String postBodyExcerpt,   // post body preview (max 300 chars)
    String siblingComments,   // other top-level comments on same post
    String commentAuthorId    // 댓글 작성자 user id — 자기 댓글에 자답 방지용
) {}
