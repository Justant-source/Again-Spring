package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingPublicationStats;
import com.againspring.repository.marketing.MarketingPublicationStatsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketingScoreAutoAdjustService")
class MarketingScoreAutoAdjustServiceTest {

    @Mock
    MarketingScoreWeightService scoreWeightService;
    @Mock
    MarketingPublicationStatsRepository statsRepository;

    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    MarketingScoreAutoAdjustService service;

    @org.junit.jupiter.api.BeforeEach
    void wireMapper() throws Exception {
        var f = MarketingScoreAutoAdjustService.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        f.set(service, objectMapper);
    }

    @Test
    @DisplayName("disabled → report-only, no weight write")
    void disabledNoWrite() {
        when(scoreWeightService.getPlatformWeights()).thenReturn(sampleAll());
        when(scoreWeightService.toNestedMap(any())).thenReturn(Map.of());
        when(scoreWeightService.isAutoAdjustEnabled()).thenReturn(false);

        var result = service.runWeeklyAdjust();

        assertThat(result.enabled()).isFalse();
        assertThat(result.applied()).isFalse();
        verify(scoreWeightService, never()).updatePlatformWeightsPartial(any(), any());
    }

    @Test
    @DisplayName("enabled + comment-heavy mix nudges comments weight within caps")
    void enabledNudgesComments() {
        when(scoreWeightService.getPlatformWeights()).thenReturn(sampleAll());
        when(scoreWeightService.toNestedMap(any())).thenAnswer(inv -> Map.of("x_thread", Map.of("comments", 2.0)));
        when(scoreWeightService.isAutoAdjustEnabled()).thenReturn(true);
        when(statsRepository.findCollectedSince(any())).thenReturn(List.of(
            MarketingPublicationStats.builder()
                .jobId(1L).postId("p1").platform("x_thread")
                .collectedAt(Instant.now())
                .metricsJson("{\"views\":10,\"likes\":1,\"comments\":40,\"replies\":10}")
                .partial(false)
                .build()
        ));
        when(scoreWeightService.updatePlatformWeightsPartial(anyMap(), eq("marketing.score.auto_adjust")))
            .thenReturn(sampleAll());

        var result = service.runWeeklyAdjust();

        assertThat(result.enabled()).isTrue();
        assertThat(result.applied()).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, MarketingPopularityScorer.PlatformWeights>> cap =
            ArgumentCaptor.forClass(Map.class);
        verify(scoreWeightService).updatePlatformWeightsPartial(cap.capture(), eq("marketing.score.auto_adjust"));
        MarketingPopularityScorer.PlatformWeights x = cap.getValue().get("x_thread");
        assertThat(x).isNotNull();
        // default X comments=2.0; delta capped at +0.05
        assertThat(x.comments()).isGreaterThan(2.0);
        assertThat(x.comments()).isLessThanOrEqualTo(2.0 + MarketingScoreAutoAdjustService.ABSOLUTE_CAP + 1e-9);
    }

    private static MarketingScoreWeightService.AllPlatformWeights sampleAll() {
        return new MarketingScoreWeightService.AllPlatformWeights(
            MarketingScoreWeightService.defaultsX(),
            MarketingScoreWeightService.defaultsFeed(),
            MarketingScoreWeightService.defaultsReels(),
            MarketingScoreWeightService.defaultsShorts()
        );
    }
}
