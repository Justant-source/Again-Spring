package com.againspring.api.admin.marketing;

import com.againspring.api.dto.response.DashboardSummaryResponse;
import com.againspring.api.dto.response.WeeklyTrendItem;
import com.againspring.service.marketing.CostMonitoringService;
import com.againspring.service.marketing.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/marketing/dashboard")
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Marketing Dashboard", description = "Marketing dashboard KPI endpoints")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CostMonitoringService costMonitoringService;

    @GetMapping("/summary")
    @Operation(summary = "Get dashboard KPI summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @GetMapping("/trend")
    @Operation(summary = "Get weekly cost trend (last 4 weeks)")
    public ResponseEntity<List<WeeklyTrendItem>> getWeeklyTrend() {
        return ResponseEntity.ok(costMonitoringService.getWeeklyTrend());
    }
}
