package com.againspring.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SyntheticViewReconcileServiceTest {

    @Test
    void insertsDeficitViewsAndUpdatesCount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // post p1: 현재 view_count=10, 목표 30 → 20건 삽입
        when(jdbc.query(anyString(), any(RowMapper.class)))
            .thenReturn(List.of(new SyntheticViewReconcileService.Candidate("p1", 10, 30)));
        when(jdbc.batchUpdate(anyString(), anyList())).thenReturn(new int[20]);
        when(jdbc.update(anyString(), eq(30), eq("p1"))).thenReturn(1);

        var r = new SyntheticViewReconcileService(jdbc).reconcile();
        assertEquals(1, r.updated());
        assertEquals(20, r.viewsInserted());
        verify(jdbc).batchUpdate(contains("INSERT INTO post_views"), argThat((List<Object[]> l) -> l.size() == 20));
        verify(jdbc).update(contains("UPDATE posts SET view_count"), eq(30), eq("p1"));
    }

    @Test
    void skipsWhenTargetNotAboveCurrent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class)))
            .thenReturn(List.of(new SyntheticViewReconcileService.Candidate("p2", 50, 30)));
        var r = new SyntheticViewReconcileService(jdbc).reconcile();
        assertEquals(0, r.updated());
        verify(jdbc, never()).batchUpdate(anyString(), anyList());
    }
}
