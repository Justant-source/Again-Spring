package com.againspring.api.admin;

import com.againspring.api.dto.response.CrisisMessageResponse;
import com.againspring.service.admin.CrisisMonitoringService;
import com.againspring.service.admin.PmfStatsService;
import com.againspring.service.admin.RetentionCohortService;
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
public class AdminDashboardController {

    private final PmfStatsService pmfStatsService;
    private final RetentionCohortService retentionCohortService;
    private final CrisisMonitoringService crisisMonitoringService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(pmfStatsService.getDashboardSummary());
    }

    @GetMapping("/daily-stats")
    public ResponseEntity<List<Map<String, Object>>> getDailyStats() {
        return ResponseEntity.ok(pmfStatsService.getLast30DaysStats());
    }

    @GetMapping("/retention")
    public ResponseEntity<List<Map<String, Object>>> getRetention() {
        return ResponseEntity.ok(retentionCohortService.getLast14DaysRetention());
    }

    @GetMapping("/crisis-recent")
    public ResponseEntity<List<CrisisMessageResponse>> getCrisisRecent(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(crisisMonitoringService.getRecent(limit));
    }

    @GetMapping("/llm-failure-rate")
    public ResponseEntity<List<Map<String, Object>>> getLlmFailureRate(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(pmfStatsService.getLlmFailureRateLastDays(days));
    }
}
