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
 * (shared daily pool: video first → remaining text = X + IG feed).
 */
@ExtendWith(MockitoExtension.class)
class XThreadPublishTriggerSchedulerTest {

    private static final Instant SINCE = Instant.parse("2026-08-02T08:43:52Z");
    private static final Instant START_OF_DAY = Instant.parse("2026-08-06T15:00:00Z");
    private static final List<String> VIDEO_TARGETS = List.of("instagram_reels", "youtube_shorts");
    private static final MarketingQuotaService.Caps DEFAULT_CAPS = new MarketingQuotaService.Caps(6, 3);

    @Mock
    private MarketingJobRepository marketingJobRepository;

    @Mock
    private MarketingJobService marketingJobService;

    @Mock
    private AsmProperties asmProperties;

    @Mock
    private MarketingQuotaService marketingQuotaService;

    @InjectMocks
    private XThreadPublishTriggerScheduler scheduler;

    @BeforeEach
    void enableTrigger() {
        ReflectionTestUtils.setField(scheduler, "triggerEnabled", true);
    }

    private void stubEnabledWithSince() {
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn(SINCE.toString());
        when(marketingQuotaService.getCaps()).thenReturn(DEFAULT_CAPS);
        when(marketingQuotaService.startOfTodayKst()).thenReturn(START_OF_DAY);
    }

