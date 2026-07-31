package com.againspring.aiuser.orchestrator.service.engagement;

import java.util.zip.CRC32;

/**
 * Formula-based engagement target calculation (views, likes) independent of LLM.
 *
 * <p>Formulas come from docs/ai-user/thread-planning.md §LLM 없는 engagement.
 * All calculations use deterministic jitter derived from CRC32 of the target ID,
 * ensuring the same target always produces the same values across repeated calls
 * (no {@code Math.random()}, no time-based variance).
 *
 * <p>Style and jitter pattern match {@code ViewDispatcher.java} CRC32-mod approach.
 */
public final class EngagementTargetCalculator {

    private EngagementTargetCalculator() { }

    /**
     * Deterministic pseudo-jitter in range [0.8, 1.2) derived from CRC32 hash of the id.
     * Same id always returns the same jitter value across repeated calls.
     *
     * @param id String identifier (post id, comment id, etc.)
     * @return jitter factor in [0.8, 1.2)
     */
    public static double jitter(String id) {
        CRC32 crc = new CRC32();
        crc.update(id.getBytes());
        long hash = crc.getValue();
        // 0.8 + (hash % 40) / 100.0 gives [0.8, 1.20)
        return 0.8 + (hash % 40) / 100.0;
    }

    /**
     * Target like count for a post.
     * Formula: round((views * perView + commentsAndReplies * perComment) * jitter)
     * No cap (posts are uncapped).
     * Never negative.
     *
     * @param views post view count
     * @param commentsAndReplies total top-level comments + all replies
     * @param postId string identifier of the post
     * @param perView likes per view (e.g., 0.02 per thread-planning.md)
     * @param perComment likes per comment/reply (e.g., 0.6 per thread-planning.md)
     * @return target like count, >= 0
     */
    public static int postLikeTarget(long views, int commentsAndReplies, String postId,
                                     double perView, double perComment) {
        double raw = (views * perView + commentsAndReplies * perComment) * jitter(postId);
        return Math.max(0, Math.toIntExact(Math.round(raw)));
    }

    /**
     * Target like count for a comment (top-level).
     * Formula: round((views * perView + childReplies * perReply) * jitter)
     * Clamped to [0, cap].
     *
     * @param views post view count (context for the comment)
     * @param childReplies count of direct replies to this comment
     * @param commentId ID of the comment
     * @param perView likes per post view (e.g., 0.002 per thread-planning.md)
     * @param perReply likes per direct reply (e.g., 1.0 per thread-planning.md)
     * @param cap maximum like count (e.g., 12 per thread-planning.md)
     * @return target like count in [0, cap]
     */
    public static int commentLikeTarget(long views, int childReplies, long commentId,
                                        double perView, double perReply, int cap) {
        double raw = (views * perView + childReplies * perReply) * jitter(String.valueOf(commentId));
        int target = Math.toIntExact(Math.round(raw));
        return Math.max(0, Math.min(target, cap));
    }

    /**
     * Target like count for a reply (direct child of a comment).
     * Formula: round(views * perView * jitter)
     * Clamped to [0, cap].
     *
     * @param views post view count (context for the reply)
     * @param commentId ID of the parent comment (used for jitter derivation)
     * @param perView likes per post view (e.g., 0.001 per thread-planning.md)
     * @param cap maximum like count (e.g., 5 per thread-planning.md)
     * @return target like count in [0, cap]
     */
    public static int replyLikeTarget(long views, long commentId,
                                      double perView, int cap) {
        double raw = views * perView * jitter(String.valueOf(commentId));
        int target = Math.toIntExact(Math.round(raw));
        return Math.max(0, Math.min(target, cap));
    }

    /**
     * How many likes are still needed to reach the target.
     * Helper: {@code Math.max(0, target - current)}.
     * Never negative.
     *
     * @param target desired like count
     * @param current current like count
     * @return likes still owed, >= 0
     */
    public static int deficit(int target, int current) {
        return Math.max(0, target - current);
    }
}
