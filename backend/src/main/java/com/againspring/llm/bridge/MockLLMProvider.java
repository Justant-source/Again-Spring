package com.againspring.llm.bridge;

import com.againspring.llm.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Mock LLM Provider for testing and development.
 * Returns scripted responses based on request metadata (turnNumber, etc).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "mock")
public class MockLLMProvider implements LLMProvider {

    private static final Map<String, String> MOCK_RESPONSES = new HashMap<>();

    static {
        // Fixture responses by turn number
        MOCK_RESPONSES.put("turn_1_a", "[Mock] Partner A expresses their concern about communication patterns");
        MOCK_RESPONSES.put("turn_2_b", "[Mock] Partner B responds with their perspective on the issue");
        MOCK_RESPONSES.put("turn_3_a", "[Mock] Mediator asks clarifying question for Partner A");
        MOCK_RESPONSES.put("turn_3_b", "[Mock] Mediator asks clarifying question for Partner B");
        MOCK_RESPONSES.put("turn_4_a", "[Mock] Partner A reflects on feedback");
        MOCK_RESPONSES.put("turn_4_b", "[Mock] Partner B reflects on feedback");
        MOCK_RESPONSES.put("turn_5_a", "[Mock] Partner A explores perspective taking");
        MOCK_RESPONSES.put("turn_5_b", "[Mock] Partner B explores perspective taking");
        MOCK_RESPONSES.put("turn_6_a", "[Mock] Partner A discusses solutions");
        MOCK_RESPONSES.put("turn_6_b", "[Mock] Partner B discusses solutions");
    }

    @Override
    public LLMResponse invoke(LLMRequest request) throws LLMException {
        String correlationId = request.getCorrelationId() != null
                ? request.getCorrelationId()
                : UUID.randomUUID().toString();

        // Extract turn number from metadata for fixture lookup
        Object turnMeta = request.getMetadata().get("turnNumber");
        Object roleMeta = request.getMetadata().get("role");
        String key = "default";

        if (turnMeta != null && roleMeta != null) {
            key = "turn_" + turnMeta + "_" + roleMeta.toString().toLowerCase();
        }

        String mockResponse = MOCK_RESPONSES.getOrDefault(key, "[Mock] Default response");

        log.info("MockLLMProvider returning fixture response: {}", key);

        return LLMResponse.builder()
                .rawText(mockResponse)
                .tokensUsed(mockResponse.length() / 4)
                .latencyMs(100)  // Minimal latency for mock
                .provider(getProviderName())
                .correlationId(correlationId)
                .isFallback(false)
                .build();
    }

    @Override
    public CompletableFuture<LLMResponse> invokeAsync(LLMRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return invoke(request);
            } catch (LLMException e) {
                throw new CompletionException(e);
            }
        });
    }

    @Override
    public String invoke(String prompt, String model) {
        log.info("MockLLMProvider.invoke(String, String) called with model={}", model);
        return "[Mock] 두 분의 이야기를 잘 들었어요. 서로의 마음을 조금 더 나눠보실 수 있을까요?";
    }

    @Override
    public CancelableInvocation invokeCancelable(String prompt, String model, String sessionId) {
        CancelableInvocation ci = new CancelableInvocation(UUID.randomUUID().toString(), sessionId);
        ci.getResultFuture().complete("[Mock] 두 분의 이야기를 잘 들었어요.");
        return ci;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public boolean isHealthy() {
        return true;  // Mock provider always healthy
    }
}
