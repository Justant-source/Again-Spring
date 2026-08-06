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

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link XThreadPublishTriggerScheduler}
 * (X all / top-3 video Reels+Shorts / remaining Instagram feed).
 */
@ExtendWith(MockitoExtension.class)
class XThreadPublishTriggerSchedulerTest {

    private static final Instant SINCE = Instant.parse("2026-08-02T08:43:52Z");
    private static final List<String> VIDEO_TARGETS = List.of("instagram_reels", "youtube_shorts");

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

        verify(marketingJobRepository, never()).findPostsEligibleForXThreadPublish(any(Instant.class), anyInt());
        verify(marketingJobRepository, never()).findPostsEligibleForInstagramFeedPublish(any(Instant.class), anyInt());
        verify(marketingJobRepository, never()).findPostsEligibleForVideoMarketing(any(Instant.class), anyInt());
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublishToXThread_asmDisabled_doesNotPoll() {
        when(asmProperties.isEnabled()).thenReturn(false);

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository, never()).findPostsEligibleForXThreadPublish(any(Instant.class), anyInt());
        verify(marketingJobRepository, never()).findPostsEligibleForInstagramFeedPublish(any(Instant.class), anyInt());
    }

    @Test
    void pollAndPublishToXThread_sinceUnset_failClosed_doesNotPoll() {
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn(null);

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository, never()).findPostsEligibleForXThreadPublish(any(Instant.class), anyInt());
        verify(marketingJobRepository, never()).findPostsEligibleForInstagramFeedPublish(any(Instant.class), anyInt());
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublishToXThread_sinceInvalid_failClosed_doesNotPoll() {
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn("not-an-instant");

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository, never()).findPostsEligibleForXThreadPublish(any(Instant.class), anyInt());
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublishToXThread_noEligiblePosts_doesNotCreateJobs() {
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn(SINCE.toString());
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(SINCE, 10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.countVideoJobsCreatedSince(any(Instant.class))).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(SINCE, 10))
            .thenReturn(Collections.emptyList());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository).findPostsEligibleForXThreadPublish(SINCE, 10);
        verify(marketingJobRepository).findPostsEligibleForVideoMarketing(eq(SINCE), anyInt());
        verify(marketingJobRepository).findPostsEligibleForInstagramFeedPublish(SINCE, 10);
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublish_singleEligible_createsXAndVideoNotFeed() {
        String postId = "post_123";
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn(SINCE.toString());
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(SINCE, 10))
            .thenReturn(Collections.singletonList(postId));
        when(marketingJobRepository.countVideoJobsCreatedSince(any(Instant.class))).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.singletonList(postId));
        // After video selection, feed pool excludes this post
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(SINCE, 10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.countActivePlatformJobs(postId, "x_thread")).thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(postId, "instagram_reels")).thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(postId, "youtube_shorts")).thenReturn(0L);

        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService).createJob(
            eq(postId), eq(Collections.singletonList("x_thread")), eq(true), eq("system:x-thread-trigger"));
        verify(marketingJobService).createJob(
            eq(postId), eq(VIDEO_TARGETS), eq(true), eq("system:video-marketing-trigger"));
        verify(marketingJobService, never()).createJob(
            anyString(), eq(Collections.singletonList("instagram_feed")), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublish_top3Video_restGoesToFeed() {
        List<String> ranked = Arrays.asList("v1", "v2", "v3", "f1", "f2");
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn(SINCE.toString());
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(SINCE, 10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.countVideoJobsCreatedSince(any(Instant.class))).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(ranked);
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(SINCE, 10))
            .thenReturn(Arrays.asList("f1", "f2"));
        when(marketingJobRepository.countActivePlatformJobs(anyString(), eq("instagram_reels"))).thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(anyString(), eq("youtube_shorts"))).thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(anyString(), eq("instagram_feed"))).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService).createJob(eq("v1"), eq(VIDEO_TARGETS), eq(true), eq("system:video-marketing-trigger"));
        verify(marketingJobService).createJob(eq("v2"), eq(VIDEO_TARGETS), eq(true), eq("system:video-marketing-trigger"));
        verify(marketingJobService).createJob(eq("v3"), eq(VIDEO_TARGETS), eq(true), eq("system:video-marketing-trigger"));
        verify(marketingJobService, never()).createJob(eq("f1"), eq(VIDEO_TARGETS), anyBoolean(), anyString());
        verify(marketingJobService).createJob(
            eq("f1"), eq(Collections.singletonList("instagram_feed")), eq(true), eq("system:instagram-feed-trigger"));
        verify(marketingJobService).createJob(
            eq("f2"), eq(Collections.singletonList("instagram_feed")), eq(true), eq("system:instagram-feed-trigger"));
    }

    @Test
    void pollAndPublish_dailyVideoCapReached_skipsVideo_enqueuesFeed() {
        String postId = "post_cap";
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn(SINCE.toString());
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(SINCE, 10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.countVideoJobsCreatedSince(any(Instant.class))).thenReturn(3L);
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(SINCE, 10))
            .thenReturn(Collections.singletonList(postId));
        when(marketingJobRepository.countActivePlatformJobs(postId, "instagram_feed")).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository, never()).findPostsEligibleForVideoMarketing(any(Instant.class), anyInt());
        verify(marketingJobService, never()).createJob(anyString(), eq(VIDEO_TARGETS), anyBoolean(), anyString());
        verify(marketingJobService).createJob(
            eq(postId), eq(Collections.singletonList("instagram_feed")), eq(true),
            eq("system:instagram-feed-trigger"));
    }

    @Test
    void pollAndPublish_dailyVideoCapPartial_enqueuesOnlyRemainingSlots() {
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn(SINCE.toString());
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(SINCE, 10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.countVideoJobsCreatedSince(any(Instant.class))).thenReturn(2L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Arrays.asList("a", "b", "c"));
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(SINCE, 10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.countActivePlatformJobs(anyString(), eq("instagram_reels"))).thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(anyString(), eq("youtube_shorts"))).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService, times(1)).createJob(
            anyString(), eq(VIDEO_TARGETS), eq(true), eq("system:video-marketing-trigger"));
        verify(marketingJobService).createJob(eq("a"), eq(VIDEO_TARGETS), eq(true), eq("system:video-marketing-trigger"));
        verify(marketingJobService, never()).createJob(eq("b"), eq(VIDEO_TARGETS), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublishToXThread_multipleEligiblePosts_createsJobsForEach() {
        List<String> postIds = Arrays.asList("post_123", "post_456", "post_789");
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn(SINCE.toString());
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(SINCE, 10)).thenReturn(postIds);
        when(marketingJobRepository.countVideoJobsCreatedSince(any(Instant.class))).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(SINCE, 10))
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
        when(asmProperties.getAutoPublishSince()).thenReturn(SINCE.toString());
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(SINCE, 10))
            .thenReturn(Collections.singletonList(postId));
        when(marketingJobRepository.countVideoJobsCreatedSince(any(Instant.class))).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(SINCE, 10))
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
        when(asmProperties.getAutoPublishSince()).thenReturn(SINCE.toString());
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(SINCE, 10))
            .thenReturn(Arrays.asList(postId1, postId2));
        when(marketingJobRepository.countVideoJobsCreatedSince(any(Instant.class))).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(SINCE, 10))
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
    void pollAndPublish_instagramOnlyEligible_createsIgJob() {
        String postId = "post_ig_only";
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn(SINCE.toString());
        when(marketingJobRepository.findPostsEligibleForXThreadPublish(SINCE, 10))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.countVideoJobsCreatedSince(any(Instant.class))).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.findPostsEligibleForInstagramFeedPublish(SINCE, 10))
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
