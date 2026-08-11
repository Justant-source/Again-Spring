package com.againspring.service.admin;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketingStatsService Unit Tests")
class MarketingStatsServiceTest {

    @Mock
    private MarketingJobRepository marketingJobRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private MarketingStatsService service;

    // ─────────────────────────────────────────────────────────────────────────
    // getPlatformPerformance Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPlatformPerformance_noJobs_returnsEmpty")
    void testGetPlatformPerformance_noJobs_returnsEmpty() {
        // Arrange
        when(marketingJobRepository.findByStatusIn(any()))
            .thenReturn(new ArrayList<>());

        // Act
        List<MarketingStatsService.PlatformStatsDto> result = service.getPlatformPerformance(30);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getPlatformPerformance_publishedJob_correctSuccessRate")
    void testGetPlatformPerformance_publishedJob_correctSuccessRate() throws Exception {
        // Arrange
        MarketingJob job = MarketingJob.builder()
            .id(1L)
            .postId("post-001")
            .status("PUBLISHED")
            .targets("[\"naver_blog\",\"x\"]")
            .publications("[{\"platform\":\"naver_blog\",\"state\":\"PUBLISHED\",\"url\":\"https://...\",\"publishedAt\":\"2026-06-10T10:00:00Z\"},{\"platform\":\"x\",\"state\":\"PUBLISHED\",\"url\":\"https://x.com/...\",\"publishedAt\":\"2026-06-10T10:05:00Z\"}]")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        List<MarketingJob> jobs = new ArrayList<>();
        jobs.add(job);

        when(marketingJobRepository.findByStatusIn(any()))
            .thenReturn(jobs);
        when(objectMapper.readValue(anyString(), (TypeReference<?>) any()))
            .thenAnswer(invocation -> {
                String json = invocation.getArgument(0);
                TypeReference<?> ref = invocation.getArgument(1);
                ObjectMapper realMapper = new ObjectMapper();
                return realMapper.readValue(json, ref);
            });

        // Act
        List<MarketingStatsService.PlatformStatsDto> result = service.getPlatformPerformance(30);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());

        // Check naver_blog
        MarketingStatsService.PlatformStatsDto naverStats = result.stream()
            .filter(p -> "naver_blog".equals(p.getPlatform()))
            .findFirst()
            .orElse(null);
        assertNotNull(naverStats);
        assertEquals(1, naverStats.getAttempted());
        assertEquals(1, naverStats.getPublished());
        assertEquals(100.0, naverStats.getSuccessRate());

        // Check x
        MarketingStatsService.PlatformStatsDto xStats = result.stream()
            .filter(p -> "x".equals(p.getPlatform()))
            .findFirst()
            .orElse(null);
        assertNotNull(xStats);
        assertEquals(1, xStats.getAttempted());
        assertEquals(1, xStats.getPublished());
        assertEquals(100.0, xStats.getSuccessRate());
    }

