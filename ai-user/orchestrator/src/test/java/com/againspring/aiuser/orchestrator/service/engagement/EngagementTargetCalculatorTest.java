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
                1_000_000L, 500, 42L, 0.75, 1.0, 12);
        assertThat(target).isLessThanOrEqualTo(12).isGreaterThanOrEqualTo(0);
    }

    @Test
    void replyLikeTargetNeverExceedsCap() {
        int target = EngagementTargetCalculator.replyLikeTarget(
                1_000_000L, 99L, 0.40, 5);
        assertThat(target).isLessThanOrEqualTo(5).isGreaterThanOrEqualTo(0);
    }

    @Test
    void targetsAreNeverNegativeAtZeroInputs() {
        assertThat(EngagementTargetCalculator.postLikeTarget(0, 0, "post_zero", 0.02, 0.6)).isGreaterThanOrEqualTo(0);
        assertThat(EngagementTargetCalculator.commentLikeTarget(0, 0, 1L, 0.75, 1.0, 12)).isGreaterThanOrEqualTo(0);
        assertThat(EngagementTargetCalculator.replyLikeTarget(0, 1L, 0.40, 5)).isGreaterThanOrEqualTo(0);
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

    @Test
    void commentLikeTargetsDivergeAtHighViewsInsteadOfAllHittingCap() {
        // Regression: linear views*0.025 parked every comment at cap 12 once views ≥ ~480
        // (prod 2026-08-01 — post_6cb27 / post_8922927 all showed identical likes).
        java.util.Set<Integer> targets = new java.util.TreeSet<>();
        int atCap = 0;
        for (long id = 3000; id < 3100; id++) {
            int t = EngagementTargetCalculator.commentLikeTarget(625L, 0, id, 0.75, 1.0, 12);
            targets.add(t);
            if (t == 12) atCap++;
        }
        assertThat(targets.size()).isGreaterThanOrEqualTo(4);
        assertThat(atCap).isLessThan(20);
        assertThat(targets.stream().mapToInt(Integer::intValue).max().orElse(0)).isLessThanOrEqualTo(12);
        assertThat(targets.stream().mapToInt(Integer::intValue).min().orElse(0)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void popularityIsDeterministicAndDecorrelatedFromJitterSalt() {
        double a = EngagementTargetCalculator.popularity("42");
        double b = EngagementTargetCalculator.popularity("42");
        assertThat(a).isEqualTo(b);
        assertThat(a).isGreaterThanOrEqualTo(0.4).isLessThan(1.6);
    }

    @Test
    void voteTargetMatches15PercentBand() {
        // views * 0.15 * jitter[0.8,1.2) -> [views*0.12, views*0.18]
        int target139 = EngagementTargetCalculator.voteTarget(139, "post_c5e81627057f4938a216", 0.15, 80);
        assertThat(target139).isBetween(16, 25);

        int target207 = EngagementTargetCalculator.voteTarget(207, "post_cd8377c210d74ecca556", 0.15, 80);
        assertThat(target207).isBetween(24, 38);
    }

    @Test
    void voteTargetIsDeterministic() {
        int first = EngagementTargetCalculator.voteTarget(139, "post_abc", 0.15, 80);
        int second = EngagementTargetCalculator.voteTarget(139, "post_abc", 0.15, 80);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void voteTargetRespectsCap() {
        int target = EngagementTargetCalculator.voteTarget(1_000_000L, "post_huge", 0.15, 80);
        assertThat(target).isEqualTo(80);
    }

    @Test
    void voteTargetIsZeroAtZeroViews() {
        int target = EngagementTargetCalculator.voteTarget(0, "post_zero", 0.15, 80);
        assertThat(target).isZero();
    }

    @Test
    void voteAShareStaysWithinConfiguredBand() {
        for (String id : new String[]{"post_1", "post_2", "post_3", "post_4", "post_5"}) {
            double share = EngagementTargetCalculator.voteAShare(id, 0.44, 0.80);
            assertThat(share).isGreaterThanOrEqualTo(0.44).isLessThan(0.80 + 1e-9);
        }
    }

    @Test
    void voteAShareIsDeterministic() {
        double first = EngagementTargetCalculator.voteAShare("post_xyz", 0.44, 0.80);
        double second = EngagementTargetCalculator.voteAShare("post_xyz", 0.44, 0.80);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void voteAShareIsDecorrelatedFromJitter() {
        // Regression guard: voteAShare must use a differently salted hash than jitter(), or a
        // post's vote-count target and its A-share target would be perfectly correlated (same
        // hash, same id) -- high-vote-target posts would always skew the same direction, which
        // does not match the observed natural distribution (share uncorrelated with volume).
        String[] ids = {"post_a", "post_b", "post_c", "post_d", "post_e", "post_f", "post_g", "post_h"};
        boolean sameOrder = true;
        double[] jitters = new double[ids.length];
        double[] shares = new double[ids.length];
        for (int i = 0; i < ids.length; i++) {
            jitters[i] = EngagementTargetCalculator.jitter(ids[i]);
            shares[i] = EngagementTargetCalculator.voteAShare(ids[i], 0.44, 0.80);
        }
        // If every pairwise order of jitter matched the pairwise order of share, the two would be
        // fully rank-correlated -- assert at least one inversion exists (i.e. NOT fully correlated).
        outer:
        for (int i = 0; i < ids.length; i++) {
            for (int j = i + 1; j < ids.length; j++) {
                boolean jitterOrder = jitters[i] < jitters[j];
                boolean shareOrder = shares[i] < shares[j];
                if (jitterOrder != shareOrder) {
                    sameOrder = false;
                    break outer;
                }
            }
        }
        assertThat(sameOrder).as("jitter() and voteAShare() must not be fully rank-correlated").isFalse();
    }

    @Test
    void chooseVoteOptionConvergesToTargetShare() {
        int a = 0;
        int b = 0;
        double targetAShare = 0.66;
        for (int i = 0; i < 40; i++) {
            if (EngagementTargetCalculator.chooseVoteOptionIndex(a, b, targetAShare) == 0) a++; else b++;
        }
        double finalShare = (double) a / (a + b);
        assertThat(finalShare).isCloseTo(targetAShare, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void chooseVoteOptionCorrectsForExistingHumanVotes() {
        // Humans already voted 0:20 (all B). Target is 66% A -- every synthetic vote from here
        // should go to A until the ratio catches up, never flipping/removing the existing B votes.
        int currentA = 0;
        int currentB = 20;
        for (int i = 0; i < 5; i++) {
            int choice = EngagementTargetCalculator.chooseVoteOptionIndex(currentA, currentB, 0.66);
            assertThat(choice).isZero();
            currentA++;
        }
    }

    @Test
    void chooseVoteOptionNeverReturnsInvalidIndex() {
        for (int a = 0; a < 10; a++) {
            for (int b = 0; b < 10; b++) {
                int choice = EngagementTargetCalculator.chooseVoteOptionIndex(a, b, 0.5);
                assertThat(choice).isIn(0, 1);
            }
        }
    }
}
