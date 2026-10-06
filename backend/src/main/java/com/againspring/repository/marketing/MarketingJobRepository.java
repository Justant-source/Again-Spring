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
     * READY auto-publish jobs. Publish is READY-driven; {@code now} is unused (kept for call-site stability).
     */
    @Query("""
        SELECT mj FROM MarketingJob mj
        WHERE mj.status = 'READY'
        AND mj.autoPublish = true
        """)
    List<MarketingJob> findDueAutoPublishJobs(@Param("now") Instant now);

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
