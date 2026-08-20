package com.againspring.aiuser.orchestrator.service.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class LlmCircuitBreakerTest {

    private LlmCircuitBreaker breaker;
    private MockClock mockClock;

    @BeforeEach
    void setUp() {
        mockClock = new MockClock();
        breaker = new LlmCircuitBreaker(3, mockClock);
    }

    @Test
    void testInitialStateClosed() {
        assertFalse(breaker.isOpen());
        assertEquals(LlmCircuitBreaker.State.CLOSED, breaker.getTelemetry().getState());
    }

    @Test
    void testThreeConsecutiveFailuresOpenCircuit() {
        breaker.recordFailure("PARSE_FAIL", "hash1");
        assertFalse(breaker.isOpen());

        breaker.recordFailure("PARSE_FAIL", "hash1");
        assertFalse(breaker.isOpen());

        breaker.recordFailure("PARSE_FAIL", "hash1");
        assertTrue(breaker.isOpen());
        assertEquals(LlmCircuitBreaker.State.OPEN, breaker.getTelemetry().getState());
    }

    @Test
    void testDifferentReasonResetsCounter() {
        breaker.recordFailure("PARSE_FAIL", "hash1");
        breaker.recordFailure("PARSE_FAIL", "hash1");
        breaker.recordFailure("TIMEOUT", "hash2");  // Different reason
        assertFalse(breaker.isOpen());
        assertEquals(1, breaker.getTelemetry().getConsecutiveFailures());
    }

    @Test
    void testSuccessResetsCounter() {
        breaker.recordFailure("PARSE_FAIL", "hash1");
        breaker.recordFailure("PARSE_FAIL", "hash1");
        breaker.recordSuccess();
        assertEquals(0, breaker.getTelemetry().getConsecutiveFailures());
    }

    @Test
    void testAutoResetAfter30Minutes() {
        // Open the circuit
        breaker.recordFailure("PARSE_FAIL", "hash1");
        breaker.recordFailure("PARSE_FAIL", "hash1");
        breaker.recordFailure("PARSE_FAIL", "hash1");
        assertTrue(breaker.isOpen());

        // Advance time less than 30 minutes
        mockClock.advanceSeconds(29 * 60);
        assertTrue(breaker.isOpen());

        // Advance past 30 minutes
        mockClock.advanceSeconds(2 * 60);  // Total 31 minutes
        assertFalse(breaker.isOpen());
        assertEquals(LlmCircuitBreaker.State.HALF_OPEN, breaker.getTelemetry().getState());
    }

    @Test
    void testPromptHashTracking() {
        breaker.recordFailure("PARSE_FAIL", "hash1");
        breaker.recordFailure("PARSE_FAIL", "hash2");
        breaker.recordFailure("PARSE_FAIL", "hash3");

        var telemetry = breaker.getTelemetry();
        assertTrue(telemetry.getRecentPromptHashes().contains("hash1"));
        assertTrue(telemetry.getRecentPromptHashes().contains("hash2"));
        assertTrue(telemetry.getRecentPromptHashes().contains("hash3"));
    }

    @Test
    void testPromptHashHistoryLimit() {
        // Record 6 failures with different hashes (limit is 5)
        for (int i = 1; i <= 6; i++) {
            breaker.recordFailure("PARSE_FAIL", "hash" + i);
        }

        var telemetry = breaker.getTelemetry();
        assertEquals(5, telemetry.getRecentPromptHashes().size());
        // Oldest hash should be gone
        assertFalse(telemetry.getRecentPromptHashes().contains("hash1"));
        assertTrue(telemetry.getRecentPromptHashes().contains("hash6"));
    }

    @Test
    void testTotalOpensCounter() {
        // First open
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure("REASON1", "hash");
        }
        assertEquals(1, breaker.getTelemetry().getTotalOpens());

        // Reset and open again
        breaker.reset();
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure("REASON2", "hash");
        }
        assertEquals(2, breaker.getTelemetry().getTotalOpens());
    }

    @Test
    void testReasonTracking() {
        breaker.recordFailure("PARSE_FAIL", "hash1");
        assertEquals("PARSE_FAIL", breaker.getTelemetry().getReason());
    }

    @Test
    void testReset() {
        breaker.recordFailure("PARSE_FAIL", "hash1");
        breaker.recordFailure("PARSE_FAIL", "hash1");
        breaker.recordFailure("PARSE_FAIL", "hash1");
        assertTrue(breaker.isOpen());

        breaker.reset();
        assertFalse(breaker.isOpen());
        assertEquals(LlmCircuitBreaker.State.CLOSED, breaker.getTelemetry().getState());
        assertEquals(0, breaker.getTelemetry().getConsecutiveFailures());
        assertNull(breaker.getTelemetry().getReason());
    }

    @Test
    void testCustomStrikeThreshold() {
        breaker = new LlmCircuitBreaker(2, mockClock);  // 2 strikes instead of 3
        breaker.recordFailure("PARSE_FAIL", "hash1");
        assertFalse(breaker.isOpen());
        breaker.recordFailure("PARSE_FAIL", "hash1");
        assertTrue(breaker.isOpen());
    }

    /**
     * Simple mock clock for testing time-based behavior.
     */
    static class MockClock extends Clock {
        private Instant now = Instant.parse("2026-08-20T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }
}
