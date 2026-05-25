package com.againspring.api.admin.marketing;

import com.againspring.service.marketing.CostMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * V15.7 마케팅 LLM 비용 모니터링 컨트롤러
 * 일일/월별 비용 통계 조회 (ADMIN 전용)
 */
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/admin/marketing/cost")
@Tag(name = "Admin — Marketing Cost (V15.7)", description = "마케팅 LLM 비용 모니터링 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CostController {

    private final CostMonitoringService costMonitoringService;

    /**
     * 특정 날짜의 비용 통계 조회
     *
     * @param date 조회 날짜 (YYYY-MM-DD), 기본값: 오늘
     * @return { "date": LocalDate, "count": long, "costUsd": BigDecimal }
     */
    @GetMapping("/daily")
    @Operation(
            summary = "일일 비용 통계 조회",
            description = "특정 날짜의 마케팅 LLM 사용량 및 비용 조회 (ADMIN)"
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Map<String, Object>> getDailyStats(
            @Parameter(description = "조회 날짜 (YYYY-MM-DD, 기본값: 오늘)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        if (date == null) {
            date = LocalDate.now();
        }
        Map<String, Object> stats = costMonitoringService.getDailyStats(date);
        return ResponseEntity.ok(stats);
    }

    /**
     * 특정 월의 비용 통계 조회
     *
     * @param month 조회 월 (YYYY-MM), 기본값: 현재 월
     * @return { "month": YearMonth, "count": long, "costUsd": BigDecimal }
     */
    @GetMapping("/monthly")
    @Operation(
            summary = "월별 비용 통계 조회",
            description = "특정 월의 마케팅 LLM 사용량 및 비용 조회 (ADMIN)"
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Map<String, Object>> getMonthlyStats(
            @Parameter(description = "조회 월 (YYYY-MM, 기본값: 현재 월)")
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM")
            YearMonth month
    ) {
        if (month == null) {
            month = YearMonth.now();
        }
        Map<String, Object> stats = costMonitoringService.getMonthlyStats(month);
        return ResponseEntity.ok(stats);
    }
}
