package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for MarketingJob
 */
@Repository
public interface MarketingJobRepository extends JpaRepository<MarketingJob, Long> {

    List<MarketingJob> findByStatusIn(List<String> statuses);

    Optional<MarketingJob> findByRemoteJobId(String remoteJobId);

    Optional<MarketingJob> findFirstByPostIdAndStatusNotIn(String postId, List<String> statuses);

    Optional<MarketingJob> findFirstByPostIdAndStatusIn(String postId, List<String> statuses);

    /**
     * Count active marketing jobs for a post with a specific platform.
     * Active statuses: REQUESTED, QUEUED, RUNNING, PUBLISHING, STALE
     *
     * {@code platform} is the bare target id (e.g. {@code x_thread}); the query wraps it
     * as a JSON string literal for {@code JSON_CONTAINS}.
     *
     * Returns a count rather than a boolean — MariaDB's {@code COUNT(*) > 0} comes back
     * as an integral JDBC type, which Hibernate's native-query scalar extraction cannot
     * coerce into a {@code boolean} return type (throws ClassCastException at runtime;
     * not caught by mocked-repository unit tests). Callers compare {@code > 0} themselves.
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) FROM marketing_job
        WHERE post_id = :postId
        AND status IN ('REQUESTED', 'QUEUED', 'RUNNING', 'PUBLISHING', 'STALE')
        AND JSON_CONTAINS(targets, JSON_QUOTE(:platform)) = TRUE
        """)
    long countActivePlatformJobs(String postId, String platform);

    /**
     * Count marketing jobs for a post with a specific platform, regardless of status.
     * Used for one-time-per-post trigger idempotency (e.g. youtube_shorts auto-enqueued
     * once after x_thread/instagram_feed first PUBLISHED — see MarketingJobService).
     * Unlike {@link #countActivePlatformJobs}, this deliberately includes terminal
     * statuses so a completed/failed job still blocks re-creation.
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) FROM marketing_job
        WHERE post_id = :postId
        AND JSON_CONTAINS(targets, JSON_QUOTE(:platform)) = TRUE
        """)
    long countAnyPlatformJobs(String postId, String platform);

    /**
     * Find posts eligible for automatic X thread publishing:
     * - created_at >= :since (operator cutoff — only posts created after this instant)
     * - created_at + 24 hours <= NOW()
     * - no soft-delete
     * - no x_thread job has ever been attempted for this post (any status)
     *
     * Comment-count gate removed 2026-08-02 — product rule is unconditional 24h after
     * publish for both human and PLAN posts (X + Instagram).
     *
     * {@code since} cutoff added 2026-08-02 after a pre-existing backlog flooded live
     * X/IG once the 24h gate alone was enabled. Fail-closed callers pass a required
     * Instant; without it the scheduler skips.
     *
     * The NOT EXISTS check intentionally ignores status entirely — it is NOT limited to
     * "active" statuses. An X thread is a one-time-per-post event: once a job exists
     * (REQUESTED..STALE, or terminal PUBLISHED/FAILED/PARTIAL/READY — see
     * MarketingJobService's terminal-status comment), the post must never be picked up
     * again. An earlier version filtered by active status only, which meant a post whose
     * job reached PUBLISHED would silently become "eligible" again on the very next poll —
     * every successful thread would have been re-published forever. Caught before this
     * ever ran in prod with the trigger enabled (2026-08-01).
     */
    @Query(nativeQuery = true, value = """
        SELECT p.id FROM posts p
        WHERE p.created_at >= :since
        AND p.created_at <= DATE_SUB(NOW(), INTERVAL 24 HOUR)
        AND p.deleted_at IS NULL
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND JSON_CONTAINS(mj.targets, '"x_thread"')
        )
        LIMIT :limit
        """)
    List<String> findPostsEligibleForXThreadPublish(Instant since, int limit);

