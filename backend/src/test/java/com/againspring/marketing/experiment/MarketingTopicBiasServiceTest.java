package com.againspring.marketing.experiment;

import com.againspring.domain.marketing.MarketingPublicationOutcome;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingTopicBiasServiceTest {

    @Mock MarketingPublicationOutcomeRepository outcomeRepository;
    @Mock MarketingExperimentSettings settings;
    MarketingTopicBiasService service;

    @BeforeEach
    void setUp() {
        service = new MarketingTopicBiasService(outcomeRepository, settings,
            Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"), ZoneOffset.UTC));
    }

    private static MarketingPublicationOutcome o(String cat, String emo, double reward) {
        return MarketingPublicationOutcome.builder().platform("youtube_shorts").category(cat)
            .hookEmotion(emo).reward(reward).status("SCORED").build();
    }

    private void rows(List<MarketingPublicationOutcome> rows) {
        when(outcomeRepository.findByPlatformAndStatusAndPublishedAtGreaterThanEqual(
            eq("youtube_shorts"), eq("SCORED"), any())).thenReturn(rows);
    }

    @Test
    void flagOff_returnsOne_withoutQuery() {
        when(settings.isTopicBiasEnabled()).thenReturn(false);
        assertThat(service.multiplier("youtube_shorts", "COUPLE", "anger")).isEqualTo(1.0);
        verifyNoInteractions(outcomeRepository);
    }

    @Test
    void shrinkage_matchesFormula() {
        when(settings.isTopicBiasEnabled()).thenReturn(true);
        List<MarketingPublicationOutcome> r = new ArrayList<>();
        for (int i = 0; i < 5; i++) r.add(o("COUPLE", "anger", 3.0));
        for (int i = 0; i < 15; i++) r.add(o("WORK", "sad", 1.0));
        rows(r);
        // platformMean = (15+15)/20 = 1.5 ; cellMean=3, n=5 -> ((15+7.5)/10)/1.5 = 1.5
        assertThat(service.multiplier("youtube_shorts", "COUPLE", "anger")).isCloseTo(1.5, within(1e-9));
    }

    @Test
    void clampsToBounds() {
        assertThat(MarketingTopicBiasService.compute(1000, 100.0, 1.0)).isEqualTo(2.0);
        assertThat(MarketingTopicBiasService.compute(1000, 0.0, 1.0)).isEqualTo(0.5);
    }

    @Test
    void platformMeanZero_returnsOne() {
        assertThat(MarketingTopicBiasService.compute(5, 0.0, 0.0)).isEqualTo(1.0);
    }

    @Test
    void emptyCell_returnsOne() {
        when(settings.isTopicBiasEnabled()).thenReturn(true);
        rows(List.of(o("WORK", "sad", 2.0)));
        assertThat(service.multiplier("youtube_shorts", "COUPLE", "anger")).isCloseTo(1.0, within(1e-9));
    }
}
