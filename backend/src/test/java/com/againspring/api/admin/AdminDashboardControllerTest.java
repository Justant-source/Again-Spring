package com.againspring.api.admin;

import com.againspring.service.DailyStatsAggregatorService;
import com.againspring.service.admin.DashboardOpsService;
import com.againspring.service.admin.PmfStatsService;
import com.againspring.service.admin.RetentionCohortService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminDashboardController 위임 계약 — e2e Journey 16-C / 17 insights API에서 이관.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDashboardController Tests")
class AdminDashboardControllerTest {

    @Mock
    private PmfStatsService pmfStatsService;
    @Mock
    private RetentionCohortService retentionCohortService;
    @Mock
    private DailyStatsAggregatorService dailyStatsAggregatorService;
    @Mock
    private DashboardOpsService dashboardOpsService;

    @InjectMocks
    private AdminDashboardController controller;

    @Test
    @DisplayName("GET action-center → service 위임")
    void getActionCenter_delegates() {
        DashboardOpsService.ActionCenterDto dto = DashboardOpsService.ActionCenterDto.builder()
                .pendingReports(1L)
                .openInquiries(2L)
                .marketingAwaitingApproval(0L)
                .aiFailuresToday(0L)
                .build();
        when(dashboardOpsService.getActionCenter()).thenReturn(dto);

        ResponseEntity<DashboardOpsService.ActionCenterDto> res = controller.getActionCenter();

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody()).isSameAs(dto);
        verify(dashboardOpsService).getActionCenter();
    }

    @Test
    @DisplayName("GET kpis → service 위임")
    void getKpis_delegates() {
        when(dashboardOpsService.getKpiMetrics(7)).thenReturn(List.of());

        ResponseEntity<List<DashboardOpsService.KpiMetricDto>> res = controller.getKpis(7);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody()).isEmpty();
        verify(dashboardOpsService).getKpiMetrics(7);
    }

    @Test
    @DisplayName("GET hot-posts → service 위임")
    void getHotPosts_delegates() {
        when(dashboardOpsService.getHotPosts(48, 5)).thenReturn(List.of());

        ResponseEntity<List<DashboardOpsService.HotPostDto>> res = controller.getHotPosts(48, 5);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verify(dashboardOpsService).getHotPosts(48, 5);
    }

    @Test
    @DisplayName("GET pulse → service 위임")
    void getPulse_delegates() {
        DashboardOpsService.PulseDto pulse = DashboardOpsService.PulseDto.builder()
                .data(List.of())
                .build();
        when(dashboardOpsService.getCommunityPulse(24)).thenReturn(pulse);

        ResponseEntity<DashboardOpsService.PulseDto> res = controller.getPulse(24);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody().getData()).isEmpty();
        verify(dashboardOpsService).getCommunityPulse(24);
    }

    @Test
    @DisplayName("GET insights → service 위임")
    void getInsights_delegates() {
        DashboardOpsService.InsightsDto insights = DashboardOpsService.InsightsDto.builder()
                .dau(1)
                .mau(2L)
                .build();
        when(dashboardOpsService.getCommunityInsights(30, true)).thenReturn(insights);

        ResponseEntity<DashboardOpsService.InsightsDto> res = controller.getInsights(30, true);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody().getDau()).isEqualTo(1);
        verify(dashboardOpsService).getCommunityInsights(30, true);
    }

    @Test
    @DisplayName("GET traffic → service 위임")
    void getTraffic_delegates() {
        DashboardOpsService.TrafficDto traffic = DashboardOpsService.TrafficDto.builder()
                .dailySeries(List.of())
                .topSources(List.of())
                .build();
        when(dashboardOpsService.getTraffic(30)).thenReturn(traffic);

        ResponseEntity<DashboardOpsService.TrafficDto> res = controller.getTraffic(30);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verify(dashboardOpsService).getTraffic(30);
    }
}
