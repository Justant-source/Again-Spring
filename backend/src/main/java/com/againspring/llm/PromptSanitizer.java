package com.againspring.llm;

import org.springframework.stereotype.Component;

/**
 * LLM 프롬프트 삽입 방어 — 사용자 입력을 안전하게 정규화.
 */
@Component
public class PromptSanitizer {

    private static final int MAX_INPUT_LENGTH = 5000;

    public String sanitize(String input, String correlationId) {
        return sanitize(input);
    }

    public String sanitize(String input) {
        if (input == null) return "";
        String sanitized = input
                .replace("<", "＜")
                .replace(">", "＞")
                .replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]", "")
                .trim();
        if (sanitized.length() > MAX_INPUT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_INPUT_LENGTH);
        }
        return sanitized;
    }
}
