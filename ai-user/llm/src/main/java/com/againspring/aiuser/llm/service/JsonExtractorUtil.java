package com.againspring.aiuser.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lenient JSON extraction for prompt-instructed structured generation.
 * When schema instructions are embedded in prompts (vs. --json-schema), the model
 * may wrap JSON in code fences or prose. This utility extracts valid JSON
 * with fallback strategies.
 */
public class JsonExtractorUtil {
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Extract JSON from potentially-wrapped text.
     *
     * Strategy:
     * 1. Try direct parse (clean JSON or with whitespace)
     * 2. Strip ```json / ``` fences
     * 3. Extract substring from first { to last } (or [ to ])
     * 4. Fail with clear error
     *
     * @param text Raw text from model (may contain prose, fences, etc.)
     * @return Parsed JsonNode
     * @throws RuntimeException if extraction/parse fails
     */
    public static JsonNode extract(String text) {
        if (text == null || text.isBlank()) {
            throw new RuntimeException("Empty or null response text");
        }

        String raw = text.trim();

        // Attempt 1: Direct parse
        try {
            return JSON.readTree(raw);
        } catch (Exception ignored) {
            // Continue to next strategy
        }

        // Attempt 2: Strip common code fence patterns
        String stripped = stripCodeFences(raw);
        if (!stripped.equals(raw)) {
            try {
                return JSON.readTree(stripped);
            } catch (Exception ignored) {
                // Continue to next strategy
            }
        }

        // Attempt 3: Extract substring from first { to last } or [ to ]
        String extracted = extractJsonSubstring(raw);
        if (!extracted.equals(raw)) {
            try {
                return JSON.readTree(extracted);
            } catch (Exception ignored) {
                // Continue to failure case
            }
        }

        // All strategies failed
        throw new RuntimeException("Failed to extract valid JSON from response");
    }

    /**
     * Strip common code fence patterns: ```json ... ``` or ``` ... ```
     */
    private static String stripCodeFences(String text) {
        String result = text.trim();
        // Remove leading ```json or ```
        if (result.startsWith("```json")) {
            result = result.substring(7).trim();
        } else if (result.startsWith("```")) {
            result = result.substring(3).trim();
        }
        // Remove trailing ```
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3).trim();
        }
        return result;
    }

    /**
     * Extract JSON substring from first opening brace/bracket to last closing brace/bracket.
     * Handles text with leading prose, trailing commentary, etc.
     */
    private static String extractJsonSubstring(String text) {
        String trimmed = text.trim();

        // Determine root type (object or array)
        int firstOpenBrace = trimmed.indexOf('{');
        int firstOpenBracket = trimmed.indexOf('[');

        if (firstOpenBrace < 0 && firstOpenBracket < 0) {
            return text; // No JSON markers found; return as-is (will fail in parse)
        }

        // Determine starting position and closing marker
        int start;
        char closeChar;
        if (firstOpenBrace < 0) {
            // Array root
            start = firstOpenBracket;
            closeChar = ']';
        } else if (firstOpenBracket < 0) {
            // Object root
            start = firstOpenBrace;
            closeChar = '}';
        } else {
            // Both present; use whichever comes first
            if (firstOpenBrace < firstOpenBracket) {
                start = firstOpenBrace;
                closeChar = '}';
            } else {
                start = firstOpenBracket;
                closeChar = ']';
            }
        }

        // Find last occurrence of closing marker
        int end = trimmed.lastIndexOf(closeChar);
        if (end < start) {
            return text; // Malformed; return as-is (will fail in parse)
        }

        return trimmed.substring(start, end + 1);
    }
}
