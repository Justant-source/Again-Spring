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
     * Formula: round((log1p(views) * perLogView + childReplies * perReply) * jitter * popularity)
     * Clamped to [0, cap].
     *
     * <p><b>Uses {@code log1p(views)}, not raw views.</b> Linear {@code views * 0.025} saturated
     * every comment at the cap once views crossed ~480 (prod 2026-08-01: 115/167 recent
     * top-level comments stuck at exactly 12). Log keeps typical bands in ~2–9 across
     * hundreds→thousands of views; {@link #popularity} spreads comments so they do not
     * all land on the same count.
     *
     * @param views post view count (context for the comment)
     * @param childReplies count of direct replies to this comment
     * @param commentId ID of the comment
     * @param perLogView likes per {@code log1p(views)} (e.g., 0.75)
     * @param perReply likes per direct reply (e.g., 1.0)
     * @param cap maximum like count (e.g., 12)
     * @return target like count in [0, cap]
     */
    public static int commentLikeTarget(long views, int childReplies, long commentId,
                                        double perLogView, double perReply, int cap) {
        String id = String.valueOf(commentId);
        double raw = (Math.log1p(views) * perLogView + childReplies * perReply)
                * jitter(id) * popularity(id);
        int target = Math.toIntExact(Math.round(raw));
        return Math.max(0, Math.min(target, cap));
    }

    /**
     * Target like count for a reply (direct child of a comment).
     * Formula: round(log1p(views) * perLogView * jitter * popularity)
     * Clamped to [0, cap].
     *
     * @param views post view count (context for the reply)
     * @param commentId ID of the reply itself (used for jitter/popularity)
     * @param perLogView likes per {@code log1p(views)} (e.g., 0.40)
     * @param cap maximum like count (e.g., 5)
     * @return target like count in [0, cap]
     */
    public static int replyLikeTarget(long views, long commentId,
                                      double perLogView, int cap) {
        String id = String.valueOf(commentId);
        double raw = Math.log1p(views) * perLogView * jitter(id) * popularity(id);
        int target = Math.toIntExact(Math.round(raw));
        return Math.max(0, Math.min(target, cap));
    }

    /**
     * Per-comment popularity multiplier in [0.4, 1.6), salted separately from {@link #jitter}
     * so volume jitter and "hot vs quiet comment" draws are uncorrelated.
     */
    public static double popularity(String id) {
        CRC32 crc = new CRC32();
        crc.update(("pop:" + id).getBytes());
        long hash = crc.getValue();
        return 0.4 + (hash % 120) / 100.0;
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

    /**
     * Target vote count for a post.
     * Formula: round(views * perView * jitter). Clamped to [0, cap].
     *
     * @param views post view count
     * @param postId string identifier of the post (jitter source)
     * @param perView votes per view (e.g., 0.15 — votes are anonymous, so real users vote far
     *                more readily than they comment; see docs/ai-user/thread-planning.md)
     * @param cap maximum vote count
     * @return target vote count in [0, cap]
     */
    public static int voteTarget(long views, String postId, double perView, int cap) {
        double raw = views * perView * jitter(postId);
        int target = Math.toIntExact(Math.round(raw));
        return Math.max(0, Math.min(target, cap));
    }

    /**
     * Deterministic target share of votes for option index 0 (the author's / "A" side),
     * within {@code [min, max]}.
     *
     * <p><b>Uses a separately salted hash, not {@link #jitter}.</b> If this reused
     * {@code jitter(postId)}, a post's target vote count and its target A-share would be
     * perfectly correlated (same hash, same id) — posts with a high vote target would always
     * skew toward the same side, which is not how the pre-engagement-dispatcher natural
     * distribution looked (44~80% A-share, uncorrelated with volume). Salting the input
     * ("ashare:" + postId) before hashing decorrelates the two draws while staying fully
     * deterministic per post.
     *
     * @param postId string identifier of the post
     * @param min minimum A-share (e.g., 0.44 — observed natural floor)
     * @param max maximum A-share (e.g., 0.80 — observed natural ceiling)
     * @return target A-share in [min, max]
     */
    public static double voteAShare(String postId, double min, double max) {
        CRC32 crc = new CRC32();
        crc.update(("ashare:" + postId).getBytes());
        long hash = crc.getValue();
        double fraction = (hash % 1000) / 1000.0; // [0, 1)
        return min + fraction * (max - min);
    }

    /**
     * Picks which vote option index (0 = author/"A", 1 = counterpart/"B") to cast the next
     * synthetic vote for, given the current split and a target A-share.
     *
     * <p>Greedy proportional fill: cast for A whenever doing so would still keep A's share at
     * or below target after the vote, otherwise cast for B. This only ever adds a vote for
     * whichever side is currently under-represented relative to the target — it never suggests
     * removing or flipping an existing vote, so real human votes already cast are never
     * overridden, only diluted toward the target ratio by new synthetic votes.
     *
     * @param currentA current vote count for option 0 (author/A)
     * @param currentB current vote count for option 1 (counterpart/B)
     * @param targetAShare desired share of votes for A, in [0, 1]
     * @return 0 to vote for A, 1 to vote for B
     */
    public static int chooseVoteOptionIndex(int currentA, int currentB, double targetAShare) {
        boolean fillA = currentA < targetAShare * (currentA + currentB + 1);
        return fillA ? 0 : 1;
    }
}
