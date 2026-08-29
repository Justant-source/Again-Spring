package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingHoldingExclusion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketingHoldingExclusionRepository
        extends JpaRepository<MarketingHoldingExclusion, String> {
}
