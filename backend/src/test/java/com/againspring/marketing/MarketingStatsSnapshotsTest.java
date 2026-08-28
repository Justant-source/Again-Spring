package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingPublicationStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketingStatsSnapshotsTest {

    private static MarketingPublicationStats row(long job, String platform, Instant at, String metrics) {
        return MarketingPublicationStats.builder()
            .jobId(job).postId("p" + job).platform(platform)
            .collectedAt(at).metricsJson(metrics).partial(false)
            .build();
    }

    @Test
    @DisplayName("지표가 전부 null인 더 최신 스냅샷은 직전 정상 수집분을 덮어쓰지 않는다")
    void emptyNewerSnapshotDoesNotShadowGoodOne() {
        Instant t0 = Instant.parse("2026-08-24T21:31:00Z");
        Instant t1 = Instant.parse("2026-08-28T21:31:00Z");
        List<MarketingPublicationStats> picked = MarketingStatsSnapshots.latestWithMetricsPerJobPlatform(List.of(
            row(1L, "x_thread", t0, "{\"impressions\":226,\"views\":226,\"likes\":1,\"replies\":1}"),
            row(1L, "x_thread", t1, "{\"impressions\":null,\"views\":null,\"likes\":null,\"replies\":null}")
        ));

        assertThat(picked).hasSize(1);
        assertThat(picked.get(0).getCollectedAt()).isEqualTo(t0);
        assertThat(picked.get(0).hasAnyMetric()).isTrue();
    }

    @Test
    @DisplayName("정상 스냅샷끼리는 최신 것을 고르고, 잡·플랫폼별로 분리한다")
    void latestGoodPerJobPlatform() {
        Instant t0 = Instant.parse("2026-08-24T21:31:00Z");
        Instant t1 = Instant.parse("2026-08-25T21:31:00Z");
        List<MarketingPublicationStats> picked = MarketingStatsSnapshots.latestWithMetricsPerJobPlatform(List.of(
            row(1L, "x_thread", t0, "{\"impressions\":100}"),
            row(1L, "x_thread", t1, "{\"impressions\":150}"),
            row(1L, "youtube_shorts", t0, "{\"views\":933,\"likes\":0}"),
            row(2L, "x_thread", t0, "{\"impressions\":null}")
        ));

        assertThat(picked).hasSize(2);
        assertThat(picked).anySatisfy(r -> {
            assertThat(r.getPlatform()).isEqualTo("x_thread");
            assertThat(r.getMetricsJson()).contains("150");
        });
    }

    @Test
    @DisplayName("hasAnyMetric — 숫자 하나라도 있으면 true, 전부 null이면 false")
    void hasAnyMetric() {
        assertThat(row(1L, "x_thread", Instant.now(), "{\"impressions\":0,\"likes\":null}").hasAnyMetric()).isTrue();
        assertThat(row(1L, "x_thread", Instant.now(), "{\"impressions\":null,\"likes\":null}").hasAnyMetric()).isFalse();
        assertThat(row(1L, "x_thread", Instant.now(), "{}").hasAnyMetric()).isFalse();
    }
}
