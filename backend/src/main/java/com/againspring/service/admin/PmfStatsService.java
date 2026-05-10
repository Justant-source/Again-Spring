package com.againspring.service.admin;

import com.againspring.domain.enums.SessionStatus;
import com.againspring.repository.DailyStatsRepository;
import com.againspring.repository.FeedbackRepository;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
    private final MessageRepository messageRepository;

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

    /**
     * V11 — 최근 N일 LLM 호출 실패율 (KST 일별 그룹).
     * 응답 row: { date, haikuTotal, haikuFallback, sonnetTotal, sonnetFallback }
     * 현재 메시지에선 Haiku만 사용 → sonnet*는 0. (Sonnet은 Report 생성용, messages 외부)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLlmFailureRateLastDays(int days) {
        int safeDays = Math.min(Math.max(days, 1), 30);
        Instant since = Instant.now().minus(Duration.ofDays(safeDays));
        List<Object[]> rows = messageRepository.aggregateMediatorByDay(since);

        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            // d: java.sql.Date, total: Number, fb: Number (or null)
            Object dateObj = row[0];
            String dateStr = dateObj instanceof Date sqlDate ? sqlDate.toString() : String.valueOf(dateObj);
            entry.put("date", dateStr);
            long total = toLong(row[1]);
            long fb = toLong(row[2]);
            entry.put("haikuTotal", total);
            entry.put("haikuFallback", fb);
            entry.put("sonnetTotal", 0L);
            entry.put("sonnetFallback", 0L);
            result.add(entry);
        }
        return result;
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (NumberFormatException e) { return 0L; }
    }
}
