package com.againspring.llm.bridge;

import com.againspring.llm.bridge.exception.LLMSanitizationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Sanitizes user input to prevent prompt injection attacks.
 * Detects and neutralizes injection patterns, control characters, oversized input.
 */
@Slf4j
@Component
public class PromptSanitizer {

    private static final int MAX_INPUT_LENGTH = 8000;

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i)ignore\\s+(previous|above|all)\\s+instructions"),
        Pattern.compile("(?i)you\\s+are\\s+now"),
        Pattern.compile("(?i)system\\s+prompt"),
        Pattern.compile("(?i)</system>"),
        Pattern.compile("(?i)<system>"),
        Pattern.compile("(?i)new\\s+role\\s*:"),
        Pattern.compile("(?i)forget\\s+everything"),
        Pattern.compile("(?i)disregard"),
        Pattern.compile("(?i)override"),
        Pattern.compile("\\[\\[\\[RESET\\]\\]\\]"),
        Pattern.compile("\\[INST\\]"),
        Pattern.compile("\\[/INST\\]")
    );

    /**
     * Sanitizes user input by:
     * 1. Enforcing max length (8000 chars)
     * 2. Detecting injection patterns
     * 3. Removing control characters
     * 4. Returns cleaned string, idempotent
     *
     * @param userInput raw user input
     * @return sanitized string
     * @throws LLMSanitizationException if input is rejected
     */
    public String sanitize(String userInput, String correlationId) throws LLMSanitizationException {
        if (userInput == null || userInput.isBlank()) {
            return "";
        }

        // 1. Length check
        if (userInput.length() > MAX_INPUT_LENGTH) {
            log.warn("User input exceeds max length: {} (max: {}), truncating",
                    userInput.length(), MAX_INPUT_LENGTH);
            userInput = userInput.substring(0, MAX_INPUT_LENGTH);
        }

        // 2. Injection pattern detection
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(userInput).find()) {
                log.warn("Potential prompt injection detected: {}", pattern.pattern());
                userInput = pattern.matcher(userInput).replaceAll("[REDACTED]");
            }
        }

        // 3. Remove control characters (keep Korean, ASCII letters/numbers, punctuation)
        userInput = userInput.replaceAll("[\\p{Cc}\\p{Cn}]", "");

        return userInput;
    }

    public static class SanitizerException extends LLMSanitizationException {
        public SanitizerException(String message, String correlationId) {
            super(message, correlationId);
        }
    }
}
