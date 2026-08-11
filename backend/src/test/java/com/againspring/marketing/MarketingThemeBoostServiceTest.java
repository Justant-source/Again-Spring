package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingThemeBoostServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @InjectMocks
    private MarketingThemeBoostService service;

    @Test
    void getBoost_missing_returnsDefault() {
        when(systemSettingRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThat(service.getBoost("x_thread", "shock", "COUPLE"))
            .isEqualTo(1.0);
    }

    @Test
    void getBoost_unknownEmotionOrCategory_returnsDefaultWithoutLookup() {
        assertThat(service.getBoost("x_thread", "unknown", "COUPLE")).isEqualTo(1.0);
        assertThat(service.getBoost("x_thread", "shock", "NOPE")).isEqualTo(1.0);
        verify(systemSettingRepository, never()).findById(anyString());
    }

    @Test
    void getBoost_storedValue_returnsParsed() {
        String key = MarketingThemeBoostService.settingKey("x_thread", "anger", "WORK");
        when(systemSettingRepository.findById(key))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(key).settingValue("1.15").build()));

        assertThat(service.getBoost("x_thread", "anger", "WORK")).isEqualTo(1.15);
    }

    @Test
    void getMatrix_fillsAllEmotionsAndCategories() {
        when(systemSettingRepository.findById(anyString())).thenReturn(Optional.empty());

        Map<String, Map<String, Double>> matrix = service.getMatrix("instagram_feed");

        assertThat(matrix.keySet()).containsExactlyElementsOf(MarketingThemeBoostService.EMOTIONS);
        assertThat(matrix.get("shock").keySet())
            .containsExactlyElementsOf(MarketingThemeBoostService.CATEGORIES);
        assertThat(matrix.get("hype").get("OTHER")).isEqualTo(1.0);
    }

    @Test
    void isShadow_defaultTrue() {
        when(systemSettingRepository.findById(MarketingThemeBoostService.KEY_SHADOW))
            .thenReturn(Optional.empty());
        assertThat(service.isShadow()).isTrue();
    }

    @Test
    void setShadow_persistsFalse() {
        when(systemSettingRepository.findById(MarketingThemeBoostService.KEY_SHADOW))
            .thenReturn(Optional.empty());

        service.setShadow(false);

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository).save(captor.capture());
        assertThat(captor.getValue().getSettingKey()).isEqualTo(MarketingThemeBoostService.KEY_SHADOW);
        assertThat(captor.getValue().getSettingValue()).isEqualTo("false");
    }

    @Test
    void canApplyNow_noLastApply_true() {
        when(systemSettingRepository.findById(MarketingThemeBoostService.KEY_LAST_APPLY_AT))
            .thenReturn(Optional.empty());
        assertThat(service.canApplyNow()).isTrue();
        assertThat(service.cooldownUntil()).isNull();
    }

    @Test
    void canApplyNow_withinCooldown_false() {
        Instant last = Instant.now().minus(2, ChronoUnit.DAYS);
        when(systemSettingRepository.findById(MarketingThemeBoostService.KEY_LAST_APPLY_AT))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingThemeBoostService.KEY_LAST_APPLY_AT)
                .settingValue(last.toString())
                .build()));

        assertThat(service.canApplyNow()).isFalse();
        assertThat(service.cooldownUntil()).isEqualTo(last.plus(7, ChronoUnit.DAYS));
    }

    @Test
    void applyChanges_confirmFalse_throws() {
        assertThatThrownBy(() -> service.applyChanges(
            "x_thread",
            List.of(new MarketingThemeBoostService.ThemeBoostChange("shock", "COUPLE", 1.05)),
            false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("confirm=true");
    }

    @Test
    void applyChanges_onCooldown_throws() {
        Instant last = Instant.now().minus(1, ChronoUnit.DAYS);
        when(systemSettingRepository.findById(MarketingThemeBoostService.KEY_LAST_APPLY_AT))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingThemeBoostService.KEY_LAST_APPLY_AT)
                .settingValue(last.toString())
                .build()));

        assertThatThrownBy(() -> service.applyChanges(
            "x_thread",
            List.of(new MarketingThemeBoostService.ThemeBoostChange("shock", "COUPLE", 1.05)),
            true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cooldown");
    }

    @Test
    void applyChanges_deltaExceedsCap_throws() {
        when(systemSettingRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyChanges(
            "x_thread",
            List.of(new MarketingThemeBoostService.ThemeBoostChange("shock", "COUPLE", 1.2)),
            true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("delta");
    }

    @Test
    void applyChanges_outOfRangeClampedThenDeltaExceeds_throws() {
        // 1.5 clamps to 1.3; vs default current 1.0 → Δ0.3 > 0.05
        when(systemSettingRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyChanges(
            "x_thread",
            List.of(new MarketingThemeBoostService.ThemeBoostChange("shock", "COUPLE", 1.5)),
            true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("delta");
    }

    @Test
    void applyChanges_valid_persistsBoostAndLastApply() {
        when(systemSettingRepository.findById(anyString())).thenReturn(Optional.empty());

        MarketingThemeBoostService.ApplyResult result = service.applyChanges(
            "youtube_shorts",
            List.of(new MarketingThemeBoostService.ThemeBoostChange("hype", "FRIEND", 1.05)),
            true);

        assertThat(result.applied()).isEqualTo(1);
        assertThat(result.cooldownUntil()).isAfter(Instant.now());

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SystemSetting::getSettingKey)
            .contains(
                MarketingThemeBoostService.settingKey("youtube_shorts", "hype", "FRIEND"),
                MarketingThemeBoostService.KEY_LAST_APPLY_AT);
    }

    @Test
    void applyChanges_unknownPlatform_throws() {
        when(systemSettingRepository.findById(MarketingThemeBoostService.KEY_LAST_APPLY_AT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyChanges(
            "naver_blog",
            List.of(new MarketingThemeBoostService.ThemeBoostChange("shock", "COUPLE", 1.0)),
            true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("platform");
    }
}
