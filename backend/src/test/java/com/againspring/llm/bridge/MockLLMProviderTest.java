package com.againspring.llm.bridge;

import com.againspring.llm.LLMRequest;
import com.againspring.llm.LLMResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class MockLLMProviderTest {

    private MockLLMProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockLLMProvider();
    }

    @Test
    void testInvokeReturnsFixture() throws Exception {
        LLMRequest request = LLMRequest.builder()
                .systemPrompt("system")
                .userInput("user input")
                .metadata(Map.of("turnNumber", 1, "role", "a"))
                .build();

        LLMResponse response = provider.invoke(request);

        assertThat(response).isNotNull();
        assertThat(response.getRawText()).contains("[Mock]");
        assertThat(response.getProvider()).isEqualTo("mock");
        assertThat(response.isFallback()).isFalse();
    }

    @Test
    void testProviderName() {
        assertThat(provider.getProviderName()).isEqualTo("mock");
    }

    @Test
    void testHealthAlwaysTrue() {
        assertThat(provider.isHealthy()).isTrue();
    }

    @Test
    void testAsyncInvoke() throws Exception {
        LLMRequest request = LLMRequest.builder()
                .systemPrompt("system")
                .userInput("input")
                .build();

        var future = provider.invokeAsync(request);
        LLMResponse response = future.get();

        assertThat(response).isNotNull();
        assertThat(response.getRawText()).isNotBlank();
    }

    @Test
    void testDifferentTurnFixtures() throws Exception {
        for (int turn = 1; turn <= 6; turn++) {
            for (String role : new String[]{"a", "b"}) {
                LLMRequest request = LLMRequest.builder()
                        .systemPrompt("system")
                        .userInput("input")
                        .metadata(Map.of("turnNumber", turn, "role", role))
                        .build();

                LLMResponse response = provider.invoke(request);
                assertThat(response.getRawText()).isNotBlank();
            }
        }
    }
}
