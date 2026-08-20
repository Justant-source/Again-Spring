package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LlmStatsLogger builder.
 * Validates the [LLMSTATS] single-line log format consistency.
 */
class LlmStatsLoggerTest {

    @Test
    void buildFormatsStatsLineWithAllFields() {
        String stats = new LlmStatsLogger("CLI", "claude-haiku-4-5-20251001", "corr-123")
            .attempt(1)
            .retryReason("NONE")
            .tokens(1500, 750)
            .cacheTokens(500, 300)
            .cacheHitPercent(25)
            .result("OK")
            .duration(2500L)
            .build();

        assertTrue(stats.startsWith("[LLMSTATS]"));
        assertTrue(stats.contains("sys=AS"));
        assertTrue(stats.contains("type=CLI"));
        assertTrue(stats.contains("model=claude-haiku-4-5-20251001"));
        assertTrue(stats.contains("attempt=1"));
        assertTrue(stats.contains("retryReason=NONE"));
        assertTrue(stats.contains("in=1500"));
        assertTrue(stats.contains("out=750"));
        assertTrue(stats.contains("cache_read=500"));
        assertTrue(stats.contains("cache_write=300"));
        assertTrue(stats.contains("cache_hit=25%"));
        assertTrue(stats.contains("result=OK"));
        assertTrue(stats.contains("duration_ms=2500"));
        assertTrue(stats.contains("corrId=corr-123"));
    }

    @Test
    void buildWithZeroTokens() {
        String stats = new LlmStatsLogger("API", "claude-opus-4", "uuid-456")
            .attempt(2)
            .retryReason("TIMEOUT")
            .tokens(0, 0)
            .cacheTokens(0, 0)
            .cacheHitPercent(0)
            .result("FAIL")
            .duration(120000L)
            .build();

        assertTrue(stats.contains("in=0"));
        assertTrue(stats.contains("out=0"));
        assertTrue(stats.contains("cache_read=0"));
        assertTrue(stats.contains("cache_write=0"));
        assertTrue(stats.contains("cache_hit=0%"));
        assertTrue(stats.contains("result=FAIL"));
        assertTrue(stats.contains("attempt=2"));
        assertTrue(stats.contains("retryReason=TIMEOUT"));
    }

    @Test
    void buildDefaultsWhenUnset() {
        String stats = new LlmStatsLogger("CLI", "haiku", "id").build();
        assertTrue(stats.contains("sys=AS"));
        assertTrue(stats.contains("attempt=1"));
        assertTrue(stats.contains("retryReason=NONE"));
        assertTrue(stats.contains("result=OK"));
        assertTrue(stats.contains("in=0"));
        assertTrue(stats.contains("out=0"));
    }

    @Test
    void timestampFormattingIsISO8601() {
        String stats = new LlmStatsLogger("CLI", "model", "id").build();
        // Should contain ts=YYYY-MM-DDTHH:mm:ssZ
        assertTrue(stats.matches(".*ts=\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z.*"));
    }

    @Test
    void cacheHitPercentEdgeCases() {
        // 100% cache hit
        String stats100 = new LlmStatsLogger("CLI", "model", "id")
            .tokens(0, 100)
            .cacheTokens(1000, 0)
            .cacheHitPercent(100)
            .build();
        assertTrue(stats100.contains("cache_hit=100%"));

        // 0% cache hit
        String stats0 = new LlmStatsLogger("CLI", "model", "id")
            .tokens(1000, 100)
            .cacheTokens(0, 0)
            .cacheHitPercent(0)
            .build();
        assertTrue(stats0.contains("cache_hit=0%"));
    }

    @Test
    void nullRetryReasonDefaultsToNONE() {
        String stats = new LlmStatsLogger("CLI", "model", "id")
            .retryReason(null)
            .build();
        assertTrue(stats.contains("retryReason=NONE"));
    }

    @Test
    void nullResultDefaultsToOK() {
        String stats = new LlmStatsLogger("CLI", "model", "id")
            .result(null)
            .build();
        assertTrue(stats.contains("result=OK"));
    }
}
