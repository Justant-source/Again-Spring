package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.MarketingHoldingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingQuotaServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;
    @Mock
    private MarketingHoldingRepository holdingRepository;

    @InjectMocks
    private MarketingQuotaService service;

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
    void getStatus_countsPerPlatform() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());
        when(holdingRepository.countCommittedForPlatformSince(
            eq("x_thread"), any())).thenReturn(1L);
        when(holdingRepository.countCommittedForPlatformSince(
            eq("instagram_feed"), any())).thenReturn(0L);
        when(holdingRepository.countCommittedForPlatformSince(
            eq("instagram_reels"), any())).thenReturn(2L);
        when(holdingRepository.countCommittedForPlatformSince(
            eq("youtube_shorts"), any())).thenReturn(1L);

        MarketingQuotaService.QuotaStatus status = service.getStatus();

        assertThat(status.remainingByPlatform().get("x_thread")).isEqualTo(2L);
        assertThat(status.remainingByPlatform().get("instagram_reels")).isEqualTo(1L);
        assertThat(status.videosToday()).isEqualTo(3L);
        assertThat(status.textsToday()).isEqualTo(1L);
        assertThat(status.dailyTextCap()).isEqualTo(6);
        assertThat(status.dailyVideoCap()).isEqualTo(6);
    }

    @Test
    void updatePlatformCaps_persistsFourKeysPlusLegacy() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());
        when(holdingRepository.countCommittedForPlatformSince(any(), any())).thenReturn(0L);

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
