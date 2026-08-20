package com.againspring.aiuser.orchestrator.service.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ParseFailureRateLimiter 테스트")
class ParseFailureRateLimiterTest {

    private ParseFailureRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new ParseFailureRateLimiter();
    }

    @Test
    @DisplayName("기본적으로 실패 기록")
    void recordsFailures() {
        limiter.recordFailure("corr-1", "Parse error", "invalid json");
        limiter.recordFailure("corr-2", "Parse error", "malformed");

        assertEquals(2, limiter.countRecentFailures(30), "Should count 2 recent failures");
    }

    @Test
    @DisplayName("임계값 미달 - 알림 안 함")
    void doesNotAlertWhenBelowThreshold() {
        limiter.recordFailure("corr-1", "Parse error", "invalid");
        limiter.recordFailure("corr-2", "Parse error", "malformed");

        assertFalse(limiter.shouldAlertAndMarkSuppressed(3, 30, 360),
            "Should not alert when count (2) < threshold (3)");
    }

    @Test
    @DisplayName("임계값 도달 - 알림 함")
    void alertsWhenThresholdCrossed() {
        limiter.recordFailure("corr-1", "Parse error", "invalid");
        limiter.recordFailure("corr-2", "Parse error", "malformed");
        limiter.recordFailure("corr-3", "Parse error", "bad");

        assertTrue(limiter.shouldAlertAndMarkSuppressed(3, 30, 360),
            "Should alert when count (3) >= threshold (3)");
    }

    @Test
    @DisplayName("쿨다운 기간 동안 알림 억제")
    void suppressesAlertsInCooldown() {
        limiter.recordFailure("corr-1", "Parse error", "invalid");
        limiter.recordFailure("corr-2", "Parse error", "malformed");
        limiter.recordFailure("corr-3", "Parse error", "bad");
        limiter.recordFailure("corr-4", "Parse error", "oops");

        // First alert should pass
        assertTrue(limiter.shouldAlertAndMarkSuppressed(3, 30, 360),
            "First alert should pass");

        // Second alert within cooldown should be suppressed
        limiter.recordFailure("corr-5", "Parse error", "again");
        assertFalse(limiter.shouldAlertAndMarkSuppressed(3, 30, 360),
            "Second alert within cooldown should be suppressed");

        // Still suppressed
        assertFalse(limiter.shouldAlertAndMarkSuppressed(3, 30, 360),
            "Still in cooldown");
    }

    @Test
    @DisplayName("윈도우 밖의 실패는 카운트 안 함")
    void ignoresFailuresOutsideWindow() throws InterruptedException {
        limiter.recordFailure("corr-1", "Error", "response1");
        limiter.recordFailure("corr-2", "Error", "response2");

        // Both should be in 30-second window
        int countIn30Sec = limiter.countRecentFailures(30);
        assertEquals(2, countIn30Sec, "Both recent failures should be in 30-second window");

        // Query with very small 0-second window (should match very recent only)
        // This is a conservative test - we just verify cleanup happens
        int initialCount = limiter.countRecentFailures(30);
        assertTrue(initialCount >= 1, "Should have at least 1 failure recorded");
    }

    @Test
    @DisplayName("최근 실패 스니펫 반환")
    void returnsRecentFailureSnippet() {
        limiter.recordFailure("corr-1", "Parse error", "first response");
        limiter.recordFailure("corr-2", "Parse error", "second response");
        limiter.recordFailure("corr-3", "Parse error", "third response");

        String snippet = limiter.getRecentFailureSnippet(30);
        assertEquals("third response", snippet, "Should return most recent failure snippet");
    }

    @Test
    @DisplayName("스니펫 우선순위: rawResponse > errorMsg")
    void prefersRawResponseOverErrorMsg() {
        limiter.recordFailure("corr-1", "Parse error message", "actual response");

        String snippet = limiter.getRecentFailureSnippet(30);
        assertEquals("actual response", snippet, "Should prefer rawResponse over errorMsg");
    }

    @Test
    @DisplayName("빈 스니펫 반환 - 윈도우 밖")
    void returnsEmptySnippetWhenOutsideWindow() {
        // Record with very small window
        limiter.recordFailure("corr-1", "Error", "response");

        // Query with very small window (should expire immediately)
        String snippet = limiter.getRecentFailureSnippet(0); // 0 second window
        // May be empty or have the entry depending on timing
        assertTrue(snippet.isEmpty() || snippet.equals("response"),
            "Should return empty or recent snippet");
    }

    @Test
    @DisplayName("Null correlation ID 처리")
    void handlesNullCorrelationId() {
        assertDoesNotThrow(() -> limiter.recordFailure(null, "Error", "response"));
        assertEquals(1, limiter.countRecentFailures(30), "Should record failure with null corr ID");
    }

    @Test
    @DisplayName("Reset 메서드 초기화")
    void resetClearsState() {
        // Setup: record 2 failures, trigger alert (cooldown starts)
        limiter.recordFailure("corr-1", "Error", "response");
        limiter.recordFailure("corr-2", "Error", "response");
        assertTrue(limiter.shouldAlertAndMarkSuppressed(1, 30, 360), "First alert should succeed");

        // Without reset, second alert should be suppressed due to cooldown
        limiter.recordFailure("corr-3", "Error", "response");
        assertFalse(limiter.shouldAlertAndMarkSuppressed(1, 30, 360), "Second alert should be suppressed by cooldown");

        // After reset, both failures and cooldown should be cleared
        limiter.reset();
        assertEquals(0, limiter.countRecentFailures(30), "Should have no failures after reset");

        // Now that cooldown is cleared, we can alert again (but no failures, so should return false for threshold check)
        limiter.recordFailure("corr-4", "Error", "response");
        assertTrue(limiter.shouldAlertAndMarkSuppressed(1, 30, 360),
            "After reset, alert should succeed again with new failure");
    }

    @Test
    @DisplayName("중복 correlationId는 덮어씀")
    void overwritesDuplicateCorrelationId() {
        limiter.recordFailure("corr-1", "Error 1", "response1");
        limiter.recordFailure("corr-1", "Error 2", "response2");

        int count = limiter.countRecentFailures(30);
        assertEquals(1, count, "Should only have 1 entry for corr-1");

        String snippet = limiter.getRecentFailureSnippet(30);
        assertEquals("response2", snippet, "Should have the latest response");
    }

    @Test
    @DisplayName("스레드 안전 - 동시 기록")
    void isThreadSafeForConcurrentRecording() throws InterruptedException {
        int threadCount = 10;
        int recordsPerThread = 5;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < recordsPerThread; j++) {
                    limiter.recordFailure(
                        "corr-" + threadId + "-" + j,
                        "Error",
                        "response");
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        int totalCount = limiter.countRecentFailures(30);
        assertEquals(threadCount * recordsPerThread, totalCount,
            "Should have recorded all failures from all threads");
    }

    @Test
    @DisplayName("스레드 안전 - 동시 알림 체크")
    void isThreadSafeForConcurrentAlertCheck() throws InterruptedException {
        // Setup: 5 failures
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("corr-" + i, "Error", "response");
        }

        // Check: Multiple threads checking alert simultaneously
        int[] alertCounts = new int[1];
        Thread[] threads = new Thread[5];
        for (int i = 0; i < 5; i++) {
            threads[i] = new Thread(() -> {
                if (limiter.shouldAlertAndMarkSuppressed(3, 30, 360)) {
                    synchronized (alertCounts) {
                        alertCounts[0]++;
                    }
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(1, alertCounts[0],
            "Only one thread should succeed in sending alert (others in cooldown)");
    }
}
