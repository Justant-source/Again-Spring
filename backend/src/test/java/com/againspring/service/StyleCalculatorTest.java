package com.againspring.service;

import com.againspring.service.StyleCalculator.CommunicationStyle;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for StyleCalculator.
 */
class StyleCalculatorTest {

    private StyleCalculator styleCalculator;

    @BeforeEach
    void setUp() {
        styleCalculator = new StyleCalculator();
    }

    @Test
    void testCalculateWaveStyle() {
        // Wave: [2, 5, 3, 4, 1, 5, 3, 5, 4, 2]
        List<Integer> answers = List.of(2, 5, 3, 4, 1, 5, 3, 5, 4, 2);

        CommunicationStyle style = styleCalculator.calculateStyle(answers);

        assertThat(style).isEqualTo(CommunicationStyle.WAVE);
        assertThat(style.getEmoji()).isEqualTo("🌊");
        assertThat(style.getLabel()).isEqualTo("파도형");
    }

    @Test
    void testCalculateMountainStyle() {
        // Mountain: High q1, Low q2 (conservative, stable)
        List<Integer> answers = List.of(5, 1, 2, 2, 3, 2, 1, 2, 2, 2);

        CommunicationStyle style = styleCalculator.calculateStyle(answers);

        assertThat(style).isEqualTo(CommunicationStyle.MOUNTAIN);
    }

    @Test
    void testCalculateStarStyle() {
        // Star: High q3, q7 (logical, concrete)
        List<Integer> answers = List.of(3, 3, 5, 2, 3, 2, 5, 2, 2, 2);

        CommunicationStyle style = styleCalculator.calculateStyle(answers);

        assertThat(style).isEqualTo(CommunicationStyle.STAR);
    }

    @Test
    void testInvalidAnswersLengthThrowsException() {
        // Only 9 answers
        List<Integer> answers = List.of(1, 2, 3, 4, 5, 1, 2, 3, 4);

        assertThatThrownBy(() -> styleCalculator.calculateStyle(answers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 answers");
    }

    @Test
    void testInvalidAnswerRangeThrowsException() {
        // One answer is 6 (out of 1-5 range)
        List<Integer> answers = List.of(1, 2, 3, 4, 5, 6, 1, 2, 3, 4);

        assertThatThrownBy(() -> styleCalculator.calculateStyle(answers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 5");
    }

    @Test
    void testAllStylesHaveRequiredAttributes() {
        for (CommunicationStyle style : CommunicationStyle.values()) {
            assertThat(style.getValue()).isNotBlank();
            assertThat(style.getEmoji()).isNotBlank();
            assertThat(style.getLabel()).isNotBlank();
            assertThat(style.getDescription()).isNotBlank();
            assertThat(style.getStrengths()).hasSize(2);
            assertThat(style.getCaution()).hasSize(2);
        }
    }
}
