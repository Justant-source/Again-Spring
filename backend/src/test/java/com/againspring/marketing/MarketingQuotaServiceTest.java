package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.MarketingHoldingRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketingQuotaServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;
    @Mock
    private MarketingHoldingRepository holdingRepository;
    @Mock
    private MarketingJobRepository jobRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MarketingQuotaService service;

    private static MarketingJob publishedJob(String postId, String publications) {
        return MarketingJob.builder().postId(postId).publications(publications).build();
    }

    @Test
    void getPlatformCaps_missingSettings_defaultsThreeEach() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        MarketingQuotaService.PlatformCaps caps = service.getPlatformCaps();

        assertThat(caps.xThread()).isEqualTo(3);
        assertThat(caps.instagramFeed()).isEqualTo(3);
        assertThat(caps.instagramReels()).isEqualTo(3);
        assertThat(caps.youtubeShorts()).isEqualTo(3);
    }

    @Test
    void getStatus_countsOnlyActuallyPublishedPerPlatform() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());
        when(jobRepository.findPublishAttemptsSince(any())).thenReturn(List.of(
            publishedJob("post_a", "[{\"platform\":\"x_thread\",\"state\":\"PUBLISHED\",\"url\":\"u1\"}]"),
            publishedJob("post_b", "[{\"platform\":\"instagram_reels\",\"state\":\"PUBLISHED\",\"url\":\"u2\"},"
                + "{\"platform\":\"youtube_shorts\",\"state\":\"PUBLISHED\",\"url\":\"u3\"}]"),
            // PARTIAL job: one target published, one failed — only the published one counts.
            publishedJob("post_c", "[{\"platform\":\"instagram_reels\",\"state\":\"PUBLISHED\",\"url\":\"u4\"},"
                + "{\"platform\":\"youtube_shorts\",\"state\":\"FAILED\",\"url\":null}]")
        ));

        MarketingQuotaService.QuotaStatus status = service.getStatus();

        assertThat(status.remainingByPlatform().get("x_thread")).isEqualTo(2L);
        assertThat(status.remainingByPlatform().get("instagram_reels")).isEqualTo(1L);
        assertThat(status.usedTodayByPlatform().get("youtube_shorts")).isEqualTo(1L);
        assertThat(status.videosToday()).isEqualTo(3L);
        assertThat(status.textsToday()).isEqualTo(1L);
        assertThat(status.dailyTextCap()).isEqualTo(6);
        assertThat(status.dailyVideoCap()).isEqualTo(6);
    }

    @Test
    void getStatus_readyOrFailedJobsDoNotConsumeQuota() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());
        // A job sitting READY (never clicked) or FAILED never shows up in
        // findPublishAttemptsSince at all (status filter excludes them) — simulate
        // that by returning an empty list, matching the real query's behavior.
        when(jobRepository.findPublishAttemptsSince(any())).thenReturn(List.of());

        MarketingQuotaService.QuotaStatus status = service.getStatus();

        assertThat(status.remainingByPlatform().get("youtube_shorts")).isEqualTo(3L);
        assertThat(status.videosToday()).isEqualTo(0L);
    }

    @Test
    void updatePlatformCaps_persistsFourKeysPlusLegacy() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());
        when(jobRepository.findPublishAttemptsSince(any())).thenReturn(List.of());

        service.updatePlatformCaps(new MarketingQuotaService.PlatformCaps(2, 4, 1, 5), "admin");

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, atLeast(6)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SystemSetting::getSettingKey)
            .contains(
                "marketing.cap.x_thread",
                "marketing.cap.instagram_feed",
                "marketing.cap.instagram_reels",
                "marketing.cap.youtube_shorts",
                MarketingQuotaService.KEY_TEXT_CAP,
                MarketingQuotaService.KEY_VIDEO_CAP);
    }

    @Test
    void validatePlatformCaps_negative_throws() {
        assertThatThrownBy(() -> MarketingQuotaService.validatePlatformCaps(
            new MarketingQuotaService.PlatformCaps(-1, 3, 3, 3)))
            .isInstanceOf(ResponseStatusException.class);
    }
}
