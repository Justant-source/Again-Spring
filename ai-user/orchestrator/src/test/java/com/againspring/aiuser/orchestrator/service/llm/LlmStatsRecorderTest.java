package com.againspring.aiuser.orchestrator.service.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmStatsRecorderTest {

    private LlmStatsRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new LlmStatsRecorder();
        recorder.reset();
    }

    @Test
    void testRecordSingleCall() {
        recorder.recordCall("POST", 100, 50, 10, 5, "OK", "NONE");

        var stats = recorder.getTodayStats();
        assertNotNull(stats);
        assertTrue(stats.containsKey("POST"));

        LlmStatsRecorder.DayStats postStats = stats.get("POST");
        assertEquals(1, postStats.getTotalCalls());
        assertEquals(0, postStats.getTotalRetries());
        assertEquals(100, postStats.getTotalInputTokens());
        assertEquals(50, postStats.getTotalOutputTokens());
        assertEquals(10, postStats.getTotalCacheRead());
        assertEquals(5, postStats.getTotalCacheWrite());
        assertTrue(postStats.getResultCounts().containsKey("OK"));
        assertEquals(1, postStats.getResultCounts().get("OK"));
    }

    @Test
    void testRetryReasonCounting() {
        recorder.recordCall("COMMENT", 80, 40, 5, 2, "RETRY", "PARSE_FAIL");
        recorder.recordCall("COMMENT", 85, 42, 6, 3, "RETRY", "PARSE_FAIL");
        recorder.recordCall("COMMENT", 90, 45, 7, 4, "OK", "NONE");

        var stats = recorder.getTodayStats();
        LlmStatsRecorder.DayStats commentStats = stats.get("COMMENT");

        assertEquals(3, commentStats.getTotalCalls());
        assertEquals(2, commentStats.getTotalRetries());
        assertEquals(2, commentStats.getRetryReasons().get("PARSE_FAIL"));
        assertEquals(2, commentStats.getResultCounts().get("RETRY"));
        assertEquals(1, commentStats.getResultCounts().get("OK"));
    }

    @Test
    void testMultipleRetryReasons() {
        recorder.recordCall("REPLY", 60, 30, 2, 1, "RETRY", "PARSE_FAIL");
        recorder.recordCall("REPLY", 65, 32, 2, 1, "RETRY", "EMPTY_RESULT");
        recorder.recordCall("REPLY", 70, 35, 3, 2, "RETRY", "PROVIDER_ERROR");
        recorder.recordCall("REPLY", 75, 37, 3, 2, "OK", "NONE");

        var stats = recorder.getTodayStats();
        LlmStatsRecorder.DayStats replyStats = stats.get("REPLY");

        assertEquals(4, replyStats.getTotalCalls());
        assertEquals(3, replyStats.getTotalRetries());
        assertEquals(1, replyStats.getRetryReasons().get("PARSE_FAIL"));
        assertEquals(1, replyStats.getRetryReasons().get("EMPTY_RESULT"));
        assertEquals(1, replyStats.getRetryReasons().get("PROVIDER_ERROR"));
    }

    @Test
    void testMultipleTypes() {
        recorder.recordCall("POST", 100, 50, 10, 5, "OK", "NONE");
        recorder.recordCall("COMMENT", 80, 40, 5, 2, "OK", "NONE");
        recorder.recordCall("CRITIQUE", 120, 60, 15, 8, "OK", "NONE");

        var stats = recorder.getTodayStats();
        assertEquals(3, stats.size());
        assertTrue(stats.containsKey("POST"));
        assertTrue(stats.containsKey("COMMENT"));
        assertTrue(stats.containsKey("CRITIQUE"));

        assertEquals(100, stats.get("POST").getTotalInputTokens());
        assertEquals(80, stats.get("COMMENT").getTotalInputTokens());
        assertEquals(120, stats.get("CRITIQUE").getTotalInputTokens());
    }

    @Test
    void testTokenAccumulation() {
        recorder.recordCall("POST", 100, 50, 10, 5, "OK", "NONE");
        recorder.recordCall("POST", 150, 75, 15, 8, "OK", "NONE");
        recorder.recordCall("POST", 200, 100, 20, 10, "OK", "NONE");

        var stats = recorder.getTodayStats();
        LlmStatsRecorder.DayStats postStats = stats.get("POST");

        assertEquals(3, postStats.getTotalCalls());
        assertEquals(450, postStats.getTotalInputTokens());  // 100 + 150 + 200
        assertEquals(225, postStats.getTotalOutputTokens()); // 50 + 75 + 100
        assertEquals(45, postStats.getTotalCacheRead());     // 10 + 15 + 20
        assertEquals(23, postStats.getTotalCacheWrite());    // 5 + 8 + 10
    }

    @Test
    void testResultDistribution() {
        recorder.recordCall("POST", 100, 50, 10, 5, "OK", "NONE");
        recorder.recordCall("POST", 100, 50, 10, 5, "OK", "NONE");
        recorder.recordCall("POST", 100, 50, 10, 5, "RETRY", "PARSE_FAIL");
        recorder.recordCall("POST", 100, 50, 10, 5, "FAIL", "CRITIQUE_FAIL");

        var stats = recorder.getTodayStats();
        LlmStatsRecorder.DayStats postStats = stats.get("POST");

        assertEquals(4, postStats.getTotalCalls());
        assertEquals(2, postStats.getResultCounts().get("OK"));
        assertEquals(1, postStats.getResultCounts().get("RETRY"));
        assertEquals(1, postStats.getResultCounts().get("FAIL"));
    }

    @Test
    void testGetStatsForType() {
        recorder.recordCall("POST", 100, 50, 10, 5, "OK", "NONE");
        recorder.recordCall("COMMENT", 80, 40, 5, 2, "OK", "NONE");

        LlmStatsRecorder.DayStats postStats = recorder.getStatsForType("POST");
        LlmStatsRecorder.DayStats commentStats = recorder.getStatsForType("COMMENT");
        LlmStatsRecorder.DayStats unknownStats = recorder.getStatsForType("UNKNOWN");

        assertEquals(1, postStats.getTotalCalls());
        assertEquals(1, commentStats.getTotalCalls());
        assertEquals(0, unknownStats.getTotalCalls());
    }

    @Test
    void testNoneRetryReasonNotCounted() {
        recorder.recordCall("POST", 100, 50, 10, 5, "OK", "NONE");
        recorder.recordCall("POST", 100, 50, 10, 5, "OK", "NONE");

        var stats = recorder.getTodayStats();
        LlmStatsRecorder.DayStats postStats = stats.get("POST");

        assertEquals(2, postStats.getTotalCalls());
        assertEquals(0, postStats.getTotalRetries());
        assertFalse(postStats.getRetryReasons().containsKey("NONE"));
    }
}