    @Test
    @DisplayName("getPlatformPerformance_failedJob_zeroSuccessRate")
    void testGetPlatformPerformance_failedJob_zeroSuccessRate() throws Exception {
        // Arrange
        MarketingJob job = MarketingJob.builder()
            .id(2L)
            .postId("post-002")
            .status("FAILED")
            .targets("[\"youtube_shorts\"]")
            .publications("[]")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        List<MarketingJob> jobs = new ArrayList<>();
        jobs.add(job);

        when(marketingJobRepository.findByStatusIn(any()))
            .thenReturn(jobs);
        when(objectMapper.readValue(anyString(), (TypeReference<?>) any()))
            .thenAnswer(invocation -> {
                String json = invocation.getArgument(0);
                TypeReference<?> ref = invocation.getArgument(1);
                ObjectMapper realMapper = new ObjectMapper();
                return realMapper.readValue(json, ref);
            });

        // Act
        List<MarketingStatsService.PlatformStatsDto> result = service.getPlatformPerformance(30);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        MarketingStatsService.PlatformStatsDto youtubeStats = result.get(0);
        assertEquals("youtube_shorts", youtubeStats.getPlatform());
        assertEquals(1, youtubeStats.getAttempted());
        assertEquals(0, youtubeStats.getPublished());
        assertEquals(1, youtubeStats.getFailed());
        assertEquals(0.0, youtubeStats.getSuccessRate());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPublicationTimeline Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPublicationTimeline_parsesPublicationsJson")
    void testGetPublicationTimeline_parsesPublicationsJson() throws Exception {
        // Arrange
        MarketingJob job = MarketingJob.builder()
            .id(3L)
            .postId("post-003")
            .status("PUBLISHED")
            .publications("[{\"platform\":\"x\",\"state\":\"PUBLISHED\",\"url\":\"https://x.com/post123\",\"publishedAt\":\"2026-06-10T12:00:00Z\"}]")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        List<MarketingJob> jobs = new ArrayList<>();
        jobs.add(job);

        when(marketingJobRepository.findAll())
            .thenReturn(jobs);
        when(objectMapper.readValue(anyString(), (TypeReference<?>) any()))
            .thenAnswer(invocation -> {
                String json = invocation.getArgument(0);
                TypeReference<?> ref = invocation.getArgument(1);
                ObjectMapper realMapper = new ObjectMapper();
                return realMapper.readValue(json, ref);
            });

        // Act
        List<MarketingStatsService.TimelineEventDto> result = service.getPublicationTimeline(20);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        MarketingStatsService.TimelineEventDto event = result.get(0);
        assertEquals(3L, event.getJobId());
        assertEquals("post-003", event.getPostId());
        assertEquals("x", event.getPlatform());
        assertEquals("https://x.com/post123", event.getUrl());
        assertEquals("PUBLISHED", event.getState());
    }

    @Test
    @DisplayName("getPublicationTimeline_respects_limit")
    void testGetPublicationTimeline_respects_limit() throws Exception {
        // Arrange
        List<MarketingJob> jobs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            MarketingJob job = MarketingJob.builder()
                .id((long) i)
                .postId("post-" + i)
                .status("PUBLISHED")
                .publications("[{\"platform\":\"x\",\"state\":\"PUBLISHED\",\"url\":\"https://x.com/post" + i + "\"}]")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            jobs.add(job);
        }

        when(marketingJobRepository.findAll())
            .thenReturn(jobs);
        when(objectMapper.readValue(anyString(), (TypeReference<?>) any()))
            .thenAnswer(invocation -> {
                String json = invocation.getArgument(0);
                TypeReference<?> ref = invocation.getArgument(1);
                ObjectMapper realMapper = new ObjectMapper();
                return realMapper.readValue(json, ref);
            });

        // Act
        List<MarketingStatsService.TimelineEventDto> result = service.getPublicationTimeline(10);

        // Assert
        assertNotNull(result);
        assertEquals(10, result.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getJobTraffic Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getJobTraffic_noVisits_returnsZero")
    void testGetJobTraffic_noVisits_returnsZero() {
        // Arrange
        when(jdbcTemplate.queryForObject(
            contains("COUNT(*)"), eq(Integer.class), anyString()))
            .thenReturn(0);
        when(jdbcTemplate.queryForObject(
            contains("COUNT(DISTINCT session_key)"), eq(Integer.class), anyString()))
            .thenReturn(0);
        when(jdbcTemplate.queryForList(
            contains("utm_source"), anyString()))
            .thenReturn(new ArrayList<>());

        // Act
        MarketingStatsService.JobTrafficDto result = service.getJobTraffic(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getJobId());
        assertEquals(0, result.getVisits());
        assertEquals(0, result.getUniqueSessions());
        assertTrue(result.getBySources().isEmpty());
        verify(jdbcTemplate).queryForObject(
            contains("COUNT(*)"), eq(Integer.class), eq("story_1"));
    }

    @Test
    @DisplayName("getJobTraffic_withVisits_computesMetrics")
    void testGetJobTraffic_withVisits_computesMetrics() {
        // Arrange
        when(jdbcTemplate.queryForObject(
            contains("COUNT(*)"), eq(Integer.class), anyString()))
            .thenReturn(100);
        when(jdbcTemplate.queryForObject(
            contains("COUNT(DISTINCT session_key)"), eq(Integer.class), anyString()))
            .thenReturn(25);

        List<Map<String, Object>> sourceData = new ArrayList<>();
        Map<String, Object> row1 = new HashMap<>();
        row1.put("source", "google");
        row1.put("visits", 60);
        sourceData.add(row1);

        Map<String, Object> row2 = new HashMap<>();
        row2.put("source", "organic");
        row2.put("visits", 40);
        sourceData.add(row2);

        when(jdbcTemplate.queryForList(
            contains("utm_source"), anyString()))
            .thenReturn(sourceData);

        // Act
        MarketingStatsService.JobTrafficDto result = service.getJobTraffic(5L);

        // Assert
        assertNotNull(result);
        assertEquals(5L, result.getJobId());
        assertEquals(100, result.getVisits());
        assertEquals(25, result.getUniqueSessions());
        assertEquals(2, result.getBySources().size());

        Map<String, Object> source1 = result.getBySources().get(0);
        assertEquals("google", source1.get("source"));
        assertEquals(60, source1.get("visits"));
    }
}
