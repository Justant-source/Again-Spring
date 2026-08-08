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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingScoreWeightServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @InjectMocks
    private MarketingScoreWeightService service;

    @Test
    void getWeights_missingSettings_returnsDefaults() {
        when(systemSettingRepository.findById(MarketingScoreWeightService.KEY_WEIGHT_VIEWS))
            .thenReturn(Optional.empty());
        when(systemSettingRepository.findById(MarketingScoreWeightService.KEY_WEIGHT_COMMENTS))
            .thenReturn(Optional.empty());
        when(systemSettingRepository.findById(MarketingScoreWeightService.KEY_WEIGHT_VOTES))
            .thenReturn(Optional.empty());

        MarketingScoreWeightService.Weights weights = service.getWeights();

        assertThat(weights.weightViews()).isEqualTo(0.1);
        assertThat(weights.weightComments()).isEqualTo(1.0);
        assertThat(weights.weightVotes()).isEqualTo(0.5);
    }

    @Test
    void getWeights_storedValues_returnsParsed() {
        when(systemSettingRepository.findById(MarketingScoreWeightService.KEY_WEIGHT_VIEWS))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingScoreWeightService.KEY_WEIGHT_VIEWS).settingValue("0.2").build()));
        when(systemSettingRepository.findById(MarketingScoreWeightService.KEY_WEIGHT_COMMENTS))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingScoreWeightService.KEY_WEIGHT_COMMENTS).settingValue("2.0").build()));
        when(systemSettingRepository.findById(MarketingScoreWeightService.KEY_WEIGHT_VOTES))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingScoreWeightService.KEY_WEIGHT_VOTES).settingValue("0.75").build()));

        MarketingScoreWeightService.Weights weights = service.getWeights();

        assertThat(weights.weightViews()).isEqualTo(0.2);
        assertThat(weights.weightComments()).isEqualTo(2.0);
        assertThat(weights.weightVotes()).isEqualTo(0.75);
    }

    @Test
    void getWeights_outOfRangeStored_fallsBackToDefault() {
        when(systemSettingRepository.findById(MarketingScoreWeightService.KEY_WEIGHT_VIEWS))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingScoreWeightService.KEY_WEIGHT_VIEWS).settingValue("-1").build()));
        when(systemSettingRepository.findById(MarketingScoreWeightService.KEY_WEIGHT_COMMENTS))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingScoreWeightService.KEY_WEIGHT_COMMENTS).settingValue("101").build()));
        when(systemSettingRepository.findById(MarketingScoreWeightService.KEY_WEIGHT_VOTES))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(MarketingScoreWeightService.KEY_WEIGHT_VOTES).settingValue("not-a-number").build()));

        MarketingScoreWeightService.Weights weights = service.getWeights();

        assertThat(weights.weightViews()).isEqualTo(0.1);
        assertThat(weights.weightComments()).isEqualTo(1.0);
        assertThat(weights.weightVotes()).isEqualTo(0.5);
    }

    @Test
    void updateWeights_persistsAllThreeKeys() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        service.updateWeights(0.2, 1.5, 0.8, "admin@test");

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SystemSetting::getSettingKey)
            .containsExactlyInAnyOrder(
                MarketingScoreWeightService.KEY_WEIGHT_VIEWS,
                MarketingScoreWeightService.KEY_WEIGHT_COMMENTS,
                MarketingScoreWeightService.KEY_WEIGHT_VOTES);
    }

    @Test
    void validate_negative_throws() {
        assertThatThrownBy(() -> MarketingScoreWeightService.validate(-0.1, 1.0, 0.5))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void validate_aboveMax_throws() {
        assertThatThrownBy(() -> MarketingScoreWeightService.validate(0.1, 100.1, 0.5))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void validate_nan_throws() {
        assertThatThrownBy(() -> MarketingScoreWeightService.validate(Double.NaN, 1.0, 0.5))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void validate_boundaryValues_ok() {
        MarketingScoreWeightService.validate(0.0, 100.0, 0.0);
    }
}
