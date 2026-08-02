package com.againspring.service.community;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostSearchQuery — 정규화·이스케이프·감쇠")
class PostSearchQueryTest {

    @Test
    void normalize_collapsesWhitespace() {
        assertThat(PostSearchQuery.normalize("  시댁   갈등  ")).isEqualTo("시댁 갈등");
    }

    @Test
    void isTooShort_usesCodePoints() {
        assertThat(PostSearchQuery.isTooShort("가")).isTrue();
        assertThat(PostSearchQuery.isTooShort("가나")).isFalse();
        assertThat(PostSearchQuery.isTooShort("a")).isTrue();
        assertThat(PostSearchQuery.isTooShort("ab")).isFalse();
    }

    @Test
    void tokens_splitAndCap() {
        assertThat(PostSearchQuery.tokens("시댁 갈등 야근")).containsExactly("시댁", "갈등", "야근");
        assertThat(PostSearchQuery.tokens("a b c d e f g h i j")).hasSize(PostSearchQuery.MAX_TOKENS);
    }

    @Test
    void escapeLike_escapesWildcards() {
        assertThat(PostSearchQuery.escapeLike("100%_ok!")).isEqualTo("100!%!_ok!!");
        assertThat(PostSearchQuery.containsPattern("a_b")).isEqualTo("%a!_b%");
    }

    @Test
    void timeDecay_halfLifeAndFloor() {
        assertThat(PostSearchQuery.timeDecay(0)).isEqualTo(1.0);
        assertThat(PostSearchQuery.timeDecay(PostSearchQuery.HALF_LIFE_SECONDS)).isCloseTo(0.5, within(1e-9));
        assertThat(PostSearchQuery.timeDecay(PostSearchQuery.HALF_LIFE_SECONDS * 2)).isCloseTo(0.25, within(1e-9));
        assertThat(PostSearchQuery.timeDecay(PostSearchQuery.HALF_LIFE_SECONDS * 20))
                .isEqualTo(PostSearchQuery.DECAY_FLOOR);
    }

    @Test
    void popularity_weightsVotesDouble() {
        assertThat(PostSearchQuery.popularityScore(3, 1)).isEqualTo(2.0 * 3 + 1 + 1);
    }

    private static org.assertj.core.data.Offset<Double> within(double eps) {
        return org.assertj.core.data.Offset.offset(eps);
    }
}
