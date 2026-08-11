package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingPublicationStats;
import com.againspring.repository.marketing.MarketingPublicationStatsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketingStatsDashboardService")
class MarketingStatsDashboardServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    MarketingPublicationStatsRepository statsRepository;
    @Mock
    JdbcTemplate jdbcTemplate;

    ObjectMapper objectMapper = new ObjectMapper();
    MarketingStatsDashboardService service;

    @BeforeEach
    void setUp() {
        service = new MarketingStatsDashboardService(statsRepository, jdbcTemplate, objectMapper);
    }

    @Test
    @DisplayName("default primary metrics match plan §4.3.1")
    void defaultPrimaryMetrics() {
        assertThat(MarketingStatsDashboardService.defaultPrimaryMetric("x_thread"))
            .isEqualTo("impressions");
        assertThat(MarketingStatsDashboardService.defaultPrimaryMetric("instagram_feed"))
            .isEqualTo("reach");
        assertThat(MarketingStatsDashboardService.defaultPrimaryMetric("instagram_reels"))
            .isEqualTo("plays");
        assertThat(MarketingStatsDashboardService.defaultPrimaryMetric("youtube_shorts"))
            .isEqualTo("views");
    }

    @Test
    @DisplayName("pickMetricValue uses platform fallbacks (impressions→views, plays→views)")
    void pickMetricFallbacks() {
        assertThat(service.pickMetricValue("x_thread", null, "{\"views\":42}"))
            .isEqualTo(42L);
        assertThat(service.pickMetricValue("x_thread", null, "{\"impressions\":100,\"views\":42}"))
            .isEqualTo(100L);
        assertThat(service.pickMetricValue("instagram_feed", null, "{\"reach\":77,\"views\":1}"))
            .isEqualTo(77L);
        assertThat(service.pickMetricValue("instagram_reels", null, "{\"views\":9}"))
            .isEqualTo(9L);
        assertThat(service.pickMetricValue("instagram_reels", null, "{\"plays\":11,\"views\":9}"))
            .isEqualTo(11L);
        assertThat(service.pickMetricValue("youtube_shorts", null, "{\"views\":5,\"plays\":99}"))
            .isEqualTo(5L);
        assertThat(service.pickMetricValue("x_thread", "likes", "{\"likes\":3,\"views\":99}"))
            .isEqualTo(3L);
    }

    @Test
    @DisplayName("deltaPct null when prev=0 and value>0; 0 when both 0")
    void deltaPctEdgeCases() {
        assertThat(MarketingStatsDashboardService.deltaPct(0, 0)).isEqualTo(0.0);
        assertThat(MarketingStatsDashboardService.deltaPct(10, 0)).isNull();
        assertThat(MarketingStatsDashboardService.deltaPct(150, 100)).isEqualTo(50.0);
    }

    @Test
    @DisplayName("weekWindow weeksAgo=0 is current KST Mon–next Mon")
    void weekWindowCurrent() {
        var w = MarketingStatsDashboardService.weekWindow(0);
        assertThat(w.start().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(w.end().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(ChronoUnit.DAYS.between(w.start(), w.end())).isEqualTo(7);
        ZonedDateTime now = ZonedDateTime.now(KST);
        assertThat(!now.isBefore(w.start()) && now.isBefore(w.end())).isTrue();
    }

    @Test
    @DisplayName("dashboard aggregates WoW value for filtered platform")
    void dashboardAggregatesWeekOverWeek() {
        var week = MarketingStatsDashboardService.weekWindow(0);
        Instant midWeek = week.start().plusDays(2).toInstant();
        Instant prevMid = week.start().minusDays(3).toInstant();

        when(statsRepository.findCollectedSince(any())).thenReturn(List.of(
            MarketingPublicationStats.builder()
                .jobId(1L).postId("p1").platform("x_thread")
                .collectedAt(midWeek)
                .metricsJson("{\"impressions\":200}")
                .partial(false)
                .build(),
            MarketingPublicationStats.builder()
                .jobId(1L).postId("p1").platform("x_thread")
                .collectedAt(prevMid)
                .metricsJson("{\"impressions\":100}")
                .partial(false)
                .build()
        ));
        stubEmptyUtm();
        when(jdbcTemplate.queryForMap(anyString(), eq("p1")))
            .thenReturn(Map.of("hook_emotion", "shock", "category", "{\"major\":\"연애\"}"));

        var dto = service.dashboard("x_thread", 0, 7, null);

        assertThat(dto.platforms()).hasSize(1);
        assertThat(dto.platforms().get(0).platform()).isEqualTo("x_thread");
        assertThat(dto.platforms().get(0).primaryMetric()).isEqualTo("impressions");
        assertThat(dto.platforms().get(0).value()).isEqualTo(200L);
        assertThat(dto.platforms().get(0).prevValue()).isEqualTo(100L);
        assertThat(dto.platforms().get(0).deltaPct()).isEqualTo(100.0);
        assertThat(dto.platforms().get(0).series()).hasSize(7);
        assertThat(dto.health().channels()).hasSize(1);
        assertThat(dto.unknownCounts().missingEmotion()).isZero();
    }

    private void stubEmptyUtm() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Integer.class), any(), any()))
            .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("DISTINCT session_key"), eq(Integer.class), any(), any()))
            .thenReturn(0);
        when(jdbcTemplate.queryForList(contains("utm_source"), any(Object.class), any(Object.class)))
            .thenReturn(List.of());
    }
}
