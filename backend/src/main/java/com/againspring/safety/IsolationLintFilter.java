package com.againspring.safety;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * Phase D PR-4 — LLM 응답 본문에 sender 라벨이 노출됐는지 검증.
 * 3중 격리 방어의 3층: 프롬프트(PR-3) + duo_chat.md(PR-4) + 이 필터.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §7 방어 3
 */
@Component
public class IsolationLintFilter {

    private static final Pattern SENDER_LABEL = Pattern.compile(
        "\\b(USER_A|USER_B|MEDIATOR_TO_A|MEDIATOR_TO_B)\\b");

    public boolean violatesIsolation(String mediatorMessage) {
        return mediatorMessage != null && SENDER_LABEL.matcher(mediatorMessage).find();
    }
}
