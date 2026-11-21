package com.againspring.marketing.experiment;

import com.againspring.marketing.MarketingPlatformStatsCollector;
import com.againspring.marketing.MarketingPlatformStatsScheduler;
import com.againspring.marketing.MarketingScoreAutoAdjustService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingPlatformStatsSchedulerTest {

    @Mock MarketingPlatformStatsCollector collector;
    @Mock MarketingScoreAutoAdjustService autoAdjustService;
    @Mock MarketingOutcomeService outcomeService;
    @Mock MarketingAccountStatsService accountStatsService;
    @InjectMocks MarketingPlatformStatsScheduler scheduler;

    @Test
    void collectDaily_runsScoringAndAccountStats_afterCollect() {
        scheduler.collectDaily();
        verify(outcomeService).scorePending();
        verify(accountStatsService).collectAndStore();
    }

    @Test
    void collectDaily_collectFailure_doesNotSkipScoringOrAccountStats() {
        when(collector.collectScheduled()).thenThrow(new RuntimeException("collect down"));
        scheduler.collectDaily();
        verify(outcomeService).scorePending();
        verify(accountStatsService).collectAndStore();
    }

    @Test
    void collectDaily_scoringFailure_doesNotSkipAccountStats() {
        when(outcomeService.scorePending()).thenThrow(new RuntimeException("boom"));
        scheduler.collectDaily();
        verify(accountStatsService).collectAndStore();
    }
}
