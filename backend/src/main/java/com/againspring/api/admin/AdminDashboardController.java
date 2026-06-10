package com.againspring.api.admin;

import com.againspring.api.dto.response.DailyStatsResponse;
import com.againspring.api.dto.response.AdminDashboardSummaryResponse;
import com.againspring.service.DailyStatsAggregatorService;
import com.againspring.service.admin.DashboardOpsService;
import com.againspring.service.admin.PmfStatsService;
import com.againspring.service.admin.RetentionCohortService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin — Dashboard", description = "PMF 통계·리텐션 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
public class AdminDashboardController {

    private final PmfStatsService pmfStatsService;
    private final RetentionCohortService retentionCohortService;
    private final DailyStatsAggregatorService dailyStatsAggregatorService;
    private final DashboardOpsService dashboardOpsService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "대시보드 요약 통계")
    public ResponseEntity<AdminDashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(pmfStatsService.getDashboardSummary());
    }

    @GetMapping("/daily-stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "일별 통계 (최근 30일)")
    public ResponseEntity<List<Map<String, Object>>> getDailyStats() {
        return ResponseEntity.ok(pmfStatsService.getLast30DaysStats());
    }

    @GetMapping("/retention")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "리텐션 코호트 (최근 14일)")
    public ResponseEntity<List<Map<String, Object>>> getRetention() {
        return ResponseEntity.ok(retentionCohortService.getLast14DaysRetention());
    }

    @GetMapping("/llm-failure-rate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "LLM 호출 실패율 통계")
    public ResponseEntity<List<Map<String, Object>>> getLlmFailureRate(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(pmfStatsService.getLlmFailureRateLastDays(days));
    }

    @PostMapping("/stats/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "통계 역산 채움 (from ~ to 날짜 범위)")
    public ResponseEntity<Map<String, String>> backfillStats(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        dailyStatsAggregatorService.backfill(from, to);
        return ResponseEntity.ok(Map.of(
                "message", "통계 역산 완료",
                "from", from.toString(),
                "to", to.toString()
        ));
    }

    @GetMapping("/action-center")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "액션 센터 - 즉시 조치 필요 항목")
    public ResponseEntity<DashboardOpsService.ActionCenterDto> getActionCenter() {
        return ResponseEntity.ok(dashboardOpsService.getActionCenter());
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "KPI 메트릭 - 주요 성과 지표")
    public ResponseEntity<List<DashboardOpsService.KpiMetricDto>> getKpis(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(dashboardOpsService.getKpiMetrics(days));
    }

    @GetMapping("/pulse")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "커뮤니티 맥박 - 시간대별 콘텐츠 생성")
    public ResponseEntity<DashboardOpsService.PulseDto> getPulse(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(dashboardOpsService.getCommunityPulse(hours));
    }

    @GetMapping("/hot-posts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "핫 포스트 - 고참여 게시글")
    public ResponseEntity<List<DashboardOpsService.HotPostDto>> getHotPosts(
            @RequestParam(defaultValue = "48") int hours,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardOpsService.getHotPosts(hours, limit));
    }

    @GetMapping("/insights")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "커뮤니티 인사이트 - DAU/WAU/MAU, 펀넬, 콘텐츠 건강도")
    public ResponseEntity<DashboardOpsService.InsightsDto> getInsights(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "true") boolean realOnly) {
        return ResponseEntity.ok(dashboardOpsService.getCommunityInsights(days, realOnly));
    }

    @GetMapping("/traffic")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "트래픽 요약 - 방문 이벤트 분석")
    public ResponseEntity<DashboardOpsService.TrafficDto> getTraffic(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(dashboardOpsService.getTraffic(days));
    }
}
