package com.againspring.service;

import com.againspring.domain.DailyStats;
import com.againspring.repository.DailyStatsRepository;
import com.againspring.repository.community.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 매일 KST 자정에 전날 투표 수를 daily_stats에 기록.
 * 홈 화면 "오늘 모인 시선" 수치의 공식 집계 소스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyStatsAggregatorService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final VoteRepository voteRepository;
    private final DailyStatsRepository dailyStatsRepository;

    /**
     * 매일 KST 자정(00:00)에 어제 투표 수를 집계하여 daily_stats에 upsert.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void aggregateYesterdayVotes() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        aggregateVotesForDate(yesterday);
    }

    /**
     * 특정 날짜의 투표 수를 집계하여 daily_stats에 upsert.
     * 해당 날짜 KST 자정 ~ 다음날 KST 자정 사이의 투표를 집계.
     */
    @Transactional
    public void aggregateVotesForDate(LocalDate date) {
        Instant from = date.atStartOfDay(KST).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(KST).toInstant();

        long count = voteRepository.countByCreatedAtBetween(from, to);

        DailyStats stats = dailyStatsRepository.findByStatDate(date)
                .orElseGet(() -> DailyStats.builder().statDate(date).build());

        stats.setVoteCount((int) count);
        dailyStatsRepository.save(stats);

        log.info("[DailyStats] {} 투표 수 집계 완료: {}건", date, count);
    }

    /**
     * 오늘(KST 기준) 자정부터 현재까지의 투표 수를 실시간으로 조회.
     * 홈 화면 "오늘 모인 시선" 실시간 표시용.
     */
    public long countTodayVotes() {
        LocalDate today = LocalDate.now(KST);
        Instant from = today.atStartOfDay(KST).toInstant();
        Instant to = Instant.now();
        return voteRepository.countByCreatedAtBetween(from, to);
    }
}
