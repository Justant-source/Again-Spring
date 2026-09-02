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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingXOpsSettingsServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @InjectMocks
    private MarketingXOpsSettingsService service;

    @Test
    void get_missingSettings_returnsDefaultsWithFlagsOff() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        MarketingXOpsSettingsService.XOpsSettings settings = service.get();

        assertThat(settings.morningTime()).isEqualTo("07:30");
        assertThat(settings.nightTime()).isEqualTo("22:00");
        assertThat(settings.storyScoopsPerDay()).isEqualTo(2);
        assertThat(settings.outboundDailyCap()).isEqualTo(20);
        assertThat(settings.inboundDailyCap()).isEqualTo(40);
        assertThat(settings.inboundPerPostCap()).isEqualTo(12);
        assertThat(settings.hotMinReplies()).isEqualTo(3);
        assertThat(settings.hotMaxAgeHours()).isEqualTo(6);
        assertThat(settings.ritualEnabled()).isFalse();
        assertThat(settings.inboundEnabled()).isFalse();
        assertThat(settings.outboundEnabled()).isFalse();
        assertThat(settings.personaLearningEnabled()).isTrue();
        assertThat(settings.personaLearnAt()).isEqualTo("04:30");
        assertThat(settings.personaEvalEnabled()).isTrue();
        assertThat(settings.originalPostEnabled()).isFalse();
        assertThat(settings.originalPostDailyCap()).isEqualTo(1);
        assertThat(settings.outboundPerTick()).isEqualTo(1);
        assertThat(settings.inboundPerTick()).isEqualTo(3);
    }

    @Test
    void update_persistsAllKeys() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        service.update(new MarketingXOpsSettingsService.XOpsSettings(
            "08:00", "21:30", 1, 10, 30, 8, 5, 4,
            true, false, true, true, "04:30", true, false, 1), "admin");

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, atLeast(18)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SystemSetting::getSettingKey)
            .contains(
                MarketingXOpsSettingsService.KEY_MORNING_TIME,
                MarketingXOpsSettingsService.KEY_NIGHT_TIME,
                MarketingXOpsSettingsService.KEY_STORY_SCOOPS_PER_DAY,
                MarketingXOpsSettingsService.KEY_OUTBOUND_DAILY_CAP,
                MarketingXOpsSettingsService.KEY_INBOUND_DAILY_CAP,
                MarketingXOpsSettingsService.KEY_INBOUND_PER_POST_CAP,
                MarketingXOpsSettingsService.KEY_HOT_MIN_REPLIES,
                MarketingXOpsSettingsService.KEY_HOT_MAX_AGE_HOURS,
                MarketingXOpsSettingsService.KEY_RITUAL_ENABLED,
                MarketingXOpsSettingsService.KEY_INBOUND_ENABLED,
                MarketingXOpsSettingsService.KEY_OUTBOUND_ENABLED,
                MarketingXOpsSettingsService.KEY_PERSONA_LEARNING_ENABLED,
                MarketingXOpsSettingsService.KEY_PERSONA_LEARN_AT,
                MarketingXOpsSettingsService.KEY_PERSONA_EVAL_ENABLED,
                MarketingXOpsSettingsService.KEY_ORIGINAL_POST_ENABLED,
                MarketingXOpsSettingsService.KEY_ORIGINAL_POST_DAILY_CAP,
                MarketingXOpsSettingsService.KEY_OUTBOUND_PER_TICK,
                MarketingXOpsSettingsService.KEY_INBOUND_PER_TICK);
        assertThat(captor.getAllValues())
            .anyMatch(s -> MarketingXOpsSettingsService.KEY_ORIGINAL_POST_ENABLED.equals(s.getSettingKey())
                && "false".equals(s.getSettingValue()));
        assertThat(captor.getAllValues())
            .anyMatch(s -> MarketingXOpsSettingsService.KEY_PERSONA_EVAL_ENABLED.equals(s.getSettingKey())
                && "true".equals(s.getSettingValue()));
    }

    @Test
    void update_hotMinRepliesZero_isAllowed() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        service.update(new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 0, 24,
            false, false, true, true, "04:30"), "admin");

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, atLeast(1)).save(captor.capture());
        assertThat(captor.getAllValues())
            .anyMatch(s -> MarketingXOpsSettingsService.KEY_HOT_MIN_REPLIES.equals(s.getSettingKey())
                && "0".equals(s.getSettingValue()));
        assertThat(captor.getAllValues())
            .anyMatch(s -> MarketingXOpsSettingsService.KEY_HOT_MAX_AGE_HOURS.equals(s.getSettingKey())
                && "24".equals(s.getSettingValue()));
    }

    @Test
    void update_invalidTime_throws() {
        assertThatThrownBy(() -> service.update(new MarketingXOpsSettingsService.XOpsSettings(
            "7:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30"), "admin"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void update_negativeCap_throws() {
        assertThatThrownBy(() -> service.update(new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, -1, 40, 12, 3, 6,
            false, false, false, true, "04:30"), "admin"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void update_originalPostDailyCapOutOfRange_throws() {
        assertThatThrownBy(() -> service.update(new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30", true, false, 6), "admin"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.update(new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30", true, false, -1), "admin"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void update_originalPostDailyCapZero_isAllowedWhileOriginalStaysOff() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        service.update(new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30", true, false, 0), "admin");

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, atLeast(1)).save(captor.capture());
        assertThat(captor.getAllValues())
            .anyMatch(s -> MarketingXOpsSettingsService.KEY_ORIGINAL_POST_DAILY_CAP.equals(s.getSettingKey())
                && "0".equals(s.getSettingValue()));
        assertThat(captor.getAllValues())
            .anyMatch(s -> MarketingXOpsSettingsService.KEY_ORIGINAL_POST_ENABLED.equals(s.getSettingKey())
                && "false".equals(s.getSettingValue()));
    }

    @Test
    void update_outboundPerTickOutOfRange_throws() {
        assertThatThrownBy(() -> service.update(new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30", true, false, 1, 0, 3), "admin"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.update(new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30", true, false, 1, 6, 3), "admin"))
            .isInstanceOf(ResponseStatusException.class);
    }
}
