package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.MarketingHashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketingHashtagRepository extends JpaRepository<MarketingHashtag, Long> {
    List<MarketingHashtag> findByPlatformOrderByUsageCountDesc(MarketingContent.Platform platform);
    List<MarketingHashtag> findAllByOrderByUsageCountDesc();
    Optional<MarketingHashtag> findByPlatformAndTag(MarketingContent.Platform platform, String tag);
}
