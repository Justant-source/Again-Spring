package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
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
     * Count active marketing jobs for a post with a specific platform (legacy, no time filter).
     * Kept for backward compatibility; prefer {@link #countActivePlatformJobs(String, String, Instant)}.
     *
     * Active statuses: REQUESTED, QUEUED, RUNNING, SLA_BREACHED, WAITING_EXTERNAL,
     * PUBLISHING, STALE. Delayed remote rendering stays active until ASM gives a real terminal result.
     *
     * {@code platform} is the bare target id (e.g. {@code x_thread}); the query wraps it
     * as a JSON string literal for {@code JSON_CONTAINS}.
     *
     * Returns a count rather than a boolean — MariaDB's {@code COUNT(*) > 0} comes back
     * as an integral JDBC type, which Hibernate's native-query scalar extraction cannot
     * coerce into a {@code boolean} return type (throws ClassCastException at runtime;
     * not caught by mocked-repository unit tests). Callers compare {@code > 0} themselves.
     *
     * @deprecated Use {@link #countActivePlatformJobs(String, String, Instant)} to filter zombie jobs.
     */
    @Deprecated
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) FROM marketing_job
        WHERE post_id = :postId
        AND status IN ('REQUESTED', 'QUEUED', 'RUNNING', 'SLA_BREACHED', 'WAITING_EXTERNAL', 'PUBLISHING', 'STALE')
        AND JSON_CONTAINS(targets, JSON_QUOTE(:platform)) = TRUE
        """)
    long countActivePlatformJobs(String postId, String platform);

    /**
     * Count active marketing jobs for a post with a specific platform, updated within a recency window.
     * Active statuses: REQUESTED, QUEUED, RUNNING, SLA_BREACHED, WAITING_EXTERNAL,
     * PUBLISHING, STALE. Delayed remote rendering stays active until ASM gives a real terminal result.
     *
     * {@code recencyCutoff}: Jobs whose {@code updated_at} is older than this instant are excluded.
     * This prevents zombie jobs (e.g., SLA_BREACHED for 90+ minutes) from permanently blocking
     * new job creation for the same post+platform. Intended callers pass
     * {@code Instant.now().minus(marketingConfig.activeJobRecencyMinutes, ChronoUnit.MINUTES)}.
     *
     * {@code platform} is the bare target id (e.g. {@code x_thread}); the query wraps it
     * as a JSON string literal for {@code JSON_CONTAINS}.
     *
     * Returns a count rather than a boolean. Callers compare {@code > 0} themselves.
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) FROM marketing_job
        WHERE post_id = :postId
        AND status IN ('REQUESTED', 'QUEUED', 'RUNNING', 'SLA_BREACHED', 'WAITING_EXTERNAL', 'PUBLISHING', 'STALE')
        AND JSON_CONTAINS(targets, JSON_QUOTE(:platform)) = TRUE
        AND updated_at > :recencyCutoff
        """)
    long countActivePlatformJobs(
        @Param("postId") String postId,
        @Param("platform") String platform,
        @Param("recencyCutoff") Instant recencyCutoff);

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
     * <p>S4 distribution C: feed may coexist with Shorts; feed ⊥ Reels is enforced
     * at target-build time ({@code resolveTargets}), not by excluding video posts here.
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
        LIMIT :limit
        """)
    List<String> findPostsEligibleForInstagramFeedPublish(Instant since, int limit);

    /**
     * Legacy ranking helper: posts past 24h with no Reels/Shorts job yet, weighted score.
     * S4 commit path uses holdings instead; kept for diagnostics / fallback.
     * Text platforms (x_thread etc.) may already exist — VIDEO stories can accompany text.
     */
    @Query(nativeQuery = true, value = """
        SELECT p.id FROM posts p
        WHERE p.created_at >= :since
        AND p.created_at <= DATE_SUB(NOW(), INTERVAL 24 HOUR)
        AND p.deleted_at IS NULL
        AND NOT EXISTS (
            SELECT 1 FROM marketing_holding mh
            WHERE mh.post_id = p.id
            AND mh.status IN ('COMMITTED', 'DROPPED')
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
        ORDER BY (
            :wViews * COALESCE(p.view_count, 0)
            + :wComments * (
                SELECT COUNT(*) FROM post_comments c
                WHERE c.post_id = p.id
                AND c.parent_comment_id IS NULL
                AND c.deleted_at IS NULL
            )
            + :wVotes * (
                SELECT COUNT(*) FROM votes v
                WHERE v.post_id = p.id
            )
        ) DESC,
        p.created_at DESC
        LIMIT :limit
        """)
    List<String> findPostsEligibleForVideoMarketing(
        @Param("since") Instant since,
        @Param("limit") int limit,
        @Param("wViews") double wViews,
        @Param("wComments") double wComments,
        @Param("wVotes") double wVotes);

    /**
     * Legacy ranking helper: posts past 24h not yet COMMITTED/DROPPED and without
     * a prior text-slot job, for text-only auto slots.
     */
    @Query(nativeQuery = true, value = """
        SELECT p.id FROM posts p
        WHERE p.created_at >= :since
        AND p.created_at <= DATE_SUB(NOW(), INTERVAL 24 HOUR)
        AND p.deleted_at IS NULL
        AND NOT EXISTS (
            SELECT 1 FROM marketing_holding mh
            WHERE mh.post_id = p.id
            AND mh.status IN ('COMMITTED', 'DROPPED')
        )
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND (
                JSON_CONTAINS(mj.targets, '"x_thread"')
                OR JSON_CONTAINS(mj.targets, '"instagram_reels"')
                OR JSON_CONTAINS(mj.targets, '"youtube_shorts"')
            )
        )
        ORDER BY (
            :wViews * COALESCE(p.view_count, 0)
            + :wComments * (
                SELECT COUNT(*) FROM post_comments c
                WHERE c.post_id = p.id
                AND c.parent_comment_id IS NULL
                AND c.deleted_at IS NULL
            )
            + :wVotes * (
                SELECT COUNT(*) FROM votes v
                WHERE v.post_id = p.id
            )
        ) DESC,
        p.created_at DESC
        LIMIT :limit
        """)
    List<String> findPostsEligibleForTextMarketing(
        @Param("since") Instant since,
        @Param("limit") int limit,
        @Param("wViews") double wViews,
        @Param("wComments") double wComments,
        @Param("wVotes") double wVotes);

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
     * Distinct posts that received any marketing job since {@code since}.
     * S4: one COMMITTED story = one shared-pool slot (multi-platform jobs do not add extra).
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(DISTINCT mj.post_id) FROM marketing_job mj
        WHERE mj.created_at >= :since
        """)
    long countDistinctMarketedPostsSince(Instant since);

    /**
     * Distinct posts that got a text-path job ({@code x_thread}) since {@code since}
     * and did <em>not</em> also get a video job the same day — used for textsToday display.
     * Prefer {@link #countDistinctMarketedPostsSince} for remaining-pool math (S4).
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(DISTINCT mj.post_id) FROM marketing_job mj
        WHERE mj.created_at >= :since
        AND JSON_CONTAINS(mj.targets, '"x_thread"') = TRUE
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job v
            WHERE v.post_id = mj.post_id
            AND v.created_at >= :since
            AND (
                JSON_CONTAINS(v.targets, '"instagram_reels"') = TRUE
                OR JSON_CONTAINS(v.targets, '"youtube_shorts"') = TRUE
            )
        )
        """)
    long countTextSlotsCreatedSince(Instant since);

    List<MarketingJob> findByPostIdIn(Collection<String> postIds);

    /**
     * Jobs that reached a publish-attempt terminal status since {@code since} — used to
     * derive true per-platform PUBLISHED counts for the daily quota (see
     * MarketingQuotaService). Quota must reflect actual publish success, not
     * commit/creation: a READY job never clicked, or a PARTIAL job that failed on one
     * platform, must not permanently consume that platform's slot. Caller inspects the
     * {@code publications} JSON per-platform since a PARTIAL job may have published on
     * one target and failed on another.
     */
    @Query(nativeQuery = true, value = """
        SELECT * FROM marketing_job
        WHERE updated_at >= :since
        AND status IN ('PUBLISHED', 'PARTIAL')
        AND publications IS NOT NULL
        """)
    List<MarketingJob> findPublishAttemptsSince(@Param("since") Instant since);

    /**
     * Find jobs with expired scheduled publish time.
     * Used for reschedule detection in polling scheduler.
     *
     * <p>READY is excluded: auto-publish jobs due at {@code scheduled_publish_at} are
     * triggered by {@link com.againspring.marketing.MarketingPollingScheduler}, not carried over.
     * QUEUED/RUNNING/SLA_BREACHED/WAITING_EXTERNAL/STALE past the grace window are carried to the next-day slot
     * (content was not ready in time).
     *
     * <p>{@code auto_publish=1} only — preview/manual jobs must not keep rolling the slot
     * or spamming carry-over Telegram alerts.
     *
     * Conditions:
     * - auto_publish = true
     * - scheduled_publish_at is not null
     * - scheduled_publish_at < now - 5 minutes (tolerance)
     * - status is one of: QUEUED, RUNNING, SLA_BREACHED, WAITING_EXTERNAL, STALE (not yet READY/terminal)
     */
    @Query(nativeQuery = true, value = """
        SELECT * FROM marketing_job
        WHERE auto_publish = 1
        AND scheduled_publish_at IS NOT NULL
        AND scheduled_publish_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)
        AND status IN ('QUEUED', 'RUNNING', 'SLA_BREACHED', 'WAITING_EXTERNAL', 'STALE')
        """)
    List<MarketingJob> findExpiredScheduledJobs();

    /**
     * READY auto-publish jobs. Publish is READY-driven; {@code now} is unused (kept for call-site stability).
     */
    @Query("""
        SELECT mj FROM MarketingJob mj
        WHERE mj.status = 'READY'
        AND mj.autoPublish = true
        """)
    List<MarketingJob> findDueAutoPublishJobs(@Param("now") Instant now);

    /**
     * Find jobs with scheduled times in the given range (±5 minutes around target time).
     * Used for collision detection when rescheduling.
     *
     * Excludes the given job ID (self) and terminal statuses.
     * {@code excludeJobId} can be null to skip self-exclusion.
     *
     * @param targetTime the center time
     * @param excludeJobId job ID to exclude (nullable)
     * @return jobs scheduled within ±5 minutes of targetTime, excluding self and terminal statuses
     */
    @Query(nativeQuery = true, value = """
        SELECT * FROM marketing_job
        WHERE scheduled_publish_at IS NOT NULL
        AND scheduled_publish_at >= DATE_SUB(:targetTime, INTERVAL 5 MINUTE)
        AND scheduled_publish_at <= DATE_ADD(:targetTime, INTERVAL 5 MINUTE)
        AND status NOT IN ('PUBLISHED', 'FAILED', 'PARTIAL')
        AND (:excludeJobId IS NULL OR id != :excludeJobId)
        """)
    List<MarketingJob> findJobsByScheduledTimeRange(
        @Param("targetTime") Instant targetTime,
        @Param("excludeJobId") Long excludeJobId);

    /**
     * Find all marketing jobs with a scheduled publish time set.
     * Used in carry-over logic to identify jobs that have been scheduled for deferred publishing.
     *
     * @return List of marketing jobs with non-null scheduledPublishAt
     */
    List<MarketingJob> findByScheduledPublishAtNotNull();

    /**
     * Find all marketing jobs scheduled to publish within a specific time range.
     * Used to detect scheduling collisions and batch-publish multiple jobs within a window.
     *
     * @param start the start time (inclusive)
     * @param end the end time (inclusive)
     * @return List of marketing jobs scheduled between start and end times (inclusive)
     */
    List<MarketingJob> findByScheduledPublishAtBetween(Instant start, Instant end);

    /**
     * Find READY auto-publish jobs that have been sitting READY for 30+ minutes
     * without being published (stuck trigger / ASM publish).
     *
     * @param thirtyMinutesAgo the cutoff time (current instant - 30 minutes)
     * @return List of READY auto-publish jobs whose {@code updatedAt} is older than the cutoff
     */
    @Query("""
        SELECT mj FROM MarketingJob mj
        WHERE mj.status = 'READY'
        AND mj.autoPublish = true
        AND mj.updatedAt IS NOT NULL
        AND mj.updatedAt < :thirtyMinutesAgo
        """)
    List<MarketingJob> findReadyJobsPastScheduleBy30Minutes(@Param("thirtyMinutesAgo") Instant thirtyMinutesAgo);

    /**
     * Count marketing jobs scheduled for a specific time that are not yet in terminal status.
     * Used for carry-over collision detection to ensure multiple jobs don't publish
     * at the exact same instant, raising a scheduling conflict for manual resolution.
     *
     * <p>Terminal statuses (PUBLISHED, FAILED, PARTIAL) are intentionally excluded to detect
     * only <em>active</em> jobs that may still conflict.
     *
     * @param time the scheduled publish time to check
     * @param excludeStatuses terminal statuses to exclude (typically PUBLISHED, FAILED, PARTIAL)
     * @return count of non-terminal jobs scheduled for the given time
     */
    long countByScheduledPublishAtAndStatusNotIn(Instant time, List<String> excludeStatuses);

    /**
     * Find all unpublished jobs whose scheduled publish time has expired.
     * Used in carry-over logic to detect stale scheduled jobs that never published,
     * allowing the scheduler to decide whether to reschedule, discard, or escalate.
     *
     * @param cutoffTime jobs with scheduledPublishAt before (strictly less than) this time
     *                   are considered expired
     * @return List of expired unpublished marketing jobs
     *         (status NOT IN PUBLISHED, FAILED, PARTIAL)
     */
    @Query("SELECT mj FROM MarketingJob mj " +
           "WHERE mj.scheduledPublishAt < :cutoffTime " +
           "AND mj.status NOT IN ('PUBLISHED', 'FAILED', 'PARTIAL')")
    List<MarketingJob> findExpiredUnpublishedJobs(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Find all marketing jobs created within a specific instant range.
     * Used for daily reporting to aggregate job statistics by channel.
     *
     * @param startInclusive the start time (inclusive)
     * @param endExclusive the end time (exclusive)
     * @return List of marketing jobs created within the time range
     */
    List<MarketingJob> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        @Param("startInclusive") Instant startInclusive,
        @Param("endExclusive") Instant endExclusive);

    /**
     * Find all redrive child jobs for a given source job ID.
     * Used for idempotency checks in redrive logic: if a source job already has a
     * non-terminal child, reuse it instead of creating a new one.
     *
     * @param retryOfJobId the source job ID
     * @return List of child jobs (may be empty)
     */
    List<MarketingJob> findByRetryOfJobId(Long retryOfJobId);
}
