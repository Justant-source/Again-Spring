package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingAccountStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketingAccountStatsRepository extends JpaRepository<MarketingAccountStats, Long> {

    Optional<MarketingAccountStats> findByPlatformAndCollectedOn(String platform, LocalDate collectedOn);

    List<MarketingAccountStats> findByCollectedOnGreaterThanEqualOrderByCollectedOnDescPlatformAsc(LocalDate since);
}
