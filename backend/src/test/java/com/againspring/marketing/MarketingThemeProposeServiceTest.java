package com.againspring.marketing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MarketingThemeProposeService scoring")
class MarketingThemeProposeServiceTest {

    @Test
    @DisplayName("proposalCombo is 1.0 at median with no WoW change")
    void comboAtMedianNeutralWow() {
        double combo = MarketingThemeProposeService.proposalCombo(100, 100, true, 100);
        assertThat(combo).isEqualTo(1.0);
    }

    @Test
    @DisplayName("proposalCombo blends level vs median and WoW")
    void comboBlendsLevelAndWow() {
        // level=1.2, wowRel=+0.5 → wowFactor=1.5 → 0.5*1.2 + 0.5*1.5 = 1.35
        double combo = MarketingThemeProposeService.proposalCombo(120, 80, true, 100);
        assertThat(combo).isEqualTo(1.35);
    }

    @Test
    @DisplayName("mapSuggestedBoost clamps to [0.7,1.3] and ±deltaCap from current")
    void mapSuggestedBoostClamps() {
        // combo high but delta-capped from 1.0
        assertThat(MarketingThemeProposeService.mapSuggestedBoost(1.5, 1.0, 0.7, 1.3, 0.05))
            .isEqualTo(1.05);
        // combo low
        assertThat(MarketingThemeProposeService.mapSuggestedBoost(0.5, 1.0, 0.7, 1.3, 0.05))
            .isEqualTo(0.95);
        // already near floor — absolute clamp wins after delta
        assertThat(MarketingThemeProposeService.mapSuggestedBoost(0.5, 0.72, 0.7, 1.3, 0.05))
            .isEqualTo(0.7);
    }

    @Test
    @DisplayName("median of even/odd lists")
    void medianEvenOdd() {
        assertThat(MarketingThemeProposeService.median(List.of(1.0, 3.0, 2.0))).isEqualTo(2.0);
        assertThat(MarketingThemeProposeService.median(List.of(1.0, 2.0, 3.0, 4.0))).isEqualTo(2.5);
        assertThat(MarketingThemeProposeService.median(List.of())).isEqualTo(0.0);
    }
}
