package com.againspring.aiuser.orchestrator.service.llm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rolling 24h counter for LLM stats.
 * Aggregates [LLMSTATS] log entries parsed at runtime (or called directly).
 *
 * Counters per type (POST|COMMENT|REPLY|CRITIQUE|PROOFREAD|VARIANT|SCRIPT):
 * - Total calls
 * - Total retries
 * - Retry reasons (PROVIDER_ERROR, PARSE_FAIL, EMPTY_RESULT, CRITIQUE_FAIL, SAFETY_BLOCKED, TIMEOUT, NONE)
 * - Total input/output tokens
 *
 * Thread-safe for concurrent calls from logging framework + admin endpoint.
 */
@Slf4j
@Service
public class LlmStatsRecorder {

    @Data
    public static class DayStats {
        private int totalCalls;
        private int totalRetries;
        private int totalInputTokens;
        private int totalOutputTokens;
        private int totalCacheRead;
        private int totalCacheWrite;
        private Map<String, Integer> retryReasons = new ConcurrentHashMap<>();  // reason -> count
        private Map<String, Integer> resultCounts = new ConcurrentHashMap<>();   // result (OK|RETRY|FAIL) -> count
    }

    private final Map<String, DayStats> statsByType = new ConcurrentHashMap<>();

    public void recordCall(String type, int inputTokens, int outputTokens, int cacheReadTokens,
                           int cacheWriteTokens, String result, String retryReason) {
        String typeKey = type != null ? type : "UNKNOWN";
        DayStats stats = statsByType.computeIfAbsent(typeKey, k -> new DayStats());

        stats.totalCalls++;
        stats.totalInputTokens += inputTokens;
        stats.totalOutputTokens += outputTokens;
        stats.totalCacheRead += cacheReadTokens;
        stats.totalCacheWrite += cacheWriteTokens;

        if (result != null) {
            stats.resultCounts.merge(result, 1, Integer::sum);
        }

        if (retryReason != null && !"NONE".equals(retryReason)) {
            stats.totalRetries++;
            stats.retryReasons.merge(retryReason, 1, Integer::sum);
        }
    }

    public Map<String, DayStats> getTodayStats() {
        return Collections.unmodifiableMap(new HashMap<>(statsByType));
    }

    public void reset() {
        statsByType.clear();
    }

    public DayStats getStatsForType(String type) {
        return statsByType.getOrDefault(type, new DayStats());
    }
}
