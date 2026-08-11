package com.againspring.marketing;

import java.util.Locale;
import java.util.Objects;

/**
 * Phase 2 per-platform popularity score (plan §3).
 *
 * <pre>
 * score = wHook*hook + wVoteSkew*vote_skew + wComments*comments
 *       + wVotes*votes + wViews*views + wHasPartner*has_partner
 * </pre>
 */
public final class MarketingPopularityScorer {

    public static final String PLATFORM_X_THREAD = "x_thread";
    public static final String PLATFORM_INSTAGRAM_FEED = "instagram_feed";
    public static final String PLATFORM_INSTAGRAM_REELS = "instagram_reels";
    public static final String PLATFORM_YOUTUBE_SHORTS = "youtube_shorts";

    /** Active Phase 2 ranking platforms (order used by admin / selection). */
    public static final java.util.List<String> RANKED_PLATFORMS = java.util.List.of(
        PLATFORM_X_THREAD,
        PLATFORM_INSTAGRAM_FEED,
        PLATFORM_INSTAGRAM_REELS,
        PLATFORM_YOUTUBE_SHORTS
    );

    private MarketingPopularityScorer() {}

    /**
     * Raw engagement signals for one holding candidate.
     *
     * @param views       post view count
     * @param comments    top-level comment count
     * @param votes       total vote count
     * @param voteSkew    |author% − 50| / 50 (0 when no votes)
     * @param hasPartner  1.0 if paired, else 0.0
     * @param hook        hook strength 0–1
     */
    public record Signals(
        double views,
        double comments,
        double votes,
        double voteSkew,
        double hasPartner,
        double hook
    ) {
        public Signals {
            views = Math.max(0, views);
            comments = Math.max(0, comments);
            votes = Math.max(0, votes);
            voteSkew = clamp01(voteSkew);
            hasPartner = hasPartner >= 0.5 ? 1.0 : 0.0;
            hook = clamp01(hook);
        }
    }

    /** Per-platform weight vector (plan §3 defaults live in {@link MarketingScoreWeightService}). */
    public record PlatformWeights(
        double hook,
        double voteSkew,
        double comments,
        double votes,
        double views,
        double hasPartner
    ) {}

    public static double score(Signals signals, PlatformWeights weights) {
        Objects.requireNonNull(signals, "signals");
        Objects.requireNonNull(weights, "weights");
        return weights.hook() * signals.hook()
            + weights.voteSkew() * signals.voteSkew()
            + weights.comments() * signals.comments()
            + weights.votes() * signals.votes()
            + weights.views() * signals.views()
            + weights.hasPartner() * signals.hasPartner();
    }

    /**
     * {@code vote_skew = |authorPct − 50| / 50}. Returns 0 when there are no votes.
     */
    public static double voteSkew(long authorVotes, long totalVotes) {
        if (totalVotes <= 0) {
            return 0.0;
        }
        double authorPct = (authorVotes * 100.0) / totalVotes;
        return Math.abs(authorPct - 50.0) / 50.0;
    }

    /**
     * Hook strength 0–1. No dedicated strength field → 0.5 baseline;
     * non-blank promo/hook text nudges lightly by length (cap 1.0).
     */
    public static double hookStrength(String promoTitleOrHook) {
        if (promoTitleOrHook == null || promoTitleOrHook.isBlank()) {
            return 0.5;
        }
        int len = promoTitleOrHook.trim().length();
        // 0.5 at empty path; ~0.5–1.0 as length grows (40 chars → 1.0)
        return clamp01(0.5 + Math.min(len, 40) / 80.0);
    }

    public static String normalizePlatform(String platform) {
        if (platform == null) {
            return "";
        }
        return platform.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isRankedPlatform(String platform) {
        return RANKED_PLATFORMS.contains(normalizePlatform(platform));
    }

    /**
     * IG feed ⊥ Reels: when the same story would take both, keep the higher score;
     * tie → Reels.
     *
     * @return {@link #PLATFORM_INSTAGRAM_REELS} or {@link #PLATFORM_INSTAGRAM_FEED}
     */
    public static String resolveIgExclusiveWinner(double scoreFeed, double scoreReels) {
        if (scoreFeed > scoreReels) {
            return PLATFORM_INSTAGRAM_FEED;
        }
        return PLATFORM_INSTAGRAM_REELS;
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0.0;
        }
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }
}
