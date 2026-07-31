package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.repository.marketing.MarketingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link XThreadPublishTriggerScheduler}.
 * Tests the automatic triggering of X thread marketing job creation.
 */
@ExtendWith(MockitoExtension.class)
class XThreadPublishTriggerSchedulerTest {

    @Mock
    private AsmClient asmClient;

    @Mock
    private MarketingJobRepository marketingJobRepository;

    @Mock
    private MarketingJobService marketingJobService;

    @Mock
    private AsmProperties asmProperties;

    @InjectMocks
    private XThreadPublishTriggerScheduler scheduler;

    @BeforeEach
    void enableTrigger() {
        // triggerEnabled is @Value-injected in production (default false — see the
        // field's javadoc for why); Mockito's @InjectMocks doesn't populate @Value
        // fields, so it stays at Java's default (false) unless set here. Tests below
        // exercise the scheduler's real logic, so flip it on explicitly per test run.
        ReflectionTestUtils.setField(scheduler, "triggerEnabled", true);
    }

    // ── Test 0: triggerDisabled_shouldSkip ─────────────────────────────────
    // The safety gate itself: default-false trigger must block everything, even
    // if ASM is otherwise enabled and posts are eligible. Regression test for the
    // 2026-07-31 incident (see the field's javadoc) — a dev redeploy created 10
    // real ASM jobs with auto_publish=true against the shared X account before
    // this flag existed.

