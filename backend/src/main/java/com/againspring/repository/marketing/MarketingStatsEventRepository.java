package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingStatsEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarketingStatsEventRepository extends JpaRepository<MarketingStatsEvent, Long> {

    List<MarketingStatsEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