    @Test
    void pollAndPublishToXThread_triggerDisabled_doesNotPoll() {
        ReflectionTestUtils.setField(scheduler, "triggerEnabled", false);

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository, never()).findPostsEligibleForVideoMarketing(any(Instant.class), anyInt());
        verify(marketingJobRepository, never()).findPostsEligibleForTextMarketing(any(Instant.class), anyInt());
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublishToXThread_asmDisabled_doesNotPoll() {
        when(asmProperties.isEnabled()).thenReturn(false);

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository, never()).findPostsEligibleForVideoMarketing(any(Instant.class), anyInt());
        verify(marketingJobRepository, never()).findPostsEligibleForTextMarketing(any(Instant.class), anyInt());
    }

    @Test
    void pollAndPublishToXThread_sinceUnset_failClosed_doesNotPoll() {
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn(null);

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository, never()).findPostsEligibleForVideoMarketing(any(Instant.class), anyInt());
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublishToXThread_sinceInvalid_failClosed_doesNotPoll() {
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn("not-an-instant");

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository, never()).findPostsEligibleForVideoMarketing(any(Instant.class), anyInt());
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublish_poolExhausted_doesNotCreateJobs() {
        stubEnabledWithSince();
        when(marketingJobRepository.countVideoJobsCreatedSince(START_OF_DAY)).thenReturn(3L);
        when(marketingJobRepository.countTextSlotsCreatedSince(START_OF_DAY)).thenReturn(3L);

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository, never()).findPostsEligibleForVideoMarketing(any(Instant.class), anyInt());
        verify(marketingJobRepository, never()).findPostsEligibleForTextMarketing(any(Instant.class), anyInt());
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublish_noEligiblePosts_doesNotCreateJobs() {
        stubEnabledWithSince();
        when(marketingJobRepository.countVideoJobsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.countTextSlotsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.findPostsEligibleForTextMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.emptyList());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository).findPostsEligibleForVideoMarketing(eq(SINCE), anyInt());
        verify(marketingJobRepository).findPostsEligibleForTextMarketing(eq(SINCE), anyInt());
        verify(marketingJobService, never()).createJob(anyString(), any(List.class), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublish_videoSelected_getsReelsShorts_notX() {
        String postId = "post_123";
        stubEnabledWithSince();
        when(marketingJobRepository.countVideoJobsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.countTextSlotsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.singletonList(postId));
        when(marketingJobRepository.findPostsEligibleForTextMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.countActivePlatformJobs(postId, "instagram_reels")).thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(postId, "youtube_shorts")).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService).createJob(
            eq(postId), eq(VIDEO_TARGETS), eq(true), eq("system:video-marketing-trigger"));
        verify(marketingJobService, never()).createJob(
            anyString(), eq(Collections.singletonList("x_thread")), anyBoolean(), anyString());
        verify(marketingJobService, never()).createJob(
            anyString(), eq(Collections.singletonList("instagram_feed")), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublish_twoVideos_fourTextSlots_fromSharedPoolOfSix() {
        List<String> ranked = Arrays.asList("v1", "v2", "v3", "t1", "t2", "t3", "t4", "t5");
        stubEnabledWithSince();
        when(marketingJobRepository.countVideoJobsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.countTextSlotsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(ranked.subList(0, 2)); // only 2 video-worthy
        when(marketingJobRepository.findPostsEligibleForTextMarketing(eq(SINCE), anyInt()))
            .thenReturn(Arrays.asList("t1", "t2", "t3", "t4", "t5"));
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService).createJob(eq("v1"), eq(VIDEO_TARGETS), eq(true), eq("system:video-marketing-trigger"));
        verify(marketingJobService).createJob(eq("v2"), eq(VIDEO_TARGETS), eq(true), eq("system:video-marketing-trigger"));
        verify(marketingJobService, never()).createJob(eq("v3"), eq(VIDEO_TARGETS), anyBoolean(), anyString());
        // textSlots = 6 - 2 = 4
        verify(marketingJobService).createJob(
            eq("t1"), eq(Collections.singletonList("x_thread")), eq(true), eq("system:x-thread-trigger"));
        verify(marketingJobService).createJob(
            eq("t1"), eq(Collections.singletonList("instagram_feed")), eq(true), eq("system:instagram-feed-trigger"));
        verify(marketingJobService).createJob(
            eq("t4"), eq(Collections.singletonList("x_thread")), eq(true), eq("system:x-thread-trigger"));
        verify(marketingJobService, never()).createJob(
            eq("t5"), eq(Collections.singletonList("x_thread")), anyBoolean(), anyString());
    }

    @Test
    void pollAndPublish_videoCapReached_usesRemainingPoolForTextOnly() {
        String postId = "post_text";
        stubEnabledWithSince();
        when(marketingJobRepository.countVideoJobsCreatedSince(START_OF_DAY)).thenReturn(3L);
        when(marketingJobRepository.countTextSlotsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForTextMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.singletonList(postId));
        when(marketingJobRepository.countActivePlatformJobs(postId, "x_thread")).thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(postId, "instagram_feed")).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobRepository, never()).findPostsEligibleForVideoMarketing(any(Instant.class), anyInt());
        verify(marketingJobService, never()).createJob(anyString(), eq(VIDEO_TARGETS), anyBoolean(), anyString());
        verify(marketingJobService).createJob(
            eq(postId), eq(Collections.singletonList("x_thread")), eq(true), eq("system:x-thread-trigger"));
        verify(marketingJobService).createJob(
            eq(postId), eq(Collections.singletonList("instagram_feed")), eq(true),
            eq("system:instagram-feed-trigger"));
    }

    @Test
    void pollAndPublish_partialVideoCap_enqueuesOnlyRemainingVideoSlots() {
        stubEnabledWithSince();
        when(marketingJobRepository.countVideoJobsCreatedSince(START_OF_DAY)).thenReturn(2L);
        when(marketingJobRepository.countTextSlotsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Arrays.asList("a", "b", "c"));
        when(marketingJobRepository.findPostsEligibleForTextMarketing(eq(SINCE), anyInt()))
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
    void pollAndPublish_textOnly_createsXAndFeed() {
        List<String> postIds = Arrays.asList("post_123", "post_456");
        stubEnabledWithSince();
        when(marketingJobRepository.countVideoJobsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.countTextSlotsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.findPostsEligibleForTextMarketing(eq(SINCE), anyInt()))
            .thenReturn(postIds);
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService, times(2)).createJob(
            anyString(), eq(Collections.singletonList("x_thread")), eq(true), eq("system:x-thread-trigger"));
        verify(marketingJobService, times(2)).createJob(
            anyString(), eq(Collections.singletonList("instagram_feed")), eq(true),
            eq("system:instagram-feed-trigger"));
    }

    @Test
    void pollAndPublish_alreadyHasActiveXThreadJob_skipsXButStillTriesFeed() {
        String postId = "post_123";
        stubEnabledWithSince();
        when(marketingJobRepository.countVideoJobsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.countTextSlotsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.findPostsEligibleForTextMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.singletonList(postId));
        when(marketingJobRepository.countActivePlatformJobs(postId, "x_thread")).thenReturn(1L);
        when(marketingJobRepository.countActivePlatformJobs(postId, "instagram_feed")).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService, never()).createJob(
            anyString(), eq(Collections.singletonList("x_thread")), anyBoolean(), anyString());
        verify(marketingJobService).createJob(
            eq(postId), eq(Collections.singletonList("instagram_feed")), eq(true),
            eq("system:instagram-feed-trigger"));
    }

    @Test
    void pollAndPublish_jobCreationFails_continuesWithNextPost() {
        String postId1 = "post_123";
        String postId2 = "post_456";
        stubEnabledWithSince();
        when(marketingJobRepository.countVideoJobsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.countTextSlotsCreatedSince(START_OF_DAY)).thenReturn(0L);
        when(marketingJobRepository.findPostsEligibleForVideoMarketing(eq(SINCE), anyInt()))
            .thenReturn(Collections.emptyList());
        when(marketingJobRepository.findPostsEligibleForTextMarketing(eq(SINCE), anyInt()))
            .thenReturn(Arrays.asList(postId1, postId2));
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);

        when(marketingJobService.createJob(eq(postId1), any(List.class), anyBoolean(), anyString()))
            .thenThrow(new IllegalArgumentException("Post not found"));
        when(marketingJobService.createJob(eq(postId2), any(List.class), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(2L).status("REQUESTED").build());

        scheduler.pollAndPublishToXThread();

        verify(marketingJobService, times(2)).createJob(
            eq(postId2), any(List.class), anyBoolean(), anyString());
    }
}