    @Test
    void pollAndPublishToXThread_triggerDisabled_doesNotPoll() {
        // Given — override the @BeforeEach opt-in back to the real production default
        ReflectionTestUtils.setField(scheduler, "triggerEnabled", false);

        // When
        scheduler.pollAndPublishToXThread();

        // Then
        verify(marketingJobRepository, never()).findPostsEligibleForXThreadPublish(any(Integer.class));
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    // ── Test 1: asmDisabled_shouldSkip ─────────────────────────────────────

    @Test
    void pollAndPublishToXThread_asmDisabled_doesNotPoll() {
        // Given
        when(asmProperties.isEnabled()).thenReturn(false);

        // When
        scheduler.pollAndPublishToXThread();

        // Then
        verify(marketingJobRepository, never()).findPostsEligibleForXThreadPublish(any(Integer.class));
    }

    // ── Test 2: noEligiblePosts_shouldSkip ─────────────────────────────────

    @Test
    void pollAndPublishToXThread_noEligiblePosts_doesNotCreateJobs() {
        // Given
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(Collections.emptyList());

        // When
        scheduler.pollAndPublishToXThread();

        // Then
        verify(marketingJobRepository).findPostsEligibleForXThreadPublish(10);
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    // ── Test 3: singleEligiblePost_createsJob ─────────────────────────────

    @Test
    void pollAndPublishToXThread_singleEligiblePost_createsJob() {
        // Given
        String postId = "post_123";
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(Collections.singletonList(postId));

        MarketingJob job = MarketingJob.builder()
            .id(1L)
            .postId(postId)
            .status("REQUESTED")
            .build();

        when(marketingJobService.createJob(
            eq(postId),
            eq(Collections.singletonList("x_thread")),
            eq(true),
            anyString()
        )).thenReturn(job);

        // When
        scheduler.pollAndPublishToXThread();

        // Then
        verify(marketingJobRepository).findPostsEligibleForXThreadPublish(10);
        verify(marketingJobService).createJob(
            eq(postId),
            eq(Collections.singletonList("x_thread")),
            eq(true),
            eq("system:x-thread-trigger")
        );
    }

    // ── Test 4: multipleEligiblePosts_createsJobsIndependently ────────────

    @Test
    void pollAndPublishToXThread_multipleEligiblePosts_createsJobsForEach() {
        // Given
        String postId1 = "post_123";
        String postId2 = "post_456";
        String postId3 = "post_789";
        List<String> postIds = Arrays.asList(postId1, postId2, postId3);

        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(postIds);

        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder()
                .id(1L)
                .status("REQUESTED")
                .build());

        // When
        scheduler.pollAndPublishToXThread();

        // Then
        verify(marketingJobRepository).findPostsEligibleForXThreadPublish(10);
        verify(marketingJobService, times(3)).createJob(
            anyString(),
            eq(Collections.singletonList("x_thread")),
            eq(true),
            eq("system:x-thread-trigger")
        );
    }

    // ── Test 5: alreadyHasActiveJob_skipsPost ──────────────────────────────

    @Test
    void pollAndPublishToXThread_alreadyHasActiveXThreadJob_skipsPost() {
        // Given
        String postId = "post_123";
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(Collections.singletonList(postId));

        // Double-check: post has an active x_thread job
        when(marketingJobRepository.countActivePlatformJobs(postId, "x_thread"))
            .thenReturn(1L);

        // When
        scheduler.pollAndPublishToXThread();

        // Then
        verify(marketingJobRepository).findPostsEligibleForXThreadPublish(10);
        verify(marketingJobRepository).countActivePlatformJobs(postId, "x_thread");
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    // ── Test 6: jobCreationThrowsException_continuesWithNextPost ───────────

    @Test
    void pollAndPublishToXThread_jobCreationFails_continuesWithNextPost() {
        // Given
        String postId1 = "post_123";
        String postId2 = "post_456";
        List<String> postIds = Arrays.asList(postId1, postId2);

        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(postIds);

        when(marketingJobRepository.countActivePlatformJobs(postId1, "x_thread"))
            .thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(postId2, "x_thread"))
            .thenReturn(0L);

        // First job creation throws exception
        when(marketingJobService.createJob(
            eq(postId1),
            any(List.class),
            anyBoolean(),
            anyString()
        )).thenThrow(new IllegalArgumentException("Post not found"));

        // Second job creation succeeds
        when(marketingJobService.createJob(
            eq(postId2),
            any(List.class),
            anyBoolean(),
            anyString()
        )).thenReturn(MarketingJob.builder()
            .id(2L)
            .status("REQUESTED")
            .build());

        // When
        scheduler.pollAndPublishToXThread();

        // Then
        verify(marketingJobService, times(2)).createJob(
            anyString(),
            any(List.class),
            anyBoolean(),
            anyString()
        );
    }

    // ── Test 7: jobCreationThrowsIllegalState_continuesWithNextPost ────────

    @Test
    void pollAndPublishToXThread_jobAlreadyProcessing_skipsAndContinues() {
        // Given
        String postId1 = "post_123";
        String postId2 = "post_456";
        List<String> postIds = Arrays.asList(postId1, postId2);

        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(postIds);

        when(marketingJobRepository.countActivePlatformJobs(postId1, "x_thread"))
            .thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(postId2, "x_thread"))
            .thenReturn(0L);

        // First job creation throws IllegalStateException (already processing)
        when(marketingJobService.createJob(
            eq(postId1),
            any(List.class),
            anyBoolean(),
            anyString()
        )).thenThrow(new IllegalStateException("이미 처리 중인 마케팅 잡이 있습니다"));

        // Second job creation succeeds
        when(marketingJobService.createJob(
            eq(postId2),
            any(List.class),
            anyBoolean(),
            anyString()
        )).thenReturn(MarketingJob.builder()
            .id(2L)
            .status("REQUESTED")
            .build());

        // When
        scheduler.pollAndPublishToXThread();

        // Then
        verify(marketingJobService, times(2)).createJob(
            anyString(),
            any(List.class),
            anyBoolean(),
            anyString()
        );
    }

    // ── Test 8: correctPlatformTarget ──────────────────────────────────────

    @Test
    void pollAndPublishToXThread_createsJobWithXThreadPlatform() {
        // Given
        String postId = "post_123";
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(Collections.singletonList(postId));

        when(marketingJobRepository.countActivePlatformJobs(postId, "x_thread"))
            .thenReturn(0L);

        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder()
                .id(1L)
                .status("REQUESTED")
                .build());

        // When
        scheduler.pollAndPublishToXThread();

        // Then
        verify(marketingJobService).createJob(
            eq(postId),
            eq(Collections.singletonList("x_thread")),
            eq(true),
            eq("system:x-thread-trigger")
        );
    }

    // ── Test 9: correctAutoPublishFlag ────────────────────────────────────

    @Test
    void pollAndPublishToXThread_createsJobWithAutoPublishTrue() {
        // Given
        String postId = "post_123";
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(Collections.singletonList(postId));

        when(marketingJobRepository.countActivePlatformJobs(postId, "x_thread"))
            .thenReturn(0L);

        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder()
                .id(1L)
                .status("REQUESTED")
                .build());

        // When
        scheduler.pollAndPublishToXThread();

        // Then
        verify(marketingJobService).createJob(
            eq(postId),
            any(List.class),
            eq(true), // autoPublish must be true
            anyString()
        );
    }
}
