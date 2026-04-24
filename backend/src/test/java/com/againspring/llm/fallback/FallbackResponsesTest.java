package com.againspring.llm.fallback;

import com.againspring.domain.enums.ConflictType;
import com.againspring.domain.enums.TurnRole;
import com.againspring.llm.LLMResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class FallbackResponsesTest {

    private FallbackResponses fallback;

    @BeforeEach
    void setUp() {
        fallback = new FallbackResponses();
    }

    @Test
    void testFallbackForTurn1A() {
        LLMResponse response = fallback.forTurn(1, TurnRole.A, null);

        assertThat(response).isNotNull();
        assertThat(response.getRawText()).isNotBlank();
        assertThat(response.isFallback()).isTrue();
        assertThat(response.getProvider()).isEqualTo("fallback");
    }

    @Test
    void testFallbackForTurn2B() {
        LLMResponse response = fallback.forTurn(2, TurnRole.B, ConflictType.FACTUAL);

        assertThat(response).isNotNull();
        assertThat(response.getRawText()).isNotBlank();
        assertThat(response.isFallback()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void testAllTurnsHaveFallbacks(int turnNumber) {
        LLMResponse responseA = fallback.forTurn(turnNumber, TurnRole.A, null);
        LLMResponse responseB = fallback.forTurn(turnNumber, TurnRole.B, null);

        assertThat(responseA.getRawText()).isNotBlank();
        assertThat(responseB.getRawText()).isNotBlank();
        assertThat(responseA.isFallback()).isTrue();
        assertThat(responseB.isFallback()).isTrue();
    }

    @Test
    void testFallbacksAreKorean() {
        LLMResponse response = fallback.forTurn(1, TurnRole.A, null);
        // Should contain Korean characters
        assertThat(response.getRawText()).matches(".*[가-힣].*");
    }

    @Test
    void testCorrelationIdPresent() {
        LLMResponse response = fallback.forTurn(3, TurnRole.A, null);
        assertThat(response.getCorrelationId()).isNotNull();
        assertThat(response.getCorrelationId()).isNotEmpty();
    }

    @Test
    void testTokenEstimate() {
        LLMResponse response = fallback.forTurn(1, TurnRole.A, null);
        assertThat(response.getTokensUsed()).isGreaterThan(0);
    }
}
