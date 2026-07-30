package com.againspring.api.admin;

import com.againspring.api.dto.response.CrawlStatusResponse;
import com.againspring.service.admin.AdminCrawlStatusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCrawlStatusController Tests")
class AdminCrawlStatusControllerTest {

    @Mock
    private AdminCrawlStatusService crawlStatusService;

    @InjectMocks
    private AdminCrawlStatusController controller;

    @Test
    @DisplayName("정상 크롤 상태 조회 → 200 OK")
    void getCrawlStatus_success() {
        // Arrange
        Instant now = Instant.now();
        CrawlStatusResponse response = CrawlStatusResponse.builder()
            .savedBySource24h(Map.of("natepan", 120, "blind", 45))
            .lastSuccessfulAt(Map.of(
                "natepan", "2026-07-29T14:30:00Z",
                "blind", "2026-07-29T10:15:00Z"
            ))
            .failureCount24h(2)
            .stale(false)
            .checkedAt(now)
            .errorMessage(null)
            .build();

        when(crawlStatusService.getCrawlStatus()).thenReturn(response);

        // Act
        ResponseEntity<CrawlStatusResponse> result = controller.getCrawlStatus();

        // Assert
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStale()).isFalse();
        assertThat(result.getBody().getFailureCount24h()).isEqualTo(2);
        assertThat(result.getBody().getSavedBySource24h()).containsEntry("natepan", 120).containsEntry("blind", 45);
        assertThat(result.getBody().getLastSuccessfulAt()).containsKey("natepan").containsKey("blind");
        assertThat(result.getBody().getCheckedAt()).isNotNull();
        assertThat(result.getBody().getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("24시간 내 성공 크롤 없음 → stale=true")
    void getCrawlStatus_stale() {
        // Arrange
        Instant now = Instant.now();
        CrawlStatusResponse response = CrawlStatusResponse.builder()
            .savedBySource24h(null)
            .lastSuccessfulAt(null)
            .failureCount24h(0)
            .stale(true)
            .checkedAt(now)
            .errorMessage(null)
            .build();

        when(crawlStatusService.getCrawlStatus()).thenReturn(response);

        // Act
        ResponseEntity<CrawlStatusResponse> result = controller.getCrawlStatus();

        // Assert
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody().getStale()).isTrue();
        assertThat(result.getBody().getFailureCount24h()).isEqualTo(0);
        assertThat(result.getBody().getSavedBySource24h()).isNull();
    }

    @Test
    @DisplayName("AI Learning 서비스 조회 실패 → errorMessage 포함")
    void getCrawlStatus_queryFailed() {
        // Arrange
        Instant now = Instant.now();
        CrawlStatusResponse response = CrawlStatusResponse.builder()
            .stale(true)
            .failureCount24h(0)
            .checkedAt(now)
            .errorMessage("Connection timeout to AI Learning service")
            .build();

        when(crawlStatusService.getCrawlStatus()).thenReturn(response);

        // Act
        ResponseEntity<CrawlStatusResponse> result = controller.getCrawlStatus();

        // Assert
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody().getErrorMessage()).contains("Connection timeout");
        assertThat(result.getBody().getStale()).isTrue();
    }
}
