package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketingPopularityScorerTest {

    @Test
    void score_reelsDefaults_emphasizesHookAndSkew() {
        var highHookSkew = new MarketingPopularityScorer.Signals(
            0, 0, 0, 1.0, 0, 1.0);
        double reels = MarketingPopularityScorer.score(highHookSkew, MarketingScoreWeightService.defaultsReels());
        double shorts = MarketingPopularityScorer.score(highHookSkew, MarketingScoreWeightService.defaultsShorts());
        // hook 2.0+skew 1.5 = 3.5 vs Shorts hook 1.2+skew 1.8 = 3.0
        assertThat(reels).isGreaterThan(shorts);
        assertThat(reels).isEqualTo(3.5);
    }

    @Test
    void score_xDefaults_emphasizesCommentsAndPartner() {
        var highComments = new MarketingPopularityScorer.Signals(0, 50, 0, 0, 1.0, 0.5);
        var lowComments = new MarketingPopularityScorer.Signals(0, 1, 0, 0, 0, 0.5);
        double xHigh = MarketingPopularityScorer.score(highComments, MarketingScoreWeightService.defaultsX());
        double xLow = MarketingPopularityScorer.score(lowComments, MarketingScoreWeightService.defaultsX());
        assertThat(xHigh).isGreaterThan(xLow);
    }

    @Test
    void voteSkew_noVotes_isZero() {
        assertThat(MarketingPopularityScorer.voteSkew(0, 0)).isZero();
    }

    @Test
    void voteSkew_fiftyFifty_isZero() {
        assertThat(MarketingPopularityScorer.voteSkew(50, 100)).isZero();
    }

    @Test
    void voteSkew_unanimous_isOne() {
        assertThat(MarketingPopularityScorer.voteSkew(100, 100)).isEqualTo(1.0);
    }

    @Test
    void hookStrength_blank_isHalf() {
        assertThat(MarketingPopularityScorer.hookStrength(null)).isEqualTo(0.5);
        assertThat(MarketingPopularityScorer.hookStrength("  ")).isEqualTo(0.5);
    }

    @Test
    void hookStrength_longText_approachesOne() {
        String longHook = "가".repeat(40);
        assertThat(MarketingPopularityScorer.hookStrength(longHook)).isEqualTo(1.0);
    }

    @Test
    void igExclusive_higherFeedWins() {
        assertThat(MarketingPopularityScorer.resolveIgExclusiveWinner(10, 5))
            .isEqualTo(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED);
    }

    @Test
    void igExclusive_tieGoesToReels() {
        assertThat(MarketingPopularityScorer.resolveIgExclusiveWinner(7, 7))
            .isEqualTo(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS);
    }
}
