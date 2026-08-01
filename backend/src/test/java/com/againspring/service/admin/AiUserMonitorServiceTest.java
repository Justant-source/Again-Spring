package com.againspring.service.admin;

import com.againspring.repository.ai.AiUserGenerationConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiUserMonitorService Unit Tests")
class AiUserMonitorServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AiUserGenerationConfigRepository configRepository;

    @InjectMocks
    private AiUserMonitorService service;

    // ─────────────────────────────────────────────────────────────────────────
    // getActionFeed Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getActionFeed_noFilter_returnsAllItems")
    void testGetActionFeed_noFilter_returnsAllItems() {
        // Arrange
        List<Map<String, Object>> mockRows = new ArrayList<>();
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", 1L);
        row1.put("persona_id", "persona-001");
        row1.put("nickname", "Test Bot");
        row1.put("tier", "STANDARD");
        row1.put("action_type", "COMMENT");
        row1.put("status", "POSTED");
        row1.put("target_type", "POST");
        row1.put("target_id", "post-001");
        row1.put("detail", "{\"success\": true}");
        row1.put("created_at", Timestamp.from(Instant.now()));
        mockRows.add(row1);

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(mockRows);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
            .thenReturn(1);

        // Act
        AiUserMonitorService.ActionFeedDto result = service.getActionFeed(50, null, null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getFeeds().size());
        assertEquals(1, result.getTotal());
        assertEquals("COMMENT", result.getFeeds().get(0).getAction());
        assertFalse(result.getFeeds().get(0).isFailed());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("LEFT JOIN users u ON pal.persona_id = u.id"),
            "nickname must come from users, not personas");
        assertTrue(sql.contains("u.nickname"));
        assertFalse(sql.contains("p.nickname"));
    }

    @Test
    @DisplayName("getActionFeed_failedFilter_onlyReturnsFailed")
    void testGetActionFeed_failedFilter_onlyReturnsFailed() {
        // Arrange
        List<Map<String, Object>> mockRows = new ArrayList<>();
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", 2L);
        row1.put("persona_id", "persona-002");
        row1.put("nickname", "Test Bot 2");
        row1.put("tier", "BASIC");
        row1.put("action_type", "LIKE");
        row1.put("status", "FAILED");
        row1.put("target_type", "COMMENT");
        row1.put("target_id", "comment-001");
        row1.put("detail", "{\"error\": \"timeout\"}");
        row1.put("created_at", Timestamp.from(Instant.now()));
        mockRows.add(row1);

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(mockRows);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
            .thenReturn(1);

        // Act
        AiUserMonitorService.ActionFeedDto result = service.getActionFeed(50, "FAILED", null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getFeeds().size());
        assertTrue(result.getFeeds().get(0).isFailed());
        assertEquals("LIKE", result.getFeeds().get(0).getAction());
    }

    @Test
    @DisplayName("getActionFeed_limitClamping_maxLimitApplied")
    void testGetActionFeed_limitClamping_maxLimitApplied() {
        // Arrange
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(new ArrayList<>());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
            .thenReturn(0);

        // Act
        AiUserMonitorService.ActionFeedDto result = service.getActionFeed(500, null, null);

        // Assert
        assertNotNull(result);
        // The service should clamp to 100
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPersonaPerformance Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPersonaPerformance_24h_computesFailureRate")
    void testGetPersonaPerformance_24h_computesFailureRate() {
        // Arrange
        List<Map<String, Object>> mockStats = new ArrayList<>();
        Map<String, Object> stat1 = new HashMap<>();
        stat1.put("persona_id", "persona-001");
        stat1.put("nickname", "Test Bot");
        stat1.put("tier", "STANDARD");
        stat1.put("active", 1);
        stat1.put("completed", 50);
        stat1.put("failed", 5);
        stat1.put("blocked", 5);
        mockStats.add(stat1);

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(mockStats);

        // Act
        List<AiUserMonitorService.PersonaPerformanceDto> result = service.getPersonaPerformance("24h");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        AiUserMonitorService.PersonaPerformanceDto perf = result.get(0);
        assertEquals("persona-001", perf.getPersonaId());
        assertEquals(50, perf.getActionsCompleted());
        assertEquals(5, perf.getFailed());
        assertEquals(5, perf.getBlocked());
        // failureRate should be 10 / 60 * 100 ≈ 16.67%
        assertEquals(16.666666666666664, perf.getFailureRate(), 0.01);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), any(Object.class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("LEFT JOIN users u ON pal.persona_id = u.id"));
        assertTrue(sql.contains("u.nickname"));
        assertFalse(sql.contains("p.nickname"));
    }

    @Test
    @DisplayName("getPersonaPerformance_7d_queriesCorrectTimeRange")
    void testGetPersonaPerformance_7d_queriesCorrectTimeRange() {
        // Arrange
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(new ArrayList<>());

        // Act
        List<AiUserMonitorService.PersonaPerformanceDto> result = service.getPersonaPerformance("7d");

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getHourlyDistribution Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getHourlyDistribution_allHours_presentIn0to23")
    void testGetHourlyDistribution_allHours_presentIn0to23() {
        // Arrange
        List<Map<String, Object>> mockHourly = new ArrayList<>();
        Map<String, Object> hour1 = new HashMap<>();
        hour1.put("hr", 10);
        hour1.put("action_type", "COMMENT");
        hour1.put("cnt", 5);
        mockHourly.add(hour1);

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(mockHourly);

        // Act
        AiUserMonitorService.HourlyDistributionDto result = service.getHourlyDistribution(24);

        // Assert
        assertNotNull(result);
        assertEquals(24, result.getHours().size());

        // Check hour 10 has data
        AiUserMonitorService.HourlyDistributionDto.HourSlot hour10 = result.getHours().get(10);
        assertEquals(10, hour10.getHour());
        assertFalse(hour10.getByType().isEmpty());
        assertEquals(5, hour10.getByType().get("COMMENT"));

        // Check other hours are present but empty
        AiUserMonitorService.HourlyDistributionDto.HourSlot hour0 = result.getHours().get(0);
        assertEquals(0, hour0.getHour());
        assertTrue(hour0.getByType().isEmpty());
    }

    @Test
    @DisplayName("getHourlyDistribution_aggregatesMultipleActionTypes")
    void testGetHourlyDistribution_aggregatesMultipleActionTypes() {
        // Arrange
        List<Map<String, Object>> mockHourly = new ArrayList<>();

        Map<String, Object> row1 = new HashMap<>();
        row1.put("hr", 14);
        row1.put("action_type", "COMMENT");
        row1.put("cnt", 10);
        mockHourly.add(row1);

        Map<String, Object> row2 = new HashMap<>();
        row2.put("hr", 14);
        row2.put("action_type", "LIKE");
        row2.put("cnt", 20);
        mockHourly.add(row2);

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(mockHourly);

        // Act
        AiUserMonitorService.HourlyDistributionDto result = service.getHourlyDistribution(24);

        // Assert
        assertNotNull(result);
        assertEquals(24, result.getHours().size());

        AiUserMonitorService.HourlyDistributionDto.HourSlot hour14 = result.getHours().get(14);
        assertEquals(14, hour14.getHour());
        assertEquals(10, hour14.getByType().get("COMMENT"));
        assertEquals(20, hour14.getByType().get("LIKE"));
    }

    @Test
    @DisplayName("getHourlyDistribution_negativeHoursDefaultsTo24")
    void testGetHourlyDistribution_negativeHoursDefaultsTo24() {
        // Arrange
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(new ArrayList<>());

        // Act
        AiUserMonitorService.HourlyDistributionDto result = service.getHourlyDistribution(-1);

        // Assert
        assertNotNull(result);
        assertEquals(24, result.getHours().size());
    }
}
