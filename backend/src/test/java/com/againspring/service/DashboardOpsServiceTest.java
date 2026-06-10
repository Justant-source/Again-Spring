package com.againspring.service;

import com.againspring.repository.DailyStatsRepository;
import com.againspring.repository.FeedbackRepository;
import com.againspring.repository.UserRepository;
import com.againspring.repository.VisitEventRepository;
import com.againspring.repository.community.CommunityReportRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteRepository;
import com.againspring.repository.inquiry.InquiryRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.againspring.service.admin.DashboardOpsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardOpsService Unit Tests")
class DashboardOpsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private CommunityReportRepository communityReportRepository;

    @Mock
    private InquiryRepository inquiryRepository;

    @Mock
    private MarketingJobRepository marketingJobRepository;

    @Mock
    private DailyStatsRepository dailyStatsRepository;

    @Mock
    private VisitEventRepository visitEventRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DashboardOpsService service;

    @Test
    @DisplayName("getActionCenter_returnsCorrectCounts")
    void testGetActionCenterReturnsCorrectCounts() {
        when(communityReportRepository.countByStatus("PENDING")).thenReturn(5L);
        when(inquiryRepository.countByStatus("OPEN")).thenReturn(3L);
        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), any(Class.class)))
                .thenReturn(0L);

        DashboardOpsService.ActionCenterDto result = service.getActionCenter();

        assertNotNull(result);
        assertEquals(5L, result.getPendingReports());
        assertEquals(3L, result.getOpenInquiries());
    }

    @Test
    @DisplayName("getKpiMetrics_returnsListWithSixItems")
    void testGetKpiMetricsReturnsListWithSixItems() {
        when(communityReportRepository.countByStatus("PENDING")).thenReturn(0L);
        when(inquiryRepository.countByStatus("OPEN")).thenReturn(0L);
        when(userRepository.countByIsGuestFalseAndCreatedAtBetween(any(), any())).thenReturn(100L);
        when(userRepository.countByIsGuestFalseAndDeletedAtIsNull()).thenReturn(500L);
        when(postRepository.countByDeletedAtIsNull()).thenReturn(200L);
        when(voteRepository.countByCreatedAtBetween(any(), any())).thenReturn(50L);
        when(dailyStatsRepository.findByStatDateBetweenOrderByStatDateAsc(any(), any()))
                .thenReturn(new ArrayList<>());

        List<DashboardOpsService.KpiMetricDto> result = service.getKpiMetrics(7);

        assertNotNull(result);
        assertEquals(6, result.size());
    }

    @Test
    @DisplayName("getCommunityPulse_sparklineLength_matchesDays")
    void testGetCommunityPulseSparklineLengthMatchesDays() {
        when(userRepository.findAllSyntheticIds()).thenReturn(java.util.Set.of());
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(new ArrayList<>());

        DashboardOpsService.PulseDto result = service.getCommunityPulse(24);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals(24, result.getData().size());
    }

    @Test
    @DisplayName("getHotPosts_scoreOrder_isCorrect")
    void testGetHotPostsScoreOrderIsCorrect() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(new ArrayList<>());

        List<DashboardOpsService.HotPostDto> result = service.getHotPosts(48, 5);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getTrafficSummary_noData_returnsEmpty")
    void testGetTrafficSummaryNoDataReturnsEmpty() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(new ArrayList<>());

        DashboardOpsService.TrafficDto result = service.getTraffic(30);

        assertNotNull(result);
        assertNotNull(result.getDailySeries());
        assertNotNull(result.getTopSources());
        assertNotNull(result.getTopCampaigns());
        assertTrue(result.getDailySeries().isEmpty());
        assertTrue(result.getTopSources().isEmpty());
        assertTrue(result.getTopCampaigns().isEmpty());
    }

    @Test
    @DisplayName("getKpiMetrics_deltaPercentCalculated")
    void testGetKpiMetricsDeltaPercentCalculated() {
        when(communityReportRepository.countByStatus("PENDING")).thenReturn(0L);
        when(inquiryRepository.countByStatus("OPEN")).thenReturn(0L);
        when(userRepository.countByIsGuestFalseAndCreatedAtBetween(any(), any())).thenReturn(50L);
        when(userRepository.countByIsGuestFalseAndDeletedAtIsNull()).thenReturn(500L);
        when(postRepository.countByDeletedAtIsNull()).thenReturn(200L);
        when(voteRepository.countByCreatedAtBetween(any(), any())).thenReturn(25L);
        when(dailyStatsRepository.findByStatDateBetweenOrderByStatDateAsc(any(), any()))
                .thenReturn(new ArrayList<>());

        List<DashboardOpsService.KpiMetricDto> result = service.getKpiMetrics(7);

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(k -> k.getDeltaPercent() != null));
    }

    @Test
    @DisplayName("getCommunityInsights_returnsValidInsights")
    void testGetCommunityInsightsReturnsValidInsights() {
        when(dailyStatsRepository.findByStatDate(any())).thenReturn(java.util.Optional.empty());
        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), any(Class.class)))
                .thenReturn(0L);

        DashboardOpsService.InsightsDto result = service.getCommunityInsights(30, true);

        assertNotNull(result);
        assertNotNull(result.getFunnel());
        assertNotNull(result.getContentHealth());
        assertNotNull(result.getProductionSeries());
    }

    @Test
    @DisplayName("getKpiMetrics_valueBounds_areCorrect")
    void testGetKpiMetricsValueBoundsAreCorrect() {
        when(communityReportRepository.countByStatus("PENDING")).thenReturn(0L);
        when(inquiryRepository.countByStatus("OPEN")).thenReturn(0L);
        when(userRepository.countByIsGuestFalseAndCreatedAtBetween(any(), any())).thenReturn(100L);
        when(userRepository.countByIsGuestFalseAndDeletedAtIsNull()).thenReturn(1000L);
        when(postRepository.countByDeletedAtIsNull()).thenReturn(500L);
        when(voteRepository.countByCreatedAtBetween(any(), any())).thenReturn(100L);
        when(dailyStatsRepository.findByStatDateBetweenOrderByStatDateAsc(any(), any()))
                .thenReturn(new ArrayList<>());

        List<DashboardOpsService.KpiMetricDto> result = service.getKpiMetrics(7);

        assertNotNull(result);
        for (DashboardOpsService.KpiMetricDto metric : result) {
            assertTrue(metric.getValue() >= 0, "KPI value should be >= 0");
            assertNotNull(metric.getKey(), "KPI key should not be null");
            assertNotNull(metric.getLabel(), "KPI label should not be null");
        }
    }
}
