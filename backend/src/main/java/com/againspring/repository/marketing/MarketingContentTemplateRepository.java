package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.MarketingContentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarketingContentTemplateRepository extends JpaRepository<MarketingContentTemplate, Long> {
    List<MarketingContentTemplate> findByPlatformAndIsActiveTrue(MarketingContent.Platform platform);
    List<MarketingContentTemplate> findByIsActiveTrue();
    List<MarketingContentTemplate> findByPlatform(MarketingContent.Platform platform);
}
