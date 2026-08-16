package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.marketing.dto.AsmJobView;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.marketing.MarketingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class MarketingPollingSchedulerTest {

    @Mock AsmClient asmClient;
    @Mock MarketingJobRepository marketingJobRepository;
    @Mock MarketingJobService marketingJobService;
    @Mock AsmProperties asmProperties;
    @Mock TelegramNotifier telegramNotifier;

    MarketingPollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MarketingPollingScheduler(
            asmClient, marketingJobRepository, marketingJobService, asmProperties, telegramNotifier);
        when(asmProperties.isEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("READY auto-publish jobs publish immediately even if an evening slot is still in the future")
    void readyAutoPublishJobsPublishWithoutWaitingForEveningSlot() {
        Instant futureEvening = Instant.now().plus(8, ChronoUnit.HOURS);
        MarketingJob job = MarketingJob.builder()
            .id(580L)
            .remoteJobId("asm-reels")
            .postId("post_v")
            .status("READY")
            .autoPublish(true)
            .scheduledPublishAt(futureEvening)
            .artifacts("{\"mp4\":\"https://example/x.mp4\"}")
            .build();

        when(marketingJobRepository.findByStatusIn(any())).thenReturn(List.of(job));
        when(marketingJobRepository.findDueAutoPublishJobs(any())).thenReturn(List.of(job));

        scheduler.pollJobs();

        verify(marketingJobService).triggerPublish(580L);
        verify(asmClient, never()).getJob(anyString());
        verify(telegramNotifier, never()).send(anyString());
    }

    @Test
    @DisplayName("preview jobs (autoPublish=false) never auto-publish")
    void previewJobsDoNotAutoPublish() {
        when(marketingJobRepository.findByStatusIn(any())).thenReturn(List.of());
        when(marketingJobRepository.findDueAutoPublishJobs(any())).thenReturn(List.of());

        scheduler.pollJobs();

        verify(telegramNotifier, never()).send(anyString());
        verify(marketingJobRepository, never()).save(any());
        verify(marketingJobService, never()).triggerPublish(anyLong());
    }

    @Test
    @DisplayName("still-generating auto-publish jobs are not rolled to a next-day evening slot")
    void generatingJobsAreNotCarriedToNextEvening() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        MarketingJob job = MarketingJob.builder()
            .id(561L)
            .remoteJobId("asm-1")
            .postId("post_x")
            .status("QUEUED")
            .autoPublish(true)
            .scheduledPublishAt(past)
            .originalScheduledAt(past)
            .rescheduledCount(0)
            .build();

        when(marketingJobRepository.findByStatusIn(any())).thenReturn(List.of(job));
        when(marketingJobRepository.findDueAutoPublishJobs(any())).thenReturn(List.of());
        when(asmClient.getJob("asm-1")).thenReturn(AsmJobView.builder()
            .status("QUEUED").phase("SCRIPT").progress(0.1).build());

        scheduler.pollJobs();

        verify(marketingJobService).applyPoll(any(), any());
        verify(telegramNotifier, never()).send(anyString());
        assertThat(job.getRescheduledCount()).isZero();
        assertThat(job.getScheduledPublishAt()).isEqualTo(past);
    }

    @Test
    @DisplayName("READY jobs with artifacts are not polled")
    void skipsReadyPreviewWithArtifacts() {
        MarketingJob ready = MarketingJob.builder()
            .id(571L)
            .remoteJobId("asm-ready")
            .postId("post_z")
            .status("READY")
            .artifacts("{\"mp4\":\"https://example/x.mp4\"}")
            .build();

        when(marketingJobRepository.findByStatusIn(any())).thenReturn(List.of(ready));
        when(marketingJobRepository.findDueAutoPublishJobs(any())).thenReturn(List.of());

        scheduler.pollJobs();

        verify(asmClient, never()).getJob(anyString());
    }

    @Test
    @DisplayName("STALE jobs with artifacts are restored to READY without ASM GET")
    void restoresStaleWithArtifacts() {
        MarketingJob stale = MarketingJob.builder()
            .id(571L)
            .remoteJobId("asm-stale")
            .postId("post_z")
            .status("STALE")
            .pollFailCount(37)
            .errorMessage("Poll failure #37")
            .artifacts("{\"mp4\":\"https://example/x.mp4\"}")
            .build();

        when(marketingJobRepository.findByStatusIn(any())).thenReturn(List.of(stale));
        when(marketingJobRepository.findDueAutoPublishJobs(any())).thenReturn(List.of());

        scheduler.pollJobs();

        verify(asmClient, never()).getJob(anyString());
        verify(marketingJobRepository).save(stale);
        assertThat(stale.getStatus()).isEqualTo("READY");
        assertThat(stale.getPollFailCount()).isZero();
        assertThat(stale.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("late READY job is published in the same reconciliation cycle even before any clock slot")
    void publishesImmediatelyWhenRemoteBecomesReady() {
        Instant futureEvening = Instant.now().plus(8, ChronoUnit.HOURS);
        MarketingJob job = MarketingJob.builder()
            .id(572L).remoteJobId("asm-late-ready").postId("post_late").status("WAITING_EXTERNAL")
            .autoPublish(true).scheduledPublishAt(futureEvening).build();

        when(marketingJobRepository.findByStatusIn(any())).thenReturn(List.of(job));
        when(marketingJobRepository.findDueAutoPublishJobs(any())).thenReturn(List.of());
        when(asmClient.getJob("asm-late-ready")).thenReturn(AsmJobView.builder().status("READY").build());
        doAnswer(invocation -> {
            ((MarketingJob) invocation.getArgument(0)).setStatus("READY");
            return null;
        }).when(marketingJobService).applyPoll(any(MarketingJob.class), any(AsmJobView.class));

        scheduler.pollJobs();

        verify(marketingJobService).triggerPublish(572L);
    }

    @Test
    @DisplayName("ASM connect failure opens circuit and stops further GETs this cycle")
    void circuitBreakerStopsFurtherPolls() {
        MarketingJob a = MarketingJob.builder()
            .id(1L).remoteJobId("a").postId("p1").status("QUEUED").build();
        MarketingJob b = MarketingJob.builder()
            .id(2L).remoteJobId("b").postId("p2").status("QUEUED").build();

        when(marketingJobRepository.findByStatusIn(any())).thenReturn(List.of(a, b));
        when(marketingJobRepository.findDueAutoPublishJobs(any())).thenReturn(List.of());
        when(asmClient.getJob("a")).thenThrow(new AsmUnavailableException("connect timeout"));

        scheduler.pollJobs();

        verify(asmClient).getJob("a");
        verify(asmClient, never()).getJob("b");
        assertThat(a.getPollFailCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("monitorPublishingDelays detects READY auto-publish jobs stuck 30+ minutes")
    void monitoringDelayAlertsOnReadyJobsPast30Minutes() {
        Instant now = Instant.now();
        Instant fortyMinutesAgo = now.minus(40, ChronoUnit.MINUTES);

        MarketingJob delayed = MarketingJob.builder()
            .id(777L)
            .remoteJobId("asm-delayed")
            .postId("post_delayed")
            .status("READY")
            .autoPublish(true)
            .updatedAt(fortyMinutesAgo)
            .targets("[\"instagram_reels\",\"youtube_shorts\"]")
            .build();

        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findReadyJobsPastScheduleBy30Minutes(any()))
            .thenReturn(List.of(delayed));

        scheduler.monitorPublishingDelays();

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier).send(msg.capture());
        assertThat(msg.getValue())
            .contains("발행 지연")
            .contains("잡 #777")
            .contains("post_delayed")
            .contains("instagram_reels")
            .contains("분");
    }

    @Test
    @DisplayName("monitorPublishingDelays does nothing when no delayed jobs")
    void monitoringDelaySkipsWhenNoDelayedJobs() {
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findReadyJobsPastScheduleBy30Minutes(any()))
            .thenReturn(List.of());

        scheduler.monitorPublishingDelays();

        verify(telegramNotifier, never()).send(anyString());
    }

    @Test
    @DisplayName("monitorPublishingDelays disabled when ASM disabled")
    void monitoringDelaySkipsWhenAsmDisabled() {
        when(asmProperties.isEnabled()).thenReturn(false);

        scheduler.monitorPublishingDelays();

        verify(marketingJobRepository, never()).findReadyJobsPastScheduleBy30Minutes(any());
    }
}
