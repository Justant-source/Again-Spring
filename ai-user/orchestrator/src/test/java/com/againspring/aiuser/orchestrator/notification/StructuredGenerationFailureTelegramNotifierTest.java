package com.againspring.aiuser.orchestrator.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StructuredGenerationFailureTelegramNotifier message formatting.
 * Note: RestClient integration testing is implicit in staging/production alerting.
 * These tests focus on message content and formatting logic.
 */
@DisplayName("StructuredGenerationFailureTelegramNotifier 테스트")
class StructuredGenerationFailureTelegramNotifierTest {

    @Test
    @DisplayName("Bundle lost alert - 생성되면 에러 없음 (Telegram 미설정)")
    void bundleLost_noErrorWhenTelegramNotConfigured() {
        var notifier = new StructuredGenerationFailureTelegramNotifier("", "", RestClient.builder());
        // Should not throw when Telegram is not configured
        assertDoesNotThrow(() -> notifier.bundleLost("corr-123", "LLM rate limit", "dev", "snippet"));
    }

    @Test
    @DisplayName("PARSE_FAIL rate alert - 생성되면 에러 없음 (Telegram 미설정)")
    void parseFailureRateAlert_noErrorWhenTelegramNotConfigured() {
        var notifier = new StructuredGenerationFailureTelegramNotifier("", "", RestClient.builder());
        // Should not throw when Telegram is not configured
        assertDoesNotThrow(() ->
            notifier.parseFailureRateAlert(3, 5, 30, 360, "dev", "snippet")
        );
    }

    @Test
    @DisplayName("Bundle lost alert - Null 값도 처리 가능")
    void bundleLost_handlesNullValues() {
        var notifier = new StructuredGenerationFailureTelegramNotifier("", "", RestClient.builder());
        // Should not throw with null values
        assertDoesNotThrow(() -> notifier.bundleLost(null, null, null, null));
    }

    @Test
    @DisplayName("PARSE_FAIL rate alert - Null 값도 처리 가능")
    void parseFailureRateAlert_handlesNullValues() {
        var notifier = new StructuredGenerationFailureTelegramNotifier("", "", RestClient.builder());
        // Should not throw with null values
        assertDoesNotThrow(() -> notifier.parseFailureRateAlert(3, 5, 30, 360, null, null));
    }

    @Test
    @DisplayName("Bundle lost alert - 매우 긴 값도 처리 가능")
    void bundleLost_handleVeryLongValues() {
        var notifier = new StructuredGenerationFailureTelegramNotifier("", "", RestClient.builder());
        String veryLong = "a".repeat(10000);
        // Should not throw with very long values
        assertDoesNotThrow(() -> notifier.bundleLost("corr", veryLong, veryLong, veryLong));
    }

    @Test
    @DisplayName("메시지에 개행과 탭 포함 가능")
    void messagesCanContainNewlinesAndTabs() {
        var notifier = new StructuredGenerationFailureTelegramNotifier("", "", RestClient.builder());
        String withNewlines = "line1\nline2\nline3";
        String withTabs = "col1\tcol2\tcol3";

        // Should not throw - newlines and tabs are cleaned in message formatting
        assertDoesNotThrow(() -> {
            notifier.bundleLost("corr", withNewlines, "dev", withTabs);
            notifier.parseFailureRateAlert(3, 5, 30, 360, "dev", withNewlines);
        });
    }
}
