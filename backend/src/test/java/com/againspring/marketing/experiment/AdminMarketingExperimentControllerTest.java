package com.againspring.marketing.experiment;

import com.againspring.api.admin.AdminMarketingExperimentController;
import com.againspring.domain.marketing.MarketingAccountStats;
import com.againspring.domain.marketing.MarketingPublicationOutcome;
import com.againspring.repository.marketing.MarketingAccountStatsRepository;
import com.againspring.repository.marketing.MarketingFormatArmRepository;
import com.againspring.repository.marketing.MarketingPublicationOutcomeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMarketingExperimentControllerTest {

    @Mock MarketingExperimentSettings settings;
    @Mock MarketingFormatArmService armService;
    @Mock MarketingFormatArmRepository armRepository;
    @Mock MarketingOutcomeService outcomeService;
    @Mock MarketingTopicBiasService topicBiasService;
    @Mock MarketingPreviewBatchService previewBatchService;
    @Mock MarketingPublicationOutcomeRepository outcomeRepository;
    @Mock MarketingAccountStatsRepository accountStatsRepository;

    AdminMarketingExperimentController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminMarketingExperimentController(settings, armService, armRepository, outcomeService,
            topicBiasService, previewBatchService, outcomeRepository, accountStatsRepository,
            Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void overview_aggregatesArmStats() {
        when(settings.isEnabled()).thenReturn(true);
        when(settings.isTopicBiasEnabled()).thenReturn(false);
        when(settings.minSamples()).thenReturn(8);
        when(armService.listAll()).thenReturn(List.of(
            new FormatArm("baseline", "marketing_hook_a", "narrative", 2, "partner_cta", null, true, "d"),
            new FormatArm("dilemma", "marketing_hook_a", "dilemma", 0, "vote_reveal", 30, false, "d2")));
        when(armRepository.countAssignedSince(eq("baseline"), any())).thenReturn(12L);
        when(armRepository.countAssignedSince(eq("dilemma"), any())).thenReturn(0L);
        when(outcomeRepository.findByStatusAndPublishedAtGreaterThanEqual(eq("SCORED"), any())).thenReturn(List.of(
            MarketingPublicationOutcome.builder().formatArm("baseline").reward(1.0).breakout(false).build(),
            MarketingPublicationOutcome.builder().formatArm("baseline").reward(3.0).breakout(true).build()));
        when(topicBiasService.cells()).thenReturn(List.of());
        when(accountStatsRepository.findByCollectedOnGreaterThanEqualOrderByCollectedOnDescPlatformAsc(any()))
            .thenReturn(List.of(MarketingAccountStats.builder().platform("x")
                .collectedOn(LocalDate.of(2026, 11, 20)).followers(100L).build()));

        var body = controller.overview().getBody();

        assertThat(body.settings().enabled()).isTrue();
        assertThat(body.arms()).hasSize(2);
        var base = body.arms().get(0);
        assertThat(base.assigned30d()).isEqualTo(12);
        assertThat(base.outcomes60d()).isEqualTo(2);
        assertThat(base.meanReward()).isEqualTo(2.0);
        assertThat(base.breakouts()).isEqualTo(1);
        assertThat(body.arms().get(1).outcomes60d()).isZero();
        assertThat(body.accounts()).singleElement().extracting("followers").isEqualTo(100L);
    }

    @Test
    void updateSettings_onlyAppliesProvidedFields() {
        when(settings.isEnabled()).thenReturn(true);
        when(settings.isTopicBiasEnabled()).thenReturn(false);
        when(settings.minSamples()).thenReturn(8);

        controller.updateSettings(new AdminMarketingExperimentController.SettingsRequest(true, null));

        verify(settings).setEnabled(true);
        verify(settings, never()).setTopicBiasEnabled(any(Boolean.class));
    }

    @Test
    void toggleArm_delegates_andRequiresEnabled() {
        FormatArm arm = new FormatArm("dilemma", "marketing_hook_a", "dilemma", 0, "vote_reveal", 30, true, null);
        when(armService.setEnabled("dilemma", true)).thenReturn(arm);

        assertThat(controller.toggleArm("dilemma",
            new AdminMarketingExperimentController.ArmToggleRequest(true)).getBody()).isEqualTo(arm);
        assertThatThrownBy(() -> controller.toggleArm("dilemma",
            new AdminMarketingExperimentController.ArmToggleRequest(null)))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void runScoring_returnsCreatedCount() {
        when(outcomeService.scorePending()).thenReturn(4);
        assertThat(controller.runScoring().getBody()).containsEntry("created", 4);
    }
}
