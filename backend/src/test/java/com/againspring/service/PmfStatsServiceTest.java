package com.againspring.service;

import com.againspring.repository.DailyStatsRepository;
import com.againspring.repository.FeedbackRepository;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.service.admin.PmfStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PmfStatsService Tests")
class PmfStatsServiceTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private UserRepository userRepository;
    @Mock private FeedbackRepository feedbackRepository;
    @Mock private DailyStatsRepository dailyStatsRepository;
    @Mock private MessageRepository messageRepository;

    private PmfStatsService pmfStatsService;

    @BeforeEach
    void setUp() {
        pmfStatsService = new PmfStatsService(sessionRepository, userRepository, feedbackRepository, dailyStatsRepository, messageRepository);
    }

    @Test
    @DisplayName("0행 데이터에서도 예외 없이 요약 반환")
    void getDashboardSummary_zeroData_returnsZeroValues() {
        when(sessionRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(sessionRepository.countByStatusAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);
        when(sessionRepository.countGuestSessionsBetween(any(), any())).thenReturn(0L);
        when(userRepository.countByIsGuestFalseAndCreatedAtBetween(any(), any())).thenReturn(0L);
        when(sessionRepository.avgTurnsBetween(any(), any())).thenReturn(null);
        when(feedbackRepository.count()).thenReturn(0L);

        Map<String, Object> summary = pmfStatsService.getDashboardSummary();

        assertNotNull(summary);
        assertEquals(0L, summary.get("todayTotalSessions"));
        assertEquals(0.0, summary.get("finalizeRate"));
    }

    @Test
    @DisplayName("totalSessions=5, completed=5 → finalizeRate=100.0")
    void getDashboardSummary_allCompleted_finalize100() {
        when(sessionRepository.countByCreatedAtBetween(any(), any())).thenReturn(5L);
        when(sessionRepository.countByStatusAndCreatedAtBetween(any(), any(), any())).thenReturn(5L);
        when(sessionRepository.countGuestSessionsBetween(any(), any())).thenReturn(0L);
        when(userRepository.countByIsGuestFalseAndCreatedAtBetween(any(), any())).thenReturn(0L);
        when(sessionRepository.avgTurnsBetween(any(), any())).thenReturn(8.0);
        when(feedbackRepository.count()).thenReturn(0L);

        Map<String, Object> summary = pmfStatsService.getDashboardSummary();

        assertEquals(100.0, summary.get("finalizeRate"));
    }

    @Test
    @DisplayName("avgTurns null 반환 시 0.0으로 처리")
    void getDashboardSummary_nullAvgTurns_returnsZero() {
        when(sessionRepository.countByCreatedAtBetween(any(), any())).thenReturn(3L);
        when(sessionRepository.countByStatusAndCreatedAtBetween(any(), any(), any())).thenReturn(1L);
        when(sessionRepository.countGuestSessionsBetween(any(), any())).thenReturn(0L);
        when(userRepository.countByIsGuestFalseAndCreatedAtBetween(any(), any())).thenReturn(0L);
        when(sessionRepository.avgTurnsBetween(any(), any())).thenReturn(null);
        when(feedbackRepository.count()).thenReturn(0L);

        Map<String, Object> summary = pmfStatsService.getDashboardSummary();

        assertEquals(0.0, summary.get("avgTurnsToday"));
    }

    @Test
    @DisplayName("getLast30DaysStats 빈 데이터 → 빈 리스트")
    void getLast30DaysStats_empty_returnsEmptyList() {
        when(dailyStatsRepository.findTop30ByOrderByStatDateDesc()).thenReturn(List.of());

        var result = pmfStatsService.getLast30DaysStats();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
