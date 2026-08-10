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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
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
    void getCaps_missingSettings_returnsDefaults() {
        when(systemSettingRepository.findById(MarketingQuotaService.KEY_TEXT_CAP)).thenReturn(Optional.empty());
        when(systemSettingRepository.findById(MarketingQuotaService.KEY_VIDEO_CAP)).thenReturn(Optional.empty());

        MarketingQuotaService.Caps caps = service.getCaps();

        assertThat(caps.dailyTextCap()).isEqualTo(6);
        assertThat(caps.dailyVideoCap()).isEqualTo(3);
    }

    @Test
    void getStatus_computesRemainingPoolFromCommittedHoldings() {
        when(systemSettingRepository.findById(MarketingQuotaService.KEY_TEXT_CAP))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingQuotaService.KEY_TEXT_CAP).settingValue("6").build()));
        when(systemSettingRepository.findById(MarketingQuotaService.KEY_VIDEO_CAP))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingQuotaService.KEY_VIDEO_CAP).settingValue("3").build()));
        // S4: story-based pool — 3 COMMITTED (2 video + 1 text); stray test jobs ignored
        when(holdingRepository.countCommittedVideosSince(any(Instant.class))).thenReturn(2L);
        when(holdingRepository.countCommittedSince(any(Instant.class))).thenReturn(3L);

        MarketingQuotaService.QuotaStatus status = service.getStatus();

        assertThat(status.videosToday()).isEqualTo(2L);
        assertThat(status.textsToday()).isEqualTo(1L);
        assertThat(status.remainingPool()).isEqualTo(3L);
    }

    @Test
    void getStatus_ignoresUncommittedVideoJobs_keepsVideoSlots() {
        when(systemSettingRepository.findById(MarketingQuotaService.KEY_TEXT_CAP))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingQuotaService.KEY_TEXT_CAP).settingValue("6").build()));
        when(systemSettingRepository.findById(MarketingQuotaService.KEY_VIDEO_CAP))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingQuotaService.KEY_VIDEO_CAP).settingValue("3").build()));
        // Only 1 real COMMITTED video today; 3 test youtube_shorts jobs must not count
        when(holdingRepository.countCommittedVideosSince(any(Instant.class))).thenReturn(1L);
        when(holdingRepository.countCommittedSince(any(Instant.class))).thenReturn(1L);

        MarketingQuotaService.QuotaStatus status = service.getStatus();

        assertThat(status.videosToday()).isEqualTo(1L);
        assertThat(status.textsToday()).isEqualTo(0L);
        assertThat(status.remainingPool()).isEqualTo(5L);
        // Effective video slots for board = cap - videosToday = 2 (> 0)
        assertThat(status.dailyVideoCap() - status.videosToday()).isEqualTo(2L);
    }

    @Test
    void updateCaps_persistsBothKeys() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());
        when(holdingRepository.countCommittedVideosSince(any(Instant.class))).thenReturn(0L);
        when(holdingRepository.countCommittedSince(any(Instant.class))).thenReturn(0L);

        service.updateCaps(8, 2, "admin@test");

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SystemSetting::getSettingKey)
            .containsExactlyInAnyOrder(
                MarketingQuotaService.KEY_TEXT_CAP,
                MarketingQuotaService.KEY_VIDEO_CAP);
    }

    @Test
    void validate_videoCapAboveTextCap_throws() {
        assertThatThrownBy(() -> MarketingQuotaService.validate(6, 7))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void validate_textCapOutOfRange_throws() {
        assertThatThrownBy(() -> MarketingQuotaService.validate(0, 0))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> MarketingQuotaService.validate(51, 3))
            .isInstanceOf(ResponseStatusException.class);
    }
}
