package com.againspring.service.marketing;

import com.againspring.repository.marketing.MarketingUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * V15.7 마케팅 LLM 비용 모니터링 서비스
 * - 일일/월별 비용 통계 조회
 * - 월별 예산 알림 (80% 도달 시 경고)
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class CostMonitoringService {

    private static final BigDecimal MONTHLY_BUDGET = new BigDecimal("20");
    private static final BigDecimal BUDGET_THRESHOLD = MONTHLY_BUDGET.multiply(new BigDecimal("0.8"));

    private final MarketingUsageLogRepository usageLogRepository;

    /**
     * 특정 날짜의 비용 통계 조회
     *
     * @param date 조회 날짜
     * @return { "date": LocalDate, "count": long, "costUsd": BigDecimal }
     */
    public Map<String, Object> getDailyStats(LocalDate date) {
        Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long count = usageLogRepository.countByCreatedAtBetween(from, to);
        BigDecimal costUsd = usageLogRepository.sumCostByCreatedAtBetween(from, to);

        Map<String, Object> result = new HashMap<>();
        result.put("date", date);
        result.put("count", count);
        result.put("costUsd", costUsd);

        return result;
    }

    /**
     * 특정 월의 비용 통계 조회
     *
     * @param month 조회 월 (YearMonth)
     * @return { "month": YearMonth, "count": long, "costUsd": BigDecimal }
     */
    public Map<String, Object> getMonthlyStats(YearMonth month) {
        LocalDate startOfMonth = month.atDay(1);
        LocalDate startOfNextMonth = month.plusMonths(1).atDay(1);

        Instant from = startOfMonth.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = startOfNextMonth.atStartOfDay(ZoneOffset.UTC).toInstant();

        long count = usageLogRepository.countByCreatedAtBetween(from, to);
        BigDecimal costUsd = usageLogRepository.sumCostByCreatedAtBetween(from, to);

        Map<String, Object> result = new HashMap<>();
        result.put("month", month);
        result.put("count", count);
        result.put("costUsd", costUsd);

        return result;
    }

    /**
     * 월별 예산 알림 확인 (매일 9 AM UTC)
     * 월 예산의 80% 이상 도달 시 경고 로그 발행
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void checkBudgetAlert() {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        Map<String, Object> stats = getMonthlyStats(currentMonth);
        BigDecimal monthlyCost = (BigDecimal) stats.get("costUsd");

        if (monthlyCost.compareTo(BUDGET_THRESHOLD) >= 0) {
            log.warn("마케팅 월 예산 80% 도달: {}USD", monthlyCost);
        }
    }
}
