package com.againspring.llm.fallback;

import com.againspring.domain.enums.ConflictType;
import com.againspring.domain.enums.TurnRole;
import com.againspring.llm.LLMResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Provides fallback (pre-canned) responses when LLM invocation fails.
 * Keyed by (turnNumber, role, conflictType).
 */
@Slf4j
@Component
public class FallbackResponses {

    private static final Map<String, String> FALLBACK_MAP = new HashMap<>();

    static {
        // Turn 1 fallbacks
        FALLBACK_MAP.put("1_A_DEFAULT", "감정을 정리해서 설명해 주셨네요. 지금 갈등 상황을 더 깊이 이해하기 위해, " +
                "이 문제가 발생한 구체적인 상황이나 배경을 설명해 주실 수 있을까요?");

        // Turn 2 fallbacks
        FALLBACK_MAP.put("2_B_DEFAULT", "당신의 관점을 들을 수 있어 고마워요. 이제 함께 이 갈등의 근본 원인을 찾아보겠습니다. " +
                "이 상황이 얼마나 오래 지속되었나요?");

        // Turn 3 fallbacks
        FALLBACK_MAP.put("3_A_DEFAULT", "그 말씀을 더 자세히 설명해 주실 수 있을까요? 구체적인 예시가 있다면 도움이 될 것 같습니다.");
        FALLBACK_MAP.put("3_B_DEFAULT", "당신의 입장을 이해합니다. 혹시 상대방과 함께 이 문제를 해결하고 싶으신가요?");

        // Generic fallbacks for other turns
        FALLBACK_MAP.put("4_A_DEFAULT", "계속 이 이야기를 나누어 주셔서 감사합니다.");
        FALLBACK_MAP.put("4_B_DEFAULT", "당신의 생각을 공유해 주셔서 감사합니다.");
        FALLBACK_MAP.put("5_A_DEFAULT", "상대방의 관점을 이해하는 데 도움이 되었나요?");
        FALLBACK_MAP.put("5_B_DEFAULT", "앞으로 어떻게 나아가고 싶으신가요?");
        FALLBACK_MAP.put("6_A_DEFAULT", "이 과정을 통해 어떤 깨달음을 얻으셨나요?");
        FALLBACK_MAP.put("6_B_DEFAULT", "앞으로의 관계를 위해 어떤 노력을 하고 싶으신가요?");
    }

    /**
     * Get fallback response for a specific turn, role, and conflict type.
     *
     * @param turnNumber 1-6
     * @param role A or B
     * @param conflictType optional conflict type
     * @return fallback LLMResponse with isFallback=true
     */
    public LLMResponse forTurn(int turnNumber, TurnRole role, ConflictType conflictType) {
        String key = turnNumber + "_" + role.name() + "_" +
                (conflictType != null ? conflictType.name() : "DEFAULT");

        String content = FALLBACK_MAP.getOrDefault(key,
                FALLBACK_MAP.getOrDefault(turnNumber + "_" + role.name() + "_DEFAULT",
                        "처리 중에 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));

        log.info("Using fallback response: turn={}, role={}, conflictType={}",
                turnNumber, role, conflictType);

        return LLMResponse.builder()
                .rawText(content)
                .provider("fallback")
                .correlationId(UUID.randomUUID().toString())
                .tokensUsed(estimateTokens(content))
                .latencyMs(0)
                .isFallback(true)
                .build();
    }

    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }
}
