package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.repository.marketing.MarketingJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(marketingJobRepository.hasActivePlatformJob(postId, "x_thread"))
            .thenReturn(true);

        // When
        scheduler.pollAndPublishToXThread();

        // Then
        verify(marketingJobRepository).findPostsEligibleForXThreadPublish(10);
        verify(marketingJobRepository).hasActivePlatformJob(postId, "x_thread");
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

        when(marketingJobRepository.hasActivePlatformJob(postId1, "x_thread"))
            .thenReturn(false);
        when(marketingJobRepository.hasActivePlatformJob(postId2, "x_thread"))
            .thenReturn(false);

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

        when(marketingJobRepository.hasActivePlatformJob(postId1, "x_thread"))
            .thenReturn(false);
        when(marketingJobRepository.hasActivePlatformJob(postId2, "x_thread"))
            .thenReturn(false);

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

        when(marketingJobRepository.hasActivePlatformJob(postId, "x_thread"))
            .thenReturn(false);

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

        when(marketingJobRepository.hasActivePlatformJob(postId, "x_thread"))
            .thenReturn(false);

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
