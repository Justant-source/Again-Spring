package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingGenerationTrace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for MarketingGenerationTrace
 * Audit log of marketing content generation events (LLM calls, render diagnostics).
 */
@Repository
public interface MarketingGenerationTraceRepository extends JpaRepository<MarketingGenerationTrace, Long> {

    /**
     * Find all traces for a given marketing job.
     * @param jobId the marketing job ID
     * @return list of traces for this job (may be empty if job_id is NULL in some traces)
     */
    List<MarketingGenerationTrace> findByJobId(Long jobId);

    /**
     * Find all traces for a given post, ordered by creation time (newest first).
     * @param postId the post ID
     * @return list of traces for this post, newest first
     */
    List<MarketingGenerationTrace> findByPostIdOrderByCreatedAtDesc(String postId);
}
