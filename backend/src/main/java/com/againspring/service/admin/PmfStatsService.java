package com.againspring.service.admin;

import com.againspring.api.dto.response.AdminDashboardSummaryResponse;
import com.againspring.repository.DailyStatsRepository;
import com.againspring.repository.FeedbackRepository;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.CommunityReportRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteRepository;
import com.againspring.repository.inquiry.InquiryRepository;
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

    // private final SessionRepository sessionRepository; (removed)
    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final DailyStatsRepository dailyStatsRepository;
    private final PostRepository postRepository;
    private final VoteRepository voteRepository;
    private final CommunityReportRepository communityReportRepository;
    private final InquiryRepository inquiryRepository;
    // private final MessageRepository messageRepository; (removed)

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getDashboardSummary() {
        LocalDate today = LocalDate.now(KST);
        Instant startOfToday = today.atStartOfDay(KST).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(KST).toInstant();

        long todayNewUsers = userRepository.countByIsGuestFalseAndCreatedAtBetween(startOfToday, startOfTomorrow);
        long totalUsers = userRepository.countByIsGuestFalseAndDeletedAtIsNull();
        long totalPosts = postRepository.countByDeletedAtIsNull();
        long totalVotes = voteRepository.count();
        long totalComments = 0; // TODO: add comment count query if needed
        long pendingReports = communityReportRepository.countByStatus("PENDING");
        long openInquiries = inquiryRepository.countByStatus("OPEN");
        long todayFeedback = feedbackRepository.count(); // TODO: filter by today
        long todayVotes = voteRepository.countByCreatedAtBetween(startOfToday, startOfTomorrow);

        return AdminDashboardSummaryResponse.builder()
                .todayNewUsers(todayNewUsers)
                .totalUsers(totalUsers)
                .totalPosts(totalPosts)
                .totalVotes(totalVotes)
                .totalComments(totalComments)
                .pendingReports(pendingReports)
                .openInquiries(openInquiries)
                .todayFeedback(todayFeedback)
                .todayVotes(todayVotes)
                .build();
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
     * 현재 메시지에선 Haiku만 사용 → sonnet*는 0. (Sonnet은 Object 생성용, messages 외부)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLlmFailureRateLastDays(int days) {
        int safeDays = Math.min(Math.max(days, 1), 30);
        Instant since = Instant.now().minus(Duration.ofDays(safeDays));
        List<Object[]> rows = java.util.Collections.emptyList();

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
