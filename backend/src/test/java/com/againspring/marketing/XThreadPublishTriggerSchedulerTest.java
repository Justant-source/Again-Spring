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
 * Unit tests for {@link XThreadPublishTriggerScheduler} (X + Instagram 24h auto-publish).
 */
@ExtendWith(MockitoExtension.class)
class XThreadPublishTriggerSchedulerTest {

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
        ReflectionTestUtils.setField(scheduler, "triggerEnabled", true);
    }

    @Test
    void pollAndPublishToXThread_triggerDisabled_doesNotPoll() {
        ReflectionTestUtils.setField(scheduler, "triggerEnabled", false);

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository, never()).findPostsEligibleForXThreadPublish(any(Integer.class));
        verify(marketingJobRepository, never()).findPostsEligibleForInstagramFeedPublish(any(Integer.class));
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublishToXThread_asmDisabled_doesNotPoll() {
        when(asmProperties.isEnabled()).thenReturn(false);

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository, never()).findPostsEligibleForXThreadPublish(any(Integer.class));
        verify(marketingJobRepository, never()).findPostsEligibleForInstagramFeedPublish(any(Integer.class));
    }

    @Test
    void pollAndPublishToXThread_noEligiblePosts_doesNotCreateJobs() {
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(10))
            .thenReturn(Collections.emptyList());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository).findPostsEligibleForXThreadPublish(10);
        verify(marketingJobRepository).findPostsEligibleForInstagramFeedPublish(10);
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublishToXThread_singleEligiblePost_createsXAndIgJobs() {
        String postId = "post_123";
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(Collections.singletonList(postId));
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(10))
            .thenReturn(Collections.singletonList(postId));
        when(marketingJobRepository.countActivePlatformJobs(postId, "x_thread")).thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(postId, "instagram_feed")).thenReturn(0L);

        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService).createJob(
            eq(postId), eq(Collections.singletonList("x_thread")), eq(true), eq("system:x-thread-trigger"));
        verify(marketingJobService).createJob(
            eq(postId), eq(Collections.singletonList("instagram_feed")), eq(true),
            eq("system:instagram-feed-trigger"));
    }

    @Test
    void pollAndPublishToXThread_multipleEligiblePosts_createsJobsForEach() {
        List<String> postIds = Arrays.asList("post_123", "post_456", "post_789");
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10)).thenReturn(postIds);
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.countActivePlatformJobs(anyString(), eq("x_thread"))).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService, times(3)).createJob(
            anyString(), eq(Collections.singletonList("x_thread")), eq(true), eq("system:x-thread-trigger"));
    }

    @Test
    void pollAndPublishToXThread_alreadyHasActiveXThreadJob_skipsPost() {
        String postId = "post_123";
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(Collections.singletonList(postId));
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.countActivePlatformJobs(postId, "x_thread")).thenReturn(1L);

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository).countActivePlatformJobs(postId, "x_thread");
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublishToXThread_jobCreationFails_continuesWithNextPost() {
        String postId1 = "post_123";
        String postId2 = "post_456";
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(Arrays.asList(postId1, postId2));
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.countActivePlatformJobs(postId1, "x_thread")).thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(postId2, "x_thread")).thenReturn(0L);

        when(marketingJobService.createJob(eq(postId1), any(List.class), anyBoolean(), anyString()))
            .thenThrow(new IllegalArgumentException("Post not found"));
        when(marketingJobService.createJob(eq(postId2), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(2L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService, times(2)).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublishToXThread_createsJobWithAutoPublishTrue() {
        String postId = "post_123";
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(Collections.singletonList(postId));
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.countActivePlatformJobs(postId, "x_thread")).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService).createJob(eq(postId), any(List.class), eq(true), anyString());
    }

    @Test
    void pollAndPublish_instagramOnlyEligible_createsIgJob() {
        String postId = "post_ig_only";
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(10))
            .thenReturn(Collections.singletonList(postId));
        when(marketingJobRepository.countActivePlatformJobs(postId, "instagram_feed")).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(9L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService).createJob(
            eq(postId),
            eq(Collections.singletonList("instagram_feed")),
            eq(true),
            eq("system:instagram-feed-trigger"));
        verify(marketingJobService, never()).createJob(
            anyString(), eq(Collections.singletonList("x_thread")), anyBoolean(), anyString());
    }
}