    /**
     * Same 24h one-shot gate as {@link #findPostsEligibleForXThreadPublish}, for
     * {@code instagram_feed}. Separate query because ASM requires each alone target
     * as its own job.
     *
     * <p>Excludes posts that already have {@code instagram_reels} or {@code youtube_shorts}
     * (any status). Video (Reels+Shorts) and feed news-cards are mutually exclusive.
     */
    @Query(nativeQuery = true, value = """
        SELECT p.id FROM posts p
        WHERE p.created_at >= :since
        AND p.created_at <= DATE_SUB(NOW(), INTERVAL 24 HOUR)
        AND p.deleted_at IS NULL
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND JSON_CONTAINS(mj.targets, '"instagram_feed"')
        )
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND JSON_CONTAINS(mj.targets, '"instagram_reels"')
        )
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND JSON_CONTAINS(mj.targets, '"youtube_shorts"')
        )
        LIMIT :limit
        """)
    List<String> findPostsEligibleForInstagramFeedPublish(Instant since, int limit);

    /**
     * Posts past 24h with no IG feed / Reels / Shorts / X-thread job yet, ranked by popularity:
     * view_count DESC → top-level comments → votes → created_at DESC.
     * Used to pick the daily video cohort (Reels + YouTube Shorts) under the shared quota pool.
     * Excludes {@code x_thread} so text-path posts are not also selected for video.
     */
    @Query(nativeQuery = true, value = """
        SELECT p.id FROM posts p
        WHERE p.created_at >= :since
        AND p.created_at <= DATE_SUB(NOW(), INTERVAL 24 HOUR)
        AND p.deleted_at IS NULL
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND JSON_CONTAINS(mj.targets, '"x_thread"')
        )
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND JSON_CONTAINS(mj.targets, '"instagram_feed"')
        )
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND JSON_CONTAINS(mj.targets, '"instagram_reels"')
        )
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND JSON_CONTAINS(mj.targets, '"youtube_shorts"')
        )
        ORDER BY
            COALESCE(p.view_count, 0) DESC,
            (
                SELECT COUNT(*) FROM post_comments c
                WHERE c.post_id = p.id
                AND c.parent_comment_id IS NULL
                AND c.deleted_at IS NULL
            ) DESC,
            (
                SELECT COUNT(*) FROM votes v
                WHERE v.post_id = p.id
            ) DESC,
            p.created_at DESC
        LIMIT :limit
        """)
    List<String> findPostsEligibleForVideoMarketing(Instant since, int limit);

    /**
     * Posts past 24h with no X / feed / Reels / Shorts job yet, ranked by the same popularity
     * score as video. Used for daily text slots (x_thread + instagram_feed).
     */
    @Query(nativeQuery = true, value = """
        SELECT p.id FROM posts p
        WHERE p.created_at >= :since
        AND p.created_at <= DATE_SUB(NOW(), INTERVAL 24 HOUR)
        AND p.deleted_at IS NULL
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND JSON_CONTAINS(mj.targets, '"x_thread"')
        )
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND JSON_CONTAINS(mj.targets, '"instagram_feed"')
        )
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND JSON_CONTAINS(mj.targets, '"instagram_reels"')
        )
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND JSON_CONTAINS(mj.targets, '"youtube_shorts"')
        )
        ORDER BY
            COALESCE(p.view_count, 0) DESC,
            (
                SELECT COUNT(*) FROM post_comments c
                WHERE c.post_id = p.id
                AND c.parent_comment_id IS NULL
                AND c.deleted_at IS NULL
            ) DESC,
            (
                SELECT COUNT(*) FROM votes v
                WHERE v.post_id = p.id
            ) DESC,
            p.created_at DESC
        LIMIT :limit
        """)
    List<String> findPostsEligibleForTextMarketing(Instant since, int limit);

    /**
     * Count distinct posts that already received a Reels and/or Shorts marketing job
     * since {@code since} (caller passes start-of-day KST as Instant).
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(DISTINCT mj.post_id) FROM marketing_job mj
        WHERE mj.created_at >= :since
        AND (
            JSON_CONTAINS(mj.targets, '"instagram_reels"') = TRUE
            OR JSON_CONTAINS(mj.targets, '"youtube_shorts"') = TRUE
        )
        """)
    long countVideoJobsCreatedSince(Instant since);

    /**
     * Count distinct posts that received an {@code x_thread} job since {@code since}.
     * One text marketing slot = one x_thread job (instagram_feed is created alongside).
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(DISTINCT mj.post_id) FROM marketing_job mj
        WHERE mj.created_at >= :since
        AND JSON_CONTAINS(mj.targets, '"x_thread"') = TRUE
        """)
    long countTextSlotsCreatedSince(Instant since);
}
