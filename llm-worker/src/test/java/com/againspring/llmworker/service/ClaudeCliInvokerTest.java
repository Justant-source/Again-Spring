package com.againspring.llmworker.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeCliInvokerTest {

    @Test
    void llmStatsLoggerBuildsCorrectFormat() {
        String corrId = "test-corr-id-123";
        String stats = new LlmStatsLogger("INVOKE", "claude-haiku-4-5-20251001", corrId)
            .attempt(1)
            .retryReason("NONE")
            .tokens(1000, 500)
            .cacheTokens(100, 50)
            .cacheHitPercent(20)
            .result("OK")
            .duration(1234)
            .build();

        assertTrue(stats.startsWith("[LLMSTATS]"));
        assertTrue(stats.contains("sys=AS"));
        assertTrue(stats.contains("type=INVOKE"));
        assertTrue(stats.contains("model=claude-haiku-4-5-20251001"));
        assertTrue(stats.contains("attempt=1"));
        assertTrue(stats.contains("retryReason=NONE"));
        assertTrue(stats.contains("in=1000"));
        assertTrue(stats.contains("out=500"));
        assertTrue(stats.contains("cache_read=100"));
        assertTrue(stats.contains("cache_write=50"));
        assertTrue(stats.contains("cache_hit=20%"));
        assertTrue(stats.contains("result=OK"));
        assertTrue(stats.contains("duration_ms=1234"));
        assertTrue(stats.contains("corrId=test-corr-id-123"));
    }

    @Test
    void llmStatsLoggerWithFailure() {
        String stats = new LlmStatsLogger("INVOKE", "claude-sonnet-4-6", "fail-id")
            .attempt(2)
            .retryReason("TIMEOUT")
            .tokens(500, 0)
            .cacheTokens(0, 0)
            .cacheHitPercent(0)
            .result("FAIL")
            .duration(5000)
            .build();

        assertTrue(stats.contains("attempt=2"));
        assertTrue(stats.contains("retryReason=TIMEOUT"));
        assertTrue(stats.contains("result=FAIL"));
        assertTrue(stats.contains("in=500"));
        assertTrue(stats.contains("out=0"));
    }

    @Test
    void llmStatsLoggerDefaultsRetryReasonToNone() {
        String stats = new LlmStatsLogger("INVOKE", "test-model", "test-id")
            .build();

        assertTrue(stats.contains("retryReason=NONE"));
    }

    @Test
    void llmStatsLoggerDefaultsResultToOk() {
        String stats = new LlmStatsLogger("INVOKE", "test-model", "test-id")
            .build();

        assertTrue(stats.contains("result=OK"));
    }

    @Test
    void llmStatsLoggerTimestampIsISO8601() {
        String stats = new LlmStatsLogger("INVOKE", "test-model", "test-id").build();

        // Check for ISO8601 format: YYYY-MM-DDTHH:MM:SSZ
        String tsPattern = "ts=\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";
        assertTrue(stats.matches(".*" + tsPattern + ".*"),
            "Timestamp should be in ISO8601 format. Got: " + stats);
    }

    @Test
    void llmStatsLoggerMaintainsCacheHitPercentWithZeroDenominator() {
        String stats = new LlmStatsLogger("INVOKE", "test-model", "test-id")
            .tokens(0, 0)
            .cacheTokens(0, 0)
            .cacheHitPercent(0)
            .build();

        assertTrue(stats.contains("cache_hit=0%"));
    }

    @Test
    void buildCommandIncludesDisallowedToolsFlag() {
        List<String> command = ClaudeCliInvoker.buildCommand(
            "claude",
            "claude-haiku-4-5-20251001",
            "test system prompt"
        );

        int disallowedToolsIdx = command.indexOf("--disallowedTools");
        assertTrue(disallowedToolsIdx >= 0, "Command should contain --disallowedTools flag");

        // Verify "*" is the argument immediately following --disallowedTools
        assertTrue(disallowedToolsIdx + 1 < command.size(),
            "There should be an argument after --disallowedTools");
        assertEquals("*", command.get(disallowedToolsIdx + 1),
            "--disallowedTools should be followed by '*'");
    }

    @Test
    void buildCommandIncludesRequiredFlags() {
        List<String> command = ClaudeCliInvoker.buildCommand(
            "claude",
            "claude-haiku-4-5-20251001",
            "test system prompt"
        );

        assertTrue(command.contains("--print"));
        assertTrue(command.contains("--output-format"));
        assertTrue(command.contains("stream-json"));
        assertTrue(command.contains("--verbose"));
        assertTrue(command.contains("--include-partial-messages"));
        assertTrue(command.contains("--model"));
        assertTrue(command.contains("claude-haiku-4-5-20251001"));
        assertTrue(command.contains("--strict-mcp-config"));
        assertTrue(command.contains("--no-session-persistence"));
        assertTrue(command.contains("--system-prompt"));
        assertTrue(command.contains("test system prompt"));
    }

    @Test
    void buildCommandStartsWithBinaryPath() {
        List<String> command = ClaudeCliInvoker.buildCommand(
            "/custom/path/claude",
            "claude-haiku-4-5-20251001",
            "test system prompt"
        );

        assertEquals("/custom/path/claude", command.get(0));
    }

    @Test
    void buildCommandOrderIsCorrect() {
        List<String> command = ClaudeCliInvoker.buildCommand(
            "claude",
            "claude-haiku-4-5-20251001",
            "test system prompt"
        );

        // Verify the command structure: --disallowedTools must come before --system-prompt
        int disallowedToolsIdx = command.indexOf("--disallowedTools");
        int systemPromptIdx = command.indexOf("--system-prompt");
        assertTrue(disallowedToolsIdx < systemPromptIdx,
            "--disallowedTools should appear before --system-prompt");
    }
}
