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
            .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")    // remove bold markers
            .replaceAll("(?<![\\w가-힣])\\*([^*\\n]+)\\*(?![\\w가-힣])", "$1") // remove italic markers
            .replaceAll("`([^`]+)`", "$1")               // remove inline code
            .replaceAll("(?m)^>\\s*", "")                // remove blockquotes
            .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")  // remove markdown links
            .trim();
        // Remove AI assistant boilerplate (anchor to line start, remove only leading boilerplate)
        s = s.replaceFirst("^(?i)(안녕하세요[,!. ]*|물론이죠[,. ]*|물론입니다[,. ]*|네,? 저는 [^\n]*\n?|제가 도와드릴게요[,. ]*)", "").stripLeading();
        s = s.trim();
        if (s.length() > maxLen) {
            // Find last sentence boundary near maxLen
            int cutAt = maxLen;
            String endings = ".!?\nㅋㅠ";
            for (int i = maxLen - 1; i >= Math.max(0, maxLen - 60); i--) {
                char c = s.charAt(i);
                if (endings.indexOf(c) >= 0) { cutAt = i + 1; break; }
            }
            s = s.substring(0, cutAt).stripTrailing();
        }
        return s;
    }
}
