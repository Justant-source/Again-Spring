package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmProviderTest {
    @Test
    void parseAcceptsAllFour() {
        assertEquals(LlmProvider.CLAUDE, LlmProvider.parse("claude"));
        assertEquals(LlmProvider.CODEX, LlmProvider.parse("CODEX"));
        assertEquals(LlmProvider.API, LlmProvider.parse("api"));
        assertEquals(LlmProvider.STUB, LlmProvider.parse("Stub"));
    }

    @Test
    void parseDefaultsToClaudeAndMapsLegacyCli() {
        assertEquals(LlmProvider.CLAUDE, LlmProvider.parse(null));
        assertEquals(LlmProvider.CLAUDE, LlmProvider.parse(""));
        assertEquals(LlmProvider.CLAUDE, LlmProvider.parse("CLI"));
    }

    @Test
    void parseRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> LlmProvider.parse("gemini"));
    }

    @Test
    void parseLegacyPrefersProviderThenBackend() {
        assertEquals(LlmProvider.CODEX, LlmProvider.parseLegacy("CODEX", "API"));
        assertEquals(LlmProvider.API, LlmProvider.parseLegacy(null, "API"));
        assertEquals(LlmProvider.CLAUDE, LlmProvider.parseLegacy(null, "CLI"));
        assertEquals(LlmProvider.CLAUDE, LlmProvider.parseLegacy(null, null));
    }
}
