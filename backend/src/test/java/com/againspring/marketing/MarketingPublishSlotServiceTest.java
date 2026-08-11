package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingPublishSlotServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @InjectMocks
    private MarketingPublishSlotService service;

    @Test
    void getSlots_missingSettings_returnsDefaults() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        MarketingPublishSlotService.Slots slots = service.getSlots();

        assertThat(slots.instagramFeed()).isEqualTo("20:00");
        assertThat(slots.instagramReels()).isEqualTo("20:30");
        assertThat(slots.youtubeShorts()).isEqualTo("20:30");
        assertThat(slots.xThread()).isEqualTo("21:30");
    }

    @Test
    void nextOccurrence_beforeTodaySlot_schedulesToday() {
        // 2026-08-11 18:00 KST
        Instant now = LocalDate.of(2026, 8, 11).atTime(18, 0).atZone(KST).toInstant();
        Instant slot = MarketingPublishSlotService.nextOccurrence(now, LocalTime.of(20, 0));

        assertThat(slot).isEqualTo(
            LocalDate.of(2026, 8, 11).atTime(20, 0).atZone(KST).toInstant());
    }

    @Test
    void nextOccurrence_atExactSlot_schedulesTomorrow() {
        // At-or-after today's slot → tomorrow (do not schedule into the past / zero-lead window)
        Instant now = LocalDate.of(2026, 8, 11).atTime(20, 0).atZone(KST).toInstant();
        Instant slot = MarketingPublishSlotService.nextOccurrence(now, LocalTime.of(20, 0));

        assertThat(slot).isEqualTo(
            LocalDate.of(2026, 8, 12).atTime(20, 0).atZone(KST).toInstant());
    }

    @Test
    void nextOccurrence_afterTodaySlot_schedulesTomorrow() {
        Instant now = LocalDate.of(2026, 8, 11).atTime(22, 15).atZone(KST).toInstant();
        Instant slot = MarketingPublishSlotService.nextOccurrence(now, LocalTime.of(21, 30));

        assertThat(slot).isEqualTo(
            LocalDate.of(2026, 8, 12).atTime(21, 30).atZone(KST).toInstant());
    }

    @Test
    void nextSlotForPlatform_usesStoredOverride() {
        when(systemSettingRepository.findById(MarketingPublishSlotService.keyFor("x_thread")))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingPublishSlotService.keyFor("x_thread"))
                .settingValue("19:45")
                .build()));

        Instant now = LocalDate.of(2026, 8, 11).atTime(18, 0).atZone(KST).toInstant();
        Instant slot = service.nextSlotForPlatform("x_thread", now).orElseThrow();

        assertThat(slot).isEqualTo(
            LocalDate.of(2026, 8, 11).atTime(19, 45).atZone(KST).toInstant());
    }

    @Test
    void nextSlotForTargets_picksEarliestAmongPlatforms() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        // 19:00 KST — feed 20:00 and x 21:30 both still today; earliest = feed
        Instant now = LocalDate.of(2026, 8, 11).atTime(19, 0).atZone(KST).toInstant();
        Instant slot = service.nextSlotForTargets(
            List.of("x_thread", "instagram_feed"), now).orElseThrow();

        assertThat(slot).isEqualTo(
            LocalDate.of(2026, 8, 11).atTime(20, 0).atZone(KST).toInstant());
    }

    @Test
    void nextSlotForTargets_dualVideo_sameDefaultSlot() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        Instant now = LocalDate.of(2026, 8, 11).atTime(12, 0).atZone(KST).toInstant();
        Instant slot = service.nextSlotForTargets(
            List.of("instagram_reels", "youtube_shorts"), now).orElseThrow();

        assertThat(slot).isEqualTo(
            LocalDate.of(2026, 8, 11).atTime(20, 30).atZone(KST).toInstant());
    }

    @Test
    void nextSlotForPlatform_unknownPlatform_empty() {
        when(systemSettingRepository.findById(MarketingPublishSlotService.keyFor("naver_blog")))
            .thenReturn(Optional.empty());

        assertThat(service.nextSlotForPlatform("naver_blog", Instant.now())).isEmpty();
    }

    @Test
    void updateSlots_persistsAllKeys() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        service.updateSlots(
            new MarketingPublishSlotService.Slots("19:00", "19:30", "19:30", "20:00"),
            "admin@test");

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, times(4)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SystemSetting::getSettingKey)
            .containsExactlyInAnyOrder(
                MarketingPublishSlotService.keyFor("instagram_feed"),
                MarketingPublishSlotService.keyFor("instagram_reels"),
                MarketingPublishSlotService.keyFor("youtube_shorts"),
                MarketingPublishSlotService.keyFor("x_thread"));
    }

    @Test
    void validateSlots_invalidFormat_throws() {
        assertThatThrownBy(() -> MarketingPublishSlotService.validateSlots(
            new MarketingPublishSlotService.Slots("20:00", "bad", "20:30", "21:30")))
            .isInstanceOf(ResponseStatusException.class);
    }
}
