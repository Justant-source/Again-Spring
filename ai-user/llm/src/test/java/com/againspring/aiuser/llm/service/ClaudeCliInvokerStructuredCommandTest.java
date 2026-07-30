package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeCliInvokerStructuredCommandTest {

    @Test
    void structuredClaudeCommandPassesNativeJsonSchemaWithoutSessionPersistence() {
        List<String> command = ClaudeCliInvoker.buildCommand("claude", "claude-sonnet-4-6", "{\"type\":\"object\"}", "system", "input");

        assertTrue(command.containsAll(List.of("--json-schema", "{\"type\":\"object\"}", "--no-session-persistence")));
    }
}
