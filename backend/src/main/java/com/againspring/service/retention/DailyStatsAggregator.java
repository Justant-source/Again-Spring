package com.againspring.service.retention;

import com.againspring.domain.DailyStats;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.repository.DailyStatsRepository;
import com.againspring.repository.FeedbackRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyStatsAggregator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final DailyStatsRepository dailyStatsRepository;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void aggregateYesterday() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        aggregateForDate(yesterday);
    }

    @Transactional
    public void aggregateForDate(LocalDate date) {
        if (dailyStatsRepository.findByStatDate(date).isPresent()) {
            log.info("[DailyStatsAggregator] {} already aggregated, skipping", date);
            return;
        }

        Instant from = date.atStartOfDay(KST).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(KST).toInstant();

        long total = sessionRepository.countByCreatedAtBetween(from, to);
        long completed = sessionRepository.countByStatusAndCreatedAtBetween(SessionStatus.COMPLETED, from, to);
        long guest = sessionRepository.countGuestSessionsBetween(from, to);
        long member = total - guest;
        long newUsers = userRepository.countByIsGuestFalseAndCreatedAtBetween(from, to);
        Double avgTurnsRaw = sessionRepository.avgTurnsBetween(from, to);
        BigDecimal avgTurns = avgTurnsRaw != null
                ? BigDecimal.valueOf(Math.round(avgTurnsRaw * 100.0) / 100.0)
                : BigDecimal.ZERO;
        long feedbacks = feedbackRepository.findAll().stream()
                .filter(f -> {
                    Instant c = f.getCreatedAt();
                    return c != null && !c.isBefore(from) && c.isBefore(to);
                }).count();

        DailyStats stats = DailyStats.builder()
                .statDate(date)
                .dau((int) total)
                .newUsers((int) newUsers)
                .guestSessions((int) guest)
                .memberSessions((int) member)
                .completedSessions((int) completed)
                .avgTurns(avgTurns)
                .feedbackCount((int) feedbacks)
                .build();

        dailyStatsRepository.save(stats);
        log.info("[DailyStatsAggregator] {} aggregated: sessions={}, completed={}, newUsers={}",
                date, total, completed, newUsers);
    }
}
