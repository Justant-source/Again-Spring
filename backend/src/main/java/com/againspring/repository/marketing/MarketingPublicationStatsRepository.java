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

}
