package com.againspring.llm.claudeapi;

import com.againspring.llm.LLMRequest;
import com.againspring.llm.LLMResponse;
import com.againspring.llm.bridge.exception.LLMCapacityException;
import com.againspring.llm.claudeapi.dto.AnthropicMessage;
import com.againspring.llm.claudeapi.dto.AnthropicRequest;
import com.againspring.llm.claudeapi.dto.AnthropicResponse;
import com.againspring.llm.claudeapi.dto.AnthropicTextBlock;
import com.againspring.llm.fallback.FallbackResponses;
import com.againspring.llm.monitoring.LLMCallLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ClaudeApiProviderTest {

    @Mock
    private FallbackResponses fallbackResponses;

    @Mock
    private LLMCallLogger llmCallLogger;

    private ClaudeApiProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        provider = new ClaudeApiProvider(objectMapper, fallbackResponses, llmCallLogger);
        ReflectionTestUtils.setField(provider, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(provider, "defaultModel", "claude-sonnet-4-5");
        ReflectionTestUtils.setField(provider, "maxConcurrency", 8);
        ReflectionTestUtils.setField(provider, "cacheEnabled", true);
        provider.init();
    }

    @Test
    void testGetProviderName() {
        assertEquals("claude-api", provider.getProviderName());
    }

    @Test
    void testIsHealthy_withValidApiKey() {
        assertTrue(provider.isHealthy());
    }

    @Test
    void testIsHealthy_withBlankApiKey() {
        ReflectionTestUtils.setField(provider, "apiKey", "");
        assertFalse(provider.isHealthy());
    }

    @Test
    void testIsHealthy_withNullApiKey() {
        ReflectionTestUtils.setField(provider, "apiKey", null);
        assertFalse(provider.isHealthy());
    }

    @Test
    void testInvoke_throwsWhenNotHealthy() {
        ReflectionTestUtils.setField(provider, "apiKey", "");

        assertThrows(Exception.class, () -> {
            provider.invoke("test prompt", "claude-sonnet-4-5");
        });
    }

    @Test
    void testAnthropicTextBlock_text() {
        AnthropicTextBlock block = AnthropicTextBlock.text("Hello World");
        assertEquals("text", block.type());
        assertEquals("Hello World", block.text());
        assertNull(block.cacheControl());
    }

    @Test
    void testAnthropicTextBlock_cached() {
        AnthropicTextBlock block = AnthropicTextBlock.cached("System Prompt");
        assertEquals("text", block.type());
        assertEquals("System Prompt", block.text());
        assertNotNull(block.cacheControl());
        assertEquals("ephemeral", block.cacheControl().type());
    }

    @Test
    void testAnthropicMessage_user() {
        AnthropicMessage msg = AnthropicMessage.user("User input");
        assertEquals("user", msg.role());
        assertEquals(1, msg.content().size());
        assertEquals("User input", msg.content().get(0).text());
    }

    @Test
    void testAnthropicMessage_assistant() {
        AnthropicMessage msg = AnthropicMessage.assistant("Assistant response");
        assertEquals("assistant", msg.role());
        assertEquals(1, msg.content().size());
        assertEquals("Assistant response", msg.content().get(0).text());
    }

    @Test
    void testAnthropicRequest_simple() {
        AnthropicRequest req = AnthropicRequest.simple("claude-sonnet-4-5", 1024, "test prompt");
        assertEquals("claude-sonnet-4-5", req.getModel());
        assertEquals(1024, req.getMaxTokens());
        assertNull(req.getSystem());
        assertEquals(1, req.getMessages().size());
        assertEquals("user", req.getMessages().get(0).role());
    }

    @Test
    void testAnthropicResponse_text() {
        AnthropicResponse resp = AnthropicResponse.builder()
            .id("msg-123")
            .type("message")
            .model("claude-sonnet-4-5")
            .stopReason("end_turn")
            .content(List.of(
                AnthropicTextBlock.text("Hello"),
                AnthropicTextBlock.text(" World")
            ))
            .usage(AnthropicResponse.UsageBlock.builder()
                .inputTokens(10)
                .outputTokens(5)
                .cacheReadInputTokens(0)
                .cacheCreationInputTokens(0)
                .build())
            .build();

        assertEquals("Hello World", resp.text());
    }

    @Test
    void testAnthropicResponse_cacheMetrics() {
        AnthropicResponse resp = AnthropicResponse.builder()
            .id("msg-123")
            .type("message")
            .model("claude-sonnet-4-5")
            .stopReason("end_turn")
            .content(List.of(AnthropicTextBlock.text("Response")))
            .usage(AnthropicResponse.UsageBlock.builder()
                .inputTokens(100)
                .outputTokens(50)
                .cacheReadInputTokens(200)
                .cacheCreationInputTokens(150)
                .build())
            .build();

        assertEquals(200, resp.cacheReadInputTokens());
        assertEquals(150, resp.cacheCreationInputTokens());
        assertEquals(300, resp.totalInputTokens());  // 100 + 200
    }

    @Test
    void testAnthropicResponse_emptyCacheMetrics() {
        AnthropicResponse resp = AnthropicResponse.builder()
            .id("msg-123")
            .type("message")
            .model("claude-sonnet-4-5")
            .stopReason("end_turn")
            .content(List.of(AnthropicTextBlock.text("Response")))
            .usage(AnthropicResponse.UsageBlock.builder()
                .inputTokens(50)
                .outputTokens(25)
                .cacheReadInputTokens(null)
                .cacheCreationInputTokens(null)
                .build())
            .build();

        assertEquals(0, resp.cacheReadInputTokens());
        assertEquals(0, resp.cacheCreationInputTokens());
        assertEquals(50, resp.totalInputTokens());
    }

    @Test
    void testLLMRequest_conversion() {
        LLMRequest request = LLMRequest.builder()
            .systemPrompt("You are helpful")
            .userInput("Hello")
            .correlationId("corr-123")
            .build();

        assertNotNull(request.getSystemPrompt());
        assertEquals("Hello", request.getUserInput());
        assertEquals("corr-123", request.getCorrelationId());
    }
}
