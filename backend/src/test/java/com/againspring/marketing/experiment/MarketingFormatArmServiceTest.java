package com.againspring.marketing.experiment;

import com.againspring.domain.marketing.MarketingFormatArmEntity;
import com.againspring.domain.marketing.MarketingPublicationOutcome;
import com.againspring.repository.marketing.MarketingFormatArmRepository;
import com.againspring.repository.marketing.MarketingPublicationOutcomeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingFormatArmServiceTest {

    private static final String PLAT = "youtube_shorts";
    private static final Instant NOW = Instant.parse("2026-11-21T00:00:00Z");

    @Mock MarketingFormatArmRepository armRepository;
    @Mock MarketingPublicationOutcomeRepository outcomeRepository;
    @Mock MarketingExperimentSettings settings;

    MarketingFormatArmService service;

    @BeforeEach
    void setUp() {
        service = new MarketingFormatArmService(armRepository, outcomeRepository, settings,
            Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(settings.isEnabled()).thenReturn(true);
        lenient().when(settings.minSamples()).thenReturn(8);
    }

    private static MarketingFormatArmEntity arm(String key) {
        return MarketingFormatArmEntity.builder().armKey(key).renderProfile("marketing_hook_a")
            .scriptStyle("narrative").commentCount(0).outroMode("vote_reveal").enabled(true).build();
    }

    private static MarketingPublicationOutcome outcome(String arm, double reward) {
        return MarketingPublicationOutcome.builder().formatArm(arm).reward(reward)
            .status(MarketingPublicationOutcome.SCORED).platform(PLAT).build();
    }

    private void enabledArms(String... keys) {
        List<MarketingFormatArmEntity> list = new ArrayList<>();
        for (String k : keys) {
            list.add(arm(k));
        }
        when(armRepository.findByEnabledTrueOrderByArmKeyAsc()).thenReturn(list);
    }

    private void assigned(String key, long n) {
        lenient().when(armRepository.countAssignedSinceForPlatform(eq(key), eq(PLAT), any())).thenReturn(n);
    }

    private void outcomes(MarketingPublicationOutcome... rows) {
        lenient().when(outcomeRepository.findByPlatformAndStatusAndPublishedAtGreaterThanEqual(
            eq(PLAT), eq("SCORED"), any())).thenReturn(List.of(rows));
    }

    @Test
    void experimentOff_returnsEmpty() {
        when(settings.isEnabled()).thenReturn(false);
        assertThat(service.pickForAutoJob(PLAT)).isEmpty();
    }

    @Test
    void nonVideoPlatform_returnsEmpty() {
        assertThat(service.pickForAutoJob("x_thread")).isEmpty();
    }

    @Test
    void noEnabledArm_returnsEmpty() {
        when(armRepository.findByEnabledTrueOrderByArmKeyAsc()).thenReturn(List.of());
        assertThat(service.pickForAutoJob(PLAT)).isEmpty();
    }

    @Test
    void singleArm_isAlwaysPicked() {
        enabledArms("baseline");
        assertThat(service.pickForAutoJob(PLAT)).get().extracting(FormatArm::key).isEqualTo("baseline");
    }

    @Test
    void underSampledArm_winsWithFewestAssigned_tieBreakByKey() {
        enabledArms("baseline", "dilemma", "short_reveal");
        assigned("baseline", 20);
        assigned("dilemma", 3);
        assigned("short_reveal", 3);
        outcomes();
        assertThat(service.pickForAutoJob(PLAT)).get().extracting(FormatArm::key).isEqualTo("dilemma");
    }

    @Test
    void allFilled_ucbPicksHigherMeanReward() {
        enabledArms("baseline", "dilemma");
        assigned("baseline", 10);
        assigned("dilemma", 10);
        List<MarketingPublicationOutcome> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(outcome("baseline", 1.0));
            rows.add(outcome("dilemma", 3.0));
        }
        outcomes(rows.toArray(new MarketingPublicationOutcome[0]));
        assertThat(service.pickForAutoJob(PLAT)).get().extracting(FormatArm::key).isEqualTo("dilemma");
    }

    @Test
    void allFilled_armWithZeroOutcomes_isTreatedLikeUnderSampled() {
        enabledArms("baseline", "dilemma");
        assigned("baseline", 10);
        assigned("dilemma", 9);
        outcomes(outcome("baseline", 5.0), outcome("baseline", 5.0));
        assertThat(service.pickForAutoJob(PLAT)).get().extracting(FormatArm::key).isEqualTo("dilemma");
    }

    @Test
    void ucb_exploresLessSampledArmWhenMeansEqual() {
        enabledArms("baseline", "dilemma");
        assigned("baseline", 10);
        assigned("dilemma", 10);
        List<MarketingPublicationOutcome> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rows.add(outcome("baseline", 2.0));
        }
        for (int i = 0; i < 2; i++) {
            rows.add(outcome("dilemma", 2.0));
        }
        outcomes(rows.toArray(new MarketingPublicationOutcome[0]));
        assertThat(service.pickForAutoJob(PLAT)).get().extracting(FormatArm::key).isEqualTo("dilemma");
    }

    @Test
    void find_unknownKey_empty() {
        when(armRepository.findById("nope")).thenReturn(Optional.empty());
        assertThat(service.find("nope")).isEmpty();
    }
}
