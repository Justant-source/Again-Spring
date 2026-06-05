package com.againspring.api.admin;

import com.againspring.api.dto.response.DailyStatsResponse;
import com.againspring.api.dto.response.AdminDashboardSummaryResponse;
import com.againspring.service.DailyStatsAggregatorService;
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
}
