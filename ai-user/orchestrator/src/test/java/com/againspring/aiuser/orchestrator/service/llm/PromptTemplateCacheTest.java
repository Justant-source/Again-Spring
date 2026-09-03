package com.againspring.aiuser.orchestrator.service.llm;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PromptTemplateCacheTest {

    @Test
    void cachesForFiveMinutesThenRefreshes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class)))
            .thenReturn(List.of(Map.entry("voice/post", "v1")))
            .thenReturn(List.of(Map.entry("voice/post", "v2")));
        Instant t0 = Instant.parse("2026-09-03T00:00:00Z");
        MutableClock clock = new MutableClock(t0);
        PromptTemplateCache cache = new PromptTemplateCache(jdbc, clock);

        assertEquals("v1", cache.overrides().get("voice/post"));
        clock.advance(Duration.ofMinutes(4));
        assertEquals("v1", cache.overrides().get("voice/post"));
        clock.advance(Duration.ofMinutes(2));
        assertEquals("v2", cache.overrides().get("voice/post"));
        verify(jdbc, times(2)).query(anyString(), any(RowMapper.class));
    }

    @Test
    void dbFailureKeepsLastGood() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class)))
            .thenReturn(List.of(Map.entry("voice/post", "v1")))
            .thenThrow(new RuntimeException("db down"));
        MutableClock clock = new MutableClock(Instant.parse("2026-09-03T00:00:00Z"));
        PromptTemplateCache cache = new PromptTemplateCache(jdbc, clock);
        assertEquals("v1", cache.overrides().get("voice/post"));
        clock.advance(Duration.ofMinutes(6));
        assertEquals("v1", cache.overrides().get("voice/post"));
    }

    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }
}
