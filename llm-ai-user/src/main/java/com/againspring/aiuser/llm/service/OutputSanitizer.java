package com.againspring.aiuser.llm.service;

import org.springframework.stereotype.Service;

@Service
public class OutputSanitizer {
    private static final int MAX_POST = 800;
    private static final int MAX_COMMENT = 300;

    public String sanitizePost(String raw) {
        return sanitize(raw, MAX_POST);
    }

    public String sanitizeComment(String raw) {
        return sanitize(raw, MAX_COMMENT);
    }

    private String sanitize(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw
            .replaceAll("(?m)^#{1,6}\\s+", "")          // strip markdown headers
            .replaceAll("\\*{1,2}([^*]+)\\*{1,2}", "$1") // remove bold/italic markers
            .replaceAll("`([^`]+)`", "$1")               // remove inline code
            .replaceAll("(?m)^>\\s*", "")                // remove blockquotes
            .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")  // remove markdown links
            .trim();
        // Remove AI assistant boilerplate
        s = s.replaceAll("(?i)(네,? ?저는|안녕하세요|물론이죠|물론입니다|제가 도와드릴게요)", "");
        s = s.trim();
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
