package com.againspring.service.admin;

import com.againspring.api.dto.response.CrawlStatusResponse;
import com.againspring.service.ai.AiLearningBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCrawlStatusServiceTest {

    @Mock
    private AiLearningBridge aiLearningBridge;

    @InjectMocks
    private AdminCrawlStatusService service;

    @Test
    void testGetCrawlStatus_Success() {
        // Arrange
        Instant now = Instant.now();
        Instant _2hAgo = now.minus(2, ChronoUnit.HOURS);
        Instant _12hAgo = now.minus(12, ChronoUnit.HOURS);
        Instant _25hAgo = now.minus(25, ChronoUnit.HOURS);

        List<AiLearningBridge.CrawlLog> logs = List.of(
            new AiLearningBridge.CrawlLog("natepan", "SUCCESS", 120, _2hAgo.toString()),
            new AiLearningBridge.CrawlLog("blind", "SUCCESS", 45, _12hAgo.toString()),
            new AiLearningBridge.CrawlLog("theqoo", "FAILED", null, _12hAgo.toString()),
            new AiLearningBridge.CrawlLog("natepan", "SUCCESS", 80, _25hAgo.toString())
        );

        when(aiLearningBridge.getCrawlLogsWithFallback()).thenReturn(logs);

        // Act
        CrawlStatusResponse response = service.getCrawlStatus();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getErrorMessage()).isNull();
        assertThat(response.getStale()).isFalse();
        assertThat(response.getFailureCount24h()).isEqualTo(1);
        assertThat(response.getSavedBySource24h()).containsEntry("natepan", 120).containsEntry("blind", 45);
        assertThat(response.getLastSuccessfulAt()).containsKey("natepan").containsKey("blind");
        assertThat(response.getCheckedAt()).isNotNull();
    }

    @Test
    void testGetCrawlStatus_NoSuccessIn24h() {
        // Arrange
        Instant now = Instant.now();
        Instant _26hAgo = now.minus(26, ChronoUnit.HOURS);

        List<AiLearningBridge.CrawlLog> logs = List.of(
            new AiLearningBridge.CrawlLog("natepan", "FAILED", null, _26hAgo.toString()),
            new AiLearningBridge.CrawlLog("blind", "FAILED", null, _26hAgo.toString())
        );

        when(aiLearningBridge.getCrawlLogsWithFallback()).thenReturn(logs);

        // Act
        CrawlStatusResponse response = service.getCrawlStatus();

        // Assert
        assertThat(response.getStale()).isTrue();
        // WO-CRAWL-01: 빈 맵이라도 null이 아니어야 함 — FE가 항상 Record<string, number>로 취급
        // (null이면 JsonInclude(NON_NULL)로 필드 자체가 생략되어 FE에서 undefined.values() 크래시)
        assertThat(response.getSavedBySource24h()).isNotNull().isEmpty();
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    void testGetCrawlStatus_QueryFailed() {
        // Arrange
        Instant now = Instant.now();
        List<AiLearningBridge.CrawlLog> logs = List.of(
            AiLearningBridge.CrawlLog.error("Connection timeout")
        );

        when(aiLearningBridge.getCrawlLogsWithFallback()).thenReturn(logs);

        // Act
        CrawlStatusResponse response = service.getCrawlStatus();

        // Assert
        assertThat(response.getErrorMessage()).isEqualTo("Connection timeout");
        assertThat(response.getStale()).isTrue();
        assertThat(response.getFailureCount24h()).isEqualTo(0);
        // WO-CRAWL-01: 빈 맵이라도 null이 아니어야 함 — FE가 항상 Record<string, number>로 취급
        // (null이면 JsonInclude(NON_NULL)로 필드 자체가 생략되어 FE에서 undefined.values() 크래시)
        assertThat(response.getSavedBySource24h()).isNotNull().isEmpty();
    }

    @Test
    void testGetCrawlStatus_EmptyLogs() {
        // Arrange
        when(aiLearningBridge.getCrawlLogsWithFallback()).thenReturn(List.of());

        // Act
        CrawlStatusResponse response = service.getCrawlStatus();

        // Assert
        assertThat(response.getStale()).isTrue();
        // WO-CRAWL-01: 빈 맵이라도 null이 아니어야 함 — FE가 항상 Record<string, number>로 취급
        // (null이면 JsonInclude(NON_NULL)로 필드 자체가 생략되어 FE에서 undefined.values() 크래시)
        assertThat(response.getSavedBySource24h()).isNotNull().isEmpty();
        assertThat(response.getFailureCount24h()).isEqualTo(0);
    }
}
