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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
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
    @DisplayName("carry-over runs after applyPoll so the next-day slot is not overwritten")
    void carryOverRunsAfterPoll() {
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
        // Second lookup (after poll) still sees expired auto-publish job
        when(marketingJobRepository.findExpiredScheduledJobs()).thenReturn(List.of(job));
        when(marketingJobRepository.findJobsByScheduledTimeRange(any(), anyLong()))
            .thenReturn(List.of());

        scheduler.pollJobs();

        InOrder order = inOrder(marketingJobService, marketingJobRepository, telegramNotifier);
        order.verify(marketingJobService).applyPoll(any(), any());
        order.verify(marketingJobRepository, atLeastOnce()).save(any());
        order.verify(telegramNotifier).send(anyString());

        assertThat(job.getRescheduledCount()).isEqualTo(1);
        assertThat(job.getScheduledPublishAt()).isEqualTo(past.plus(1, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("preview jobs (autoPublish=false) never trigger carry-over telegram")
    void previewJobsDoNotCarryOver() {
        when(marketingJobRepository.findByStatusIn(any())).thenReturn(List.of());
        when(marketingJobRepository.findDueAutoPublishJobs(any())).thenReturn(List.of());
        // Repository query filters auto_publish=1; empty list simulates that.
        when(marketingJobRepository.findExpiredScheduledJobs()).thenReturn(List.of());

        scheduler.pollJobs();

        verify(telegramNotifier, never()).send(anyString());
        verify(marketingJobRepository, never()).save(any());
        verify(marketingJobRepository).findExpiredScheduledJobs();
    }

    @Test
    @DisplayName("telegram message uses incremented carry-over count")
    void telegramIncludesCarryCount() {
        Instant past = Instant.parse("2026-08-12T11:30:00Z");
        MarketingJob job = MarketingJob.builder()
            .id(562L)
            .remoteJobId("asm-2")
            .postId("post_y")
            .status("RUNNING")
            .autoPublish(true)
            .scheduledPublishAt(past)
            .originalScheduledAt(past)
            .rescheduledCount(0)
            .build();

        when(marketingJobRepository.findByStatusIn(any())).thenReturn(List.of());
        when(marketingJobRepository.findDueAutoPublishJobs(any())).thenReturn(List.of());
        when(marketingJobRepository.findExpiredScheduledJobs()).thenReturn(List.of(job));
        when(marketingJobRepository.findJobsByScheduledTimeRange(any(), anyLong()))
            .thenReturn(List.of());

        scheduler.pollJobs();

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier).send(msg.capture());
        assertThat(msg.getValue()).contains("잡 #562").contains("1회째 이월");
        assertThat(job.getRescheduledCount()).isEqualTo(1);
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
        when(marketingJobRepository.findExpiredScheduledJobs()).thenReturn(List.of());

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
        when(marketingJobRepository.findExpiredScheduledJobs()).thenReturn(List.of());

        scheduler.pollJobs();

        verify(asmClient, never()).getJob(anyString());
        verify(marketingJobRepository).save(stale);
        assertThat(stale.getStatus()).isEqualTo("READY");
        assertThat(stale.getPollFailCount()).isZero();
        assertThat(stale.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("late READY job is published in the same reconciliation cycle")
    void publishesImmediatelyWhenRemoteBecomesReadyAfterSlot() {
        Instant past = Instant.now().minus(10, ChronoUnit.MINUTES);
        MarketingJob job = MarketingJob.builder()
            .id(572L).remoteJobId("asm-late-ready").postId("post_late").status("WAITING_EXTERNAL")
            .autoPublish(true).scheduledPublishAt(past).build();

        when(marketingJobRepository.findByStatusIn(any())).thenReturn(List.of(job));
        when(marketingJobRepository.findDueAutoPublishJobs(any())).thenReturn(List.of());
        when(marketingJobRepository.findExpiredScheduledJobs()).thenReturn(List.of());
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
        when(marketingJobRepository.findExpiredScheduledJobs()).thenReturn(List.of());
        when(asmClient.getJob("a")).thenThrow(new AsmUnavailableException("connect timeout"));

        scheduler.pollJobs();

        verify(asmClient).getJob("a");
        verify(asmClient, never()).getJob("b");
        assertThat(a.getPollFailCount()).isEqualTo(1);
    }
}
