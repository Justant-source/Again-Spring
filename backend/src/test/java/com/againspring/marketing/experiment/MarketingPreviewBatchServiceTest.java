package com.againspring.marketing.experiment;

import com.againspring.domain.community.Post;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.marketing.MarketingJobService;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingPreviewBatchServiceTest {

    @Mock PostRepository postRepository;
    @Mock MarketingJobService jobService;
    @Mock MarketingJobRepository jobRepository;
    @Mock MarketingFormatArmService armService;

    private final Executor direct = Runnable::run;
    private MarketingPreviewBatchService service;

    private static FormatArm arm(String key) {
        return new FormatArm(key, "marketing_hook_a", "narrative", 0, "vote_reveal", 25, false, key);
    }

    private static Post post(String id) {
        Post p = new Post();
        p.setId(id);
        return p;
    }

    @BeforeEach
    void setUp() {
        service = new MarketingPreviewBatchService(postRepository, jobService, jobRepository, armService, direct, ms -> { });
        when(armService.find(anyString())).thenAnswer(i -> Optional.of(arm(i.getArgument(0))));
        org.mockito.Mockito.lenient().when(armService.listAll()).thenReturn(List.of(arm("baseline"), arm("short_reveal"), arm("dilemma")));
        Page<Post> page = new PageImpl<>(List.of(post("p1"), post("p2")));
        when(postRepository.findByVisibilityAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(any(), any(), any(Pageable.class)))
            .thenReturn(page);
    }

    @Test
    void start_rendersEachArmInOrderPerPost_excludingBaselineByDefault() {
        MarketingJob ready = MarketingJob.builder().id(1L).status("READY").build();
        when(jobService.createTestJobWithArm(anyString(), any(), anyString(), anyString())).thenReturn(ready);

        var started = service.start(5, null, null);

        assertThat(started.arms()).containsExactly("short_reveal", "dilemma");
        assertThat(started.platform()).isEqualTo("youtube_shorts");
        InOrder o = inOrder(jobService);
        o.verify(jobService).createTestJobWithArm("p1", List.of("youtube_shorts"), "preview-batch", "short_reveal");
        o.verify(jobService).createTestJobWithArm("p1", List.of("youtube_shorts"), "preview-batch", "dilemma");
        verify(jobService).createTestJobWithArm("p2", List.of("youtube_shorts"), "preview-batch", "dilemma");
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void start_oneArmFailing_doesNotStopTheRest() {
        MarketingJob ready = MarketingJob.builder().id(1L).status("READY").build();
        when(jobService.createTestJobWithArm(eq("p1"), any(), anyString(), eq("short_reveal")))
            .thenThrow(new IllegalStateException("이미 처리 중인 마케팅 잡이 있습니다"));
        when(jobService.createTestJobWithArm(anyString(), any(), anyString(), eq("dilemma"))).thenReturn(ready);
        when(jobService.createTestJobWithArm(eq("p2"), any(), anyString(), eq("short_reveal"))).thenReturn(ready);

        service.start(5, null, null);

        verify(jobService).createTestJobWithArm("p1", List.of("youtube_shorts"), "preview-batch", "dilemma");
    }

    @Test
    void start_waitsForNonTerminalJobBeforeNextArm() {
        MarketingJob running = MarketingJob.builder().id(9L).status("RUNNING").build();
        MarketingJob ready = MarketingJob.builder().id(9L).status("READY").build();
        when(jobService.createTestJobWithArm(anyString(), any(), anyString(), anyString())).thenReturn(running);
        when(jobRepository.findById(9L)).thenReturn(Optional.of(running), Optional.of(ready));

        service.start(1, List.of("short_reveal"), "youtube_shorts");

        verify(jobRepository, org.mockito.Mockito.atLeast(2)).findById(9L);
    }

    @Test
    void start_skipsPairsThatAreAlreadyReady_soRerunOnlyFillsGaps() {
        MarketingJob ready = MarketingJob.builder().id(1L).status("READY").build();
        when(jobRepository.countReadyTestJobs(eq("p1"), eq("short_reveal"), eq("youtube_shorts"), any())).thenReturn(1L);
        when(jobService.createTestJobWithArm(anyString(), any(), anyString(), anyString())).thenReturn(ready);

        service.start(5, null, null);

        verify(jobService, never()).createTestJobWithArm(eq("p1"), any(), anyString(), eq("short_reveal"));
        verify(jobService).createTestJobWithArm("p1", List.of("youtube_shorts"), "preview-batch", "dilemma");
        verify(jobService).createTestJobWithArm("p2", List.of("youtube_shorts"), "preview-batch", "short_reveal");
    }

    @Test
    void start_retriesWhenLlmCallWasInterrupted_thenContinues() {
        MarketingJob llmFailed = MarketingJob.builder().id(1L).status("FAILED").failureCode("VARIANT_LLM_ERROR").build();
        MarketingJob ready = MarketingJob.builder().id(2L).status("READY").build();
        when(jobService.createTestJobWithArm(eq("p1"), any(), anyString(), eq("short_reveal")))
            .thenReturn(llmFailed, llmFailed, ready);
        when(jobService.createTestJobWithArm(anyString(), any(), anyString(), eq("dilemma"))).thenReturn(ready);
        when(jobService.createTestJobWithArm(eq("p2"), any(), anyString(), eq("short_reveal"))).thenReturn(ready);

        service.start(5, null, null);

        verify(jobService, org.mockito.Mockito.times(3))
            .createTestJobWithArm("p1", List.of("youtube_shorts"), "preview-batch", "short_reveal");
    }

    @Test
    void start_givesUpAfterMaxRetriesButStillRunsNextArm() {
        MarketingJob llmFailed = MarketingJob.builder().id(1L).status("FAILED").failureCode("VARIANT_LLM_ERROR").build();
        MarketingJob ready = MarketingJob.builder().id(2L).status("READY").build();
        when(jobService.createTestJobWithArm(eq("p1"), any(), anyString(), eq("short_reveal"))).thenReturn(llmFailed);
        when(jobService.createTestJobWithArm(anyString(), any(), anyString(), eq("dilemma"))).thenReturn(ready);
        when(jobService.createTestJobWithArm(eq("p2"), any(), anyString(), eq("short_reveal"))).thenReturn(ready);

        service.start(5, null, null);

        verify(jobService, org.mockito.Mockito.times(1 + MarketingPreviewBatchService.MAX_LLM_RETRIES))
            .createTestJobWithArm("p1", List.of("youtube_shorts"), "preview-batch", "short_reveal");
        verify(jobService).createTestJobWithArm("p1", List.of("youtube_shorts"), "preview-batch", "dilemma");
    }

    @Test
    void start_rejectsConcurrentBatchAndBadInput() {
        Executor never = r -> { };
        service = new MarketingPreviewBatchService(postRepository, jobService, jobRepository, armService, never, ms -> { });
        service.start(5, null, null);
        assertThat(service.isRunning()).isTrue();
        assertThatThrownBy(() -> service.start(5, null, null)).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> service.start(5, null, "x_thread")).isInstanceOf(IllegalArgumentException.class);
        verify(jobService, never()).createTestJobWithArm(anyString(), any(), anyString(), anyString());
    }
}
