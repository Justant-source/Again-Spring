package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingJob;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
