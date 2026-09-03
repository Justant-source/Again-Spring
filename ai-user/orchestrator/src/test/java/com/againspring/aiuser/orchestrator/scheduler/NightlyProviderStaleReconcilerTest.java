package com.againspring.aiuser.orchestrator.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NightlyProviderStaleReconcilerTest {
    @Test
    void restoresWhenStale() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Integer.class))).thenReturn(1);
        new NightlyProviderStaleReconciler(jdbc).reconcile();
        verify(jdbc).update(contains("nightly-batch-stale-restore"));
        verify(jdbc).update(contains("restored_at=UTC_TIMESTAMP(3)"));
    }

    @Test
    void noopWhenNotStale() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Integer.class))).thenReturn(0);
        new NightlyProviderStaleReconciler(jdbc).reconcile();
        verify(jdbc, never()).update(anyString());
    }
}
