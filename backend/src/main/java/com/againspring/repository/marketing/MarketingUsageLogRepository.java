package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 마케팅 시뮬레이션 LLM 사용 로그 저장소
 */
@Repository
public interface MarketingUsageLogRepository extends JpaRepository<MarketingUsageLog, Long> {

    /**
     * 특정 기간 동안의 로그 수 조회
     */
    @Query("SELECT COUNT(l) FROM MarketingUsageLog l WHERE l.createdAt >= :from AND l.createdAt < :to")
    long countByCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 특정 기간 동안의 총 비용 조회
     */
    @Query("SELECT COALESCE(SUM(l.costUsd), 0) FROM MarketingUsageLog l WHERE l.createdAt >= :from AND l.createdAt < :to")
    BigDecimal sumCostByCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to);
}
