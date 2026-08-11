package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingPublicationStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MarketingPublicationStatsRepository extends JpaRepository<MarketingPublicationStats, Long> {

    List<MarketingPublicationStats> findByJobIdOrderByCollectedAtDesc(Long jobId);

    @Query("""
        SELECT s FROM MarketingPublicationStats s
        WHERE s.collectedAt >= :since
        ORDER BY s.collectedAt DESC
        """)
    List<MarketingPublicationStats> findCollectedSince(@Param("since") Instant since);

    @Query(nativeQuery = true, value = """
        SELECT s.* FROM marketing_publication_stats s
        INNER JOIN (
          SELECT job_id, platform, MAX(collected_at) AS max_collected
          FROM marketing_publication_stats
          WHERE collected_at >= :since
          GROUP BY job_id, platform
        ) latest
          ON s.job_id = latest.job_id
         AND s.platform = latest.platform
         AND s.collected_at = latest.max_collected
        """)
    List<MarketingPublicationStats> findLatestPerJobPlatformSince(@Param("since") Instant since);
}
