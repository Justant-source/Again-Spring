package com.againspring.service;

import com.againspring.domain.DailyStats;
import com.againspring.repository.DailyStatsRepository;
import com.againspring.repository.FeedbackRepository;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.PostRepository;
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
 * 매일 KST 자정에 전날 집계 데이터를 daily_stats에 기록.
 * 투표, 신규 회원, 피드백 등을 집계.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyStatsAggregatorService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final VoteRepository voteRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final FeedbackRepository feedbackRepository;
    private final DailyStatsRepository dailyStatsRepository;

    /**
     * 매일 KST 자정(00:00)에 어제의 모든 통계를 집계하여 daily_stats에 upsert.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void aggregateYesterdayVotes() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        aggregateForDate(yesterday);
    }

    /**
     * 특정 날짜의 모든 통계를 집계하여 daily_stats에 upsert.
     * 해당 날짜 KST 자정 ~ 다음날 KST 자정 사이의 데이터를 집계.
     */
    @Transactional
    public void aggregateForDate(LocalDate date) {
        Instant from = date.atStartOfDay(KST).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(KST).toInstant();

        long voteCount = voteRepository.countByCreatedAtBetween(from, to);
        long newUsers = userRepository.countByIsGuestFalseAndCreatedAtBetween(from, to);
        long postCount = postRepository.countByDeletedAtIsNullAndCreatedAtBetween(from, to);
        long feedbackCount = feedbackRepository.count(); // TODO: filter by date range

        DailyStats stats = dailyStatsRepository.findByStatDate(date)
                .orElseGet(() -> DailyStats.builder().statDate(date).build());

        stats.setVoteCount((int) voteCount);
        stats.setNewUsers((int) newUsers);
        stats.setPostCount((int) postCount);
        stats.setFeedbackCount((int) feedbackCount);
        // TODO: dau, avgTurns, crisisTriggers 데이터 소스 없음 (기존 세션 시스템 제거됨)

        dailyStatsRepository.save(stats);

        log.info("[DailyStats] {} 통계 집계 완료: 투표={}, 신규={}, 게시글={}, 피드백={}", date, voteCount, newUsers, postCount, feedbackCount);
    }

    /**
     * 지정된 기간의 모든 날짜에 대해 통계를 역산하여 채움 (backfill).
     * 기존 데이터가 있으면 덮어씀.
     */
    @Transactional
    public void backfill(LocalDate from, LocalDate to) {
        LocalDate current = from;
        while (!current.isAfter(to)) {
            aggregateForDate(current);
            current = current.plusDays(1);
        }
        log.info("[DailyStats] Backfill 완료: {} ~ {}", from, to);
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
