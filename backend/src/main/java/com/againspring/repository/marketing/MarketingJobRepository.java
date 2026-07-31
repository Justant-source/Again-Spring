package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
     * Check if a post has an active marketing job with a specific platform
     * Active statuses: REQUESTED, QUEUED, RUNNING, PUBLISHING, STALE
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) > 0 FROM marketing_job
        WHERE post_id = :postId
        AND status IN ('REQUESTED', 'QUEUED', 'RUNNING', 'PUBLISHING', 'STALE')
        AND JSON_CONTAINS(targets, :platform) = TRUE
        """)
    boolean hasActivePlatformJob(String postId, String platform);

    /**
     * Find posts eligible for X thread publishing:
     * - created_at + 24 hours <= NOW()
     * - comment count >= 6
     * - no active x_thread job exists
     */
    @Query(nativeQuery = true, value = """
        SELECT p.id FROM posts p
        WHERE p.created_at <= DATE_SUB(NOW(), INTERVAL 24 HOUR)
        AND p.deleted_at IS NULL
        AND (
            SELECT COUNT(*) FROM post_comments pc
            WHERE pc.post_id = p.id AND pc.deleted_at IS NULL
        ) >= 6
        AND NOT EXISTS (
            SELECT 1 FROM marketing_job mj
            WHERE mj.post_id = p.id
            AND mj.status IN ('REQUESTED', 'QUEUED', 'RUNNING', 'PUBLISHING', 'STALE')
            AND JSON_CONTAINS(mj.targets, '"x_thread"')
        )
        LIMIT :limit
        """)
    List<String> findPostsEligibleForXThreadPublish(int limit);
}
