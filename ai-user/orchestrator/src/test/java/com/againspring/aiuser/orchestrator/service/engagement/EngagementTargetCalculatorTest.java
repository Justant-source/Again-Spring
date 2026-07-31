package com.againspring.aiuser.orchestrator.service.engagement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EngagementTargetCalculatorTest {

    @Test
    void jitterIsDeterministicPerId() {
        double first = EngagementTargetCalculator.jitter("post_abc123");
        double second = EngagementTargetCalculator.jitter("post_abc123");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void jitterVariesAcrossDifferentIds() {
        double a = EngagementTargetCalculator.jitter("post_aaaaaaaaaaaaaaaaaaaaaaaa");
        double b = EngagementTargetCalculator.jitter("post_bbbbbbbbbbbbbbbbbbbbbbbb");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void jitterStaysWithinRange() {
        for (String id : new String[]{"1", "post_x", "comment_999999", ""}) {
            double j = EngagementTargetCalculator.jitter(id.isEmpty() ? "empty" : id);
            assertThat(j).isGreaterThanOrEqualTo(0.8).isLessThan(1.2);
        }
    }

    @Test
    void commentLikeTargetNeverExceedsCap() {
        int target = EngagementTargetCalculator.commentLikeTarget(
                1_000_000L, 500, 42L, 0.002, 1.0, 12);
        assertThat(target).isLessThanOrEqualTo(12).isGreaterThanOrEqualTo(0);
    }

    @Test
    void replyLikeTargetNeverExceedsCap() {
        int target = EngagementTargetCalculator.replyLikeTarget(
                1_000_000L, 99L, 0.001, 5);
        assertThat(target).isLessThanOrEqualTo(5).isGreaterThanOrEqualTo(0);
    }

    @Test
    void targetsAreNeverNegativeAtZeroInputs() {
        assertThat(EngagementTargetCalculator.postLikeTarget(0, 0, "post_zero", 0.02, 0.6)).isGreaterThanOrEqualTo(0);
        assertThat(EngagementTargetCalculator.commentLikeTarget(0, 0, 1L, 0.002, 1.0, 12)).isGreaterThanOrEqualTo(0);
        assertThat(EngagementTargetCalculator.replyLikeTarget(0, 1L, 0.001, 5)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void deficitNeverGoesNegative() {
        assertThat(EngagementTargetCalculator.deficit(5, 10)).isZero();
        assertThat(EngagementTargetCalculator.deficit(10, 5)).isEqualTo(5);
        assertThat(EngagementTargetCalculator.deficit(0, 0)).isZero();
    }

    @Test
    void postLikeTargetGrowsWithViewsAndComments() {
        int low = EngagementTargetCalculator.postLikeTarget(100, 5, "post_growth", 0.02, 0.6);
        int high = EngagementTargetCalculator.postLikeTarget(10_000, 50, "post_growth", 0.02, 0.6);
        assertThat(high).isGreaterThan(low);
    }
}
