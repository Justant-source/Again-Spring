package com.againspring.aiuser.orchestrator.service.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmStatsLoggerTest {

    @Test
    void testBuildFormatCompleteness() {
        LlmStatsLogger logger = new LlmStatsLogger("POST", "claude-haiku-4-5-20251001", "test-corr-id");
        logger.tokens(100, 50)
            .cacheTokens(10, 20)
            .cacheHitPercent(5)
            .attempt(1)
            .retryReason("NONE")
            .result("OK")
            .duration(120);

        String logLine = logger.build();

        // Validate format structure
        assertTrue(logLine.startsWith("[LLMSTATS]"));
        assertTrue(logLine.contains("ts="), "Missing ts field");
        assertTrue(logLine.contains("sys=AS"), "Missing sys field");
        assertTrue(logLine.contains("type=POST"), "Missing type field");
        assertTrue(logLine.contains("model=claude-haiku-4-5-20251001"), "Missing model field");
        assertTrue(logLine.contains("attempt=1"), "Missing attempt field");
        assertTrue(logLine.contains("retryReason=NONE"), "Missing retryReason field");
        assertTrue(logLine.contains("in=100"), "Missing in tokens");
        assertTrue(logLine.contains("out=50"), "Missing out tokens");
        assertTrue(logLine.contains("cache_read=10"), "Missing cache_read");
        assertTrue(logLine.contains("cache_write=20"), "Missing cache_write");
        assertTrue(logLine.contains("cache_hit=5%"), "Missing cache_hit");
        assertTrue(logLine.contains("result=OK"), "Missing result field");
        assertTrue(logLine.contains("duration_ms=120"), "Missing duration_ms");
        assertTrue(logLine.contains("corrId=test-corr-id"), "Missing corrId field");
    }

    @Test
    void testRetryReasonSetting() {
        LlmStatsLogger logger = new LlmStatsLogger("COMMENT", "claude-haiku-4-5-20251001", "corr-123");
        logger.attempt(2)
            .retryReason("PARSE_FAIL")
            .result("RETRY")
            .tokens(80, 40)
            .duration(250);

        String logLine = logger.build();
        assertTrue(logLine.contains("retryReason=PARSE_FAIL"));
        assertTrue(logLine.contains("attempt=2"));
        assertTrue(logLine.contains("result=RETRY"));
    }

    @Test
    void testDefaultValues() {
        LlmStatsLogger logger = new LlmStatsLogger("CRITIQUE", "claude-sonnet-5", "corr-456");
        String logLine = logger.build();

        assertTrue(logLine.contains("retryReason=NONE"), "Default retryReason should be NONE");
        assertTrue(logLine.contains("result=OK"), "Default result should be OK");
        assertTrue(logLine.contains("sys=AS"), "sys should always be AS");
    }

    @Test
    void testSingleLineFormat() {
        LlmStatsLogger logger = new LlmStatsLogger("PROOFREAD", "claude-haiku-4-5-20251001", "corr-789");
        logger.tokens(150, 75)
            .cacheTokens(5, 10)
            .cacheHitPercent(2)
            .attempt(1)
            .retryReason("NONE")
            .result("OK")
            .duration(300);

        String logLine = logger.build();

        // Ensure no newlines (single-line format for grep)
        assertFalse(logLine.contains("\n"), "Log line should not contain newlines");
        assertFalse(logLine.contains("\r"), "Log line should not contain carriage returns");

        // Verify tab-safety (no tabs either for clean parsing)
        assertFalse(logLine.contains("\t"), "Log line should not contain tabs");
    }

    @Test
    void testZeroTokens() {
        LlmStatsLogger logger = new LlmStatsLogger("API", "claude-haiku-4-5-20251001", "corr-zero");
        logger.tokens(0, 0)
            .cacheTokens(0, 0)
            .cacheHitPercent(0)
            .result("FAIL")
            .duration(100);

        String logLine = logger.build();
        assertTrue(logLine.contains("in=0"));
        assertTrue(logLine.contains("out=0"));
        assertTrue(logLine.contains("cache_read=0"));
        assertTrue(logLine.contains("cache_write=0"));
    }
}
