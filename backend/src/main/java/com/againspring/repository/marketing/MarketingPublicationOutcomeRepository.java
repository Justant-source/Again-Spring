package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingPublicationOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MarketingPublicationOutcomeRepository extends JpaRepository<MarketingPublicationOutcome, Long> {

    boolean existsByJobIdAndPlatform(Long jobId, String platform);

    /** 기준선: 해당 플랫폼에서 이 게시 이전에 채점된 최근 30건. */
    List<MarketingPublicationOutcome> findTop30ByPlatformAndStatusAndPublishedAtBeforeOrderByPublishedAtDesc(
        String platform, String status, Instant publishedAt);

    List<MarketingPublicationOutcome> findByStatusAndPublishedAtGreaterThanEqual(String status, Instant since);

    List<MarketingPublicationOutcome> findByPlatformAndStatusAndPublishedAtGreaterThanEqual(
        String platform, String status, Instant since);
}
