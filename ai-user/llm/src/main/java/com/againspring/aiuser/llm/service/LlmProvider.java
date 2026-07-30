package com.againspring.aiuser.llm.service;

/** Login-session based providers supported by the AI-user worker. */
public enum LlmProvider {
    CLAUDE, CODEX;

    public static LlmProvider parse(String value) {
        if (value == null || value.isBlank()) return CLAUDE;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported LLM provider: " + value);
        }
    }
}
