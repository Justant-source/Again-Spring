package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderHealthRegistryTest {
    @Test
    void authDownExpiresAfterTtl() {
        Instant t0 = Instant.parse("2026-09-03T00:00:00Z");
        MutableClock clock = new MutableClock(t0);
        ProviderHealthRegistry reg = new ProviderHealthRegistry(10, clock);
        reg.markAuthDown(LlmProvider.CLAUDE, "Not logged in");
        assertEquals("AUTH_DOWN", state(reg, "claude"));
        clock.advance(Duration.ofMinutes(11));
        assertEquals("UP", state(reg, "claude"));
    }

    @Test
    void markUpClearsDown() {
        ProviderHealthRegistry reg = new ProviderHealthRegistry(10, Clock.systemUTC());
        reg.markAuthDown(LlmProvider.CODEX, "x");
        reg.markUp(LlmProvider.CODEX);
        assertEquals("UP", state(reg, "codex"));
        assertEquals("UP", state(reg, "stub"));
    }

    @SuppressWarnings("unchecked")
    private static String state(ProviderHealthRegistry reg, String key) {
        return (String) ((Map<String, Object>) reg.snapshot().get(key)).get("state");
    }

    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
