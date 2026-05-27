package com.againspring.service.marketing.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured output from a ContentGenerator.
 * bodyText: sanitized plain text for storage (body_text column).
 * hashtags: hashtag string for storage (hashtags column).
 * structuredPayload: parsed JSON map from the LLM (quoteCard, slides, imageSlots, etc.).
 */
public record GenerationOutput(
        String bodyText,
        String hashtags,
        Map<String, Object> structuredPayload
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern FENCE_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

    public static GenerationOutput textOnly(String bodyText) {
        return new GenerationOutput(bodyText, null, Map.of());
    }

    /**
     * Parse LLM response as JSON. Handles code fence wrapping.
     * On parse failure returns textOnly with the raw response.
     */
    @SuppressWarnings("unchecked")
    public static GenerationOutput fromLlmJson(String raw) {
        if (raw == null || raw.isBlank()) return textOnly("");

        String jsonCandidate = extractJsonBlock(raw);
        try {
            Map<String, Object> payload = MAPPER.readValue(jsonCandidate,
                    new TypeReference<Map<String, Object>>() {});

            // Extract bodyText: prefer 'markdown' → fallback join 'tweets'
            String bodyText = null;
            if (payload.containsKey("markdown")) {
                bodyText = (String) payload.get("markdown");
            } else if (payload.containsKey("caption")) {
                bodyText = (String) payload.get("caption");
            } else if (payload.containsKey("tweets")) {
                Object tweets = payload.get("tweets");
                if (tweets instanceof List<?> list) {
                    bodyText = String.join("\n\n", list.stream()
                            .map(Object::toString).toList());
                }
            } else {
                bodyText = raw; // fallback
            }

            // Extract hashtags
            String hashtags = null;
            if (payload.containsKey("hashtags")) {
                Object h = payload.get("hashtags");
                if (h instanceof List<?> list) {
                    hashtags = String.join(" ", list.stream().map(Object::toString).toList());
                } else if (h instanceof String s) {
                    hashtags = s;
                }
            }

            return new GenerationOutput(bodyText, hashtags, payload);
        } catch (Exception e) {
            return textOnly(raw);
        }
    }

    private static String extractJsonBlock(String text) {
        Matcher m = FENCE_PATTERN.matcher(text);
        if (m.find()) return m.group(1).trim();
        // Try to find raw JSON object
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }
}
