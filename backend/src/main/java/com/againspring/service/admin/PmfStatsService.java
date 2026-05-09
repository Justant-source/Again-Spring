package com.againspring.service.admin;

import com.againspring.domain.enums.SessionStatus;
import com.againspring.repository.DailyStatsRepository;
import com.againspring.repository.FeedbackRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PmfStatsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final DailyStatsRepository dailyStatsRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardSummary() {
        LocalDate today = LocalDate.now(KST);
        Instant startOfToday = today.atStartOfDay(KST).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(KST).toInstant();

        long todayTotal = sessionRepository.countByCreatedAtBetween(startOfToday, startOfTomorrow);
        long todayCompleted = sessionRepository.countByStatusAndCreatedAtBetween(
                SessionStatus.COMPLETED, startOfToday, startOfTomorrow);
        long todayGuest = sessionRepository.countGuestSessionsBetween(startOfToday, startOfTomorrow);
        long todayMember = todayTotal - todayGuest;
        long newUsers = userRepository.countByIsGuestFalseAndCreatedAtBetween(startOfToday, startOfTomorrow);
        Double avgTurns = sessionRepository.avgTurnsBetween(startOfToday, startOfTomorrow);
        long totalFeedbacks = feedbackRepository.count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayTotalSessions", todayTotal);
        result.put("todayCompletedSessions", todayCompleted);
        result.put("todayGuestSessions", todayGuest);
        result.put("todayMemberSessions", todayMember);
        result.put("todayNewUsers", newUsers);
        result.put("avgTurnsToday", avgTurns != null ? Math.round(avgTurns * 100.0) / 100.0 : 0.0);
        result.put("finalizeRate", todayTotal > 0 ? Math.round((double) todayCompleted / todayTotal * 10000) / 100.0 : 0.0);
        result.put("totalFeedbacks", totalFeedbacks);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLast30DaysStats() {
        return dailyStatsRepository.findTop30ByOrderByStatDateDesc().stream()
                .map(s -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", s.getStatDate().toString());
                    row.put("dau", s.getDau());
                    row.put("newUsers", s.getNewUsers());
                    row.put("guestSessions", s.getGuestSessions());
                    row.put("memberSessions", s.getMemberSessions());
                    row.put("completedSessions", s.getCompletedSessions());
                    row.put("avgTurns", s.getAvgTurns());
                    row.put("crisisTriggers", s.getCrisisTriggers());
                    row.put("feedbackCount", s.getFeedbackCount());
                    return row;
                })
                .toList();
    }
}
