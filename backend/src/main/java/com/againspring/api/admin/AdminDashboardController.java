package com.againspring.api.admin;

import com.againspring.api.dto.response.CrisisMessageResponse;
import com.againspring.service.admin.CrisisMonitoringService;
import com.againspring.service.admin.PmfStatsService;
import com.againspring.service.admin.RetentionCohortService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin — Dashboard", description = "PMF 통계·리텐션·위기 모니터링 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
public class AdminDashboardController {

    private final PmfStatsService pmfStatsService;
    private final RetentionCohortService retentionCohortService;
    private final CrisisMonitoringService crisisMonitoringService;

    @GetMapping("/summary")
    @Operation(summary = "대시보드 요약 통계", description = "PMF 핵심 지표(DAU, 세션 수, 완료율 등) 요약 반환")
    @ApiResponse(responseCode = "200", description = "요약 통계 맵 반환")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(pmfStatsService.getDashboardSummary());
    }

    @GetMapping("/daily-stats")
    @Operation(summary = "일별 통계 (최근 30일)", description = "최근 30일간 일별 DAU·세션·완료 지표 목록 반환")
    @ApiResponse(responseCode = "200", description = "일별 통계 목록")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<List<Map<String, Object>>> getDailyStats() {
        return ResponseEntity.ok(pmfStatsService.getLast30DaysStats());
    }

    @GetMapping("/retention")
    @Operation(summary = "리텐션 코호트 (최근 14일)", description = "최근 14일 코호트별 리텐션 데이터 반환")
    @ApiResponse(responseCode = "200", description = "코호트 리텐션 목록")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<List<Map<String, Object>>> getRetention() {
        return ResponseEntity.ok(retentionCohortService.getLast14DaysRetention());
    }

    @GetMapping("/crisis-recent")
    @Operation(summary = "최근 위기 메시지 조회", description = "위기 감지된 최근 메시지를 limit 건 반환 (기본 20)")
    @ApiResponse(responseCode = "200", description = "위기 메시지 목록")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<List<CrisisMessageResponse>> getCrisisRecent(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(crisisMonitoringService.getRecent(limit));
    }

    @GetMapping("/llm-failure-rate")
    @Operation(summary = "LLM 호출 실패율 통계", description = "최근 N일간 LLM 호출 실패율 반환 (기본 7일)")
    @ApiResponse(responseCode = "200", description = "날짜별 실패율 목록")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<List<Map<String, Object>>> getLlmFailureRate(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(pmfStatsService.getLlmFailureRateLastDays(days));
    }
}
