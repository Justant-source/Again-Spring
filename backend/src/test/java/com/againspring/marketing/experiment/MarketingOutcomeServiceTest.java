package com.againspring.marketing.experiment;

import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.domain.marketing.MarketingPublicationOutcome;
import com.againspring.domain.marketing.MarketingPublicationStats;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.againspring.repository.marketing.MarketingPublicationOutcomeRepository;
import com.againspring.repository.marketing.MarketingPublicationStatsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingOutcomeServiceTest {

    private static final Instant PUBLISHED = Instant.parse("2026-11-10T00:00:00Z");
    private static final String YT = "youtube_shorts";

    @Mock MarketingJobRepository jobRepository;
    @Mock MarketingPublicationStatsRepository statsRepository;
    @Mock MarketingPublicationOutcomeRepository outcomeRepository;
    @Mock PostRepository postRepository;

    Instant now = PUBLISHED.plus(Duration.ofHours(60));
    MarketingOutcomeService service;

    @BeforeEach
    void setUp() {
        build();
        lenient().when(outcomeRepository.existsByJobIdAndPlatform(any(), any())).thenReturn(false);
        Post post = new Post();
        post.setCategory(PostCategory.COUPLE);
        post.setHookEmotion("anger");
        lenient().when(postRepository.findById("p1")).thenReturn(Optional.of(post));
        lenient().when(outcomeRepository
            .findTop30ByPlatformAndStatusAndPublishedAtBeforeOrderByPublishedAtDesc(any(), any(), any()))
            .thenReturn(List.of());
    }

    private void build() {
        service = new MarketingOutcomeService(jobRepository, statsRepository, outcomeRepository, postRepository,
            new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private MarketingJob job() {
        return MarketingJob.builder().id(1L).postId("p1").formatArm("dilemma")
            .scheduledPublishAt(PUBLISHED).createdAt(PUBLISHED.minusSeconds(3600))
            .publications("[{\"platform\":\"youtube_shorts\",\"state\":\"PUBLISHED\"}]").build();
    }

    private static MarketingPublicationStats snap(Instant at, String metrics) {
        return MarketingPublicationStats.builder().jobId(1L).postId("p1").platform(YT)
            .collectedAt(at).metricsJson(metrics).build();
    }

    private MarketingPublicationOutcome savedOutcome() {
        ArgumentCaptor<MarketingPublicationOutcome> c = ArgumentCaptor.forClass(MarketingPublicationOutcome.class);
        verify(outcomeRepository).save(c.capture());
        return c.getValue();
    }

    private void jobs(MarketingJob... j) {
        when(jobRepository.findPublishAttemptsSince(any())).thenReturn(List.of(j));
    }

    private static List<MarketingPublicationOutcome> baseline(int n, long primary, long engagement) {
        List<MarketingPublicationOutcome> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(MarketingPublicationOutcome.builder().primaryMetric(primary).engagement(engagement).build());
        }
        return l;
    }

    @Test
    void picksEarliestMetricSnapshotInsideWindow_andSkipsEmptyRow() {
        jobs(job());
        when(statsRepository.findByJobIdOrderByCollectedAtDesc(1L)).thenReturn(List.of(
            snap(PUBLISHED.plus(Duration.ofHours(50)), "{\"views\":900,\"likes\":9,\"comments\":1}"),
            snap(PUBLISHED.plus(Duration.ofHours(40)), "{\"views\":500,\"likes\":5,\"comments\":0}"),
            snap(PUBLISHED.plus(Duration.ofHours(38)), "{\"views\":null,\"likes\":null,\"comments\":null}"),
            snap(PUBLISHED.plus(Duration.ofHours(10)), "{\"views\":100,\"likes\":1}")));

        assertThat(service.scorePending()).isEqualTo(1);
        MarketingPublicationOutcome o = savedOutcome();
        assertThat(o.getStatus()).isEqualTo("SCORED");
        assertThat(o.getPrimaryMetric()).isEqualTo(500L);
        assertThat(o.getEngagement()).isEqualTo(5L);
        assertThat(o.getFormatArm()).isEqualTo("dilemma");
        assertThat(o.getCategory()).isEqualTo("COUPLE");
        assertThat(o.getHookEmotion()).isEqualTo("anger");
        assertThat(o.getPublishedAt()).isEqualTo(PUBLISHED);
    }

    @Test
    void fewerThan10Baselines_ratiosAreOne() {
        jobs(job());
        when(statsRepository.findByJobIdOrderByCollectedAtDesc(1L)).thenReturn(List.of(
            snap(PUBLISHED.plus(Duration.ofHours(40)), "{\"views\":500,\"likes\":5}")));
        when(outcomeRepository.findTop30ByPlatformAndStatusAndPublishedAtBeforeOrderByPublishedAtDesc(
            any(), any(), any())).thenReturn(baseline(9, 100, 1));

        service.scorePending();
        MarketingPublicationOutcome o = savedOutcome();
        assertThat(o.getViewRatio()).isEqualTo(1.0);
        assertThat(o.getEngRatio()).isEqualTo(1.0);
        assertThat(o.getReward()).isCloseTo(1.0, within(1e-9));
        assertThat(o.isBreakout()).isFalse();
    }

    @Test
    void breakoutBoundary_exactlyThreeIsBreakout() {
        jobs(job());
        when(statsRepository.findByJobIdOrderByCollectedAtDesc(1L)).thenReturn(List.of(
            snap(PUBLISHED.plus(Duration.ofHours(40)), "{\"views\":300,\"likes\":3}")));
        when(outcomeRepository.findTop30ByPlatformAndStatusAndPublishedAtBeforeOrderByPublishedAtDesc(
            any(), any(), any())).thenReturn(baseline(10, 100, 1));

        service.scorePending();
        MarketingPublicationOutcome o = savedOutcome();
        assertThat(o.getViewRatio()).isCloseTo(3.0, within(1e-9));
        assertThat(o.isBreakout()).isTrue();
        // engRate 0.01 / median 0.01 = 1.0 ; reward = 0.6*3 + 0.4*1
        assertThat(o.getReward()).isCloseTo(2.2, within(1e-9));
    }

    @Test
    void justBelowThree_isNotBreakout() {
        jobs(job());
        when(statsRepository.findByJobIdOrderByCollectedAtDesc(1L)).thenReturn(List.of(
            snap(PUBLISHED.plus(Duration.ofHours(40)), "{\"views\":299,\"likes\":3}")));
        when(outcomeRepository.findTop30ByPlatformAndStatusAndPublishedAtBeforeOrderByPublishedAtDesc(
            any(), any(), any())).thenReturn(baseline(10, 100, 1));

        service.scorePending();
        assertThat(savedOutcome().isBreakout()).isFalse();
    }

    @Test
    void medianEngRateZero_usesTwoWhenPositive() {
        jobs(job());
        when(statsRepository.findByJobIdOrderByCollectedAtDesc(1L)).thenReturn(List.of(
            snap(PUBLISHED.plus(Duration.ofHours(40)), "{\"views\":100,\"likes\":5}")));
        when(outcomeRepository.findTop30ByPlatformAndStatusAndPublishedAtBeforeOrderByPublishedAtDesc(
            any(), any(), any())).thenReturn(baseline(10, 100, 0));

        service.scorePending();
        assertThat(savedOutcome().getEngRatio()).isEqualTo(2.0);
    }

    @Test
    void noSnapshotAfter96h_marksMissing() {
        now = PUBLISHED.plus(Duration.ofHours(97));
        build();
        jobs(job());
        when(statsRepository.findByJobIdOrderByCollectedAtDesc(1L)).thenReturn(List.of(
            snap(PUBLISHED.plus(Duration.ofHours(10)), "{\"views\":100}")));

        assertThat(service.scorePending()).isEqualTo(1);
        MarketingPublicationOutcome o = savedOutcome();
        assertThat(o.getStatus()).isEqualTo("MISSING");
        assertThat(o.getReward()).isNull();
        assertThat(o.isBreakout()).isFalse();
    }

    @Test
    void noSnapshotBefore96h_staysPending() {
        jobs(job());
        when(statsRepository.findByJobIdOrderByCollectedAtDesc(1L)).thenReturn(List.of());

        assertThat(service.scorePending()).isZero();
        verify(outcomeRepository, never()).save(any());
    }

    @Test
    void alreadyScored_isNotScoredAgain() {
        jobs(job());
        when(outcomeRepository.existsByJobIdAndPlatform(1L, YT)).thenReturn(true);

        assertThat(service.scorePending()).isZero();
        verify(outcomeRepository, never()).save(any());
    }

    @Test
    void unpublishedPlatformIsIgnored() {
        MarketingJob j = job();
        j.setPublications("[{\"platform\":\"youtube_shorts\",\"state\":\"FAILED\"}]");
        jobs(j);

        assertThat(service.scorePending()).isZero();
    }

    @Test
    void engagementFormulas_perPlatform() {
        assertThat(service.extract("x_thread", "{\"impressions\":1000,\"likes\":10,\"replies\":2,\"reposts\":1}"))
            .isEqualTo(new MarketingOutcomeService.Metrics(1000, 10 + 4 + 3));
        assertThat(service.extract("instagram_reels",
            "{\"reach\":800,\"likes\":10,\"comments\":2,\"shares\":1,\"saves\":2}"))
            .isEqualTo(new MarketingOutcomeService.Metrics(800, 10 + 4 + 9));
        assertThat(service.extract("youtube_shorts", "{\"views\":900,\"likes\":10,\"comments\":3}"))
            .isEqualTo(new MarketingOutcomeService.Metrics(900, 16));
    }
}
