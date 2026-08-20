package com.againspring.aiuser.orchestrator.admin;

import com.againspring.aiuser.orchestrator.service.llm.LlmCircuitBreaker;
import com.againspring.aiuser.orchestrator.service.llm.LlmStatsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin metrics endpoints for LLM observability.
 * Access from Docker internal network only (no external routing).
 */
@Slf4j
@RestController
@RequestMapping("/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final LlmStatsRecorder statsRecorder;
    private final LlmCircuitBreaker circuitBreaker;

    /**
     * Returns in-memory rolling 24h LLM stats aggregated from [LLMSTATS] logs.
     * Format: type -> { totalCalls, totalRetries, retryReasons: {REASON: count}, ... }
     */
    @GetMapping("/llm-today")
    public ResponseEntity<Map<String, Object>> getLlmStats() {
        var statsByType = statsRecorder.getTodayStats();

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", java.time.Instant.now());
        response.put("scope", "in-memory 24h rolling");

        // Include circuit breaker state
        Map<String, Object> circuitState = new HashMap<>();
        LlmCircuitBreaker.Telemetry breaker = circuitBreaker.getTelemetry();
        circuitState.put("state", breaker.getState());
        circuitState.put("reason", breaker.getReason());
        circuitState.put("consecutiveFailures", breaker.getConsecutiveFailures());
        circuitState.put("promptHashes", breaker.getRecentPromptHashes());
        circuitState.put("openedAt", breaker.getOpenedAt());
        circuitState.put("autoResetAt", breaker.getAutoResetAt());
        circuitState.put("totalOpens", breaker.getTotalOpens());
        response.put("circuitBreaker", circuitState);

        Map<String, Map<String, Object>> typeStats = new HashMap<>();
        for (var entry : statsByType.entrySet()) {
            String type = entry.getKey();
            LlmStatsRecorder.DayStats stats = entry.getValue();

            Map<String, Object> typeData = new HashMap<>();
            typeData.put("totalCalls", stats.getTotalCalls());
            typeData.put("totalRetries", stats.getTotalRetries());
            typeData.put("retryRate", stats.getTotalCalls() > 0
                ? String.format("%.1f%%", 100.0 * stats.getTotalRetries() / stats.getTotalCalls())
                : "0%");
            typeData.put("retryReasons", stats.getRetryReasons());
            typeData.put("resultCounts", stats.getResultCounts());
            typeData.put("totalInputTokens", stats.getTotalInputTokens());
            typeData.put("totalOutputTokens", stats.getTotalOutputTokens());
            typeData.put("totalCacheRead", stats.getTotalCacheRead());
            typeData.put("totalCacheWrite", stats.getTotalCacheWrite());
            double avgCacheHit = stats.getTotalCalls() > 0
                ? 100.0 * stats.getTotalCacheRead() / Math.max(1, stats.getTotalInputTokens() + stats.getTotalCacheRead() + stats.getTotalCacheWrite())
                : 0;
            typeData.put("avgCacheHitPercent", String.format("%.1f%%", avgCacheHit));

            typeStats.put(type, typeData);
        }
        response.put("stats", typeStats);

        return ResponseEntity.ok(response);
    }
}
