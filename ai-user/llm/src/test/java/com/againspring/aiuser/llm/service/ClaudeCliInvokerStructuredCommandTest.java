package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaudeCliInvokerStructuredCommandTest {

    @Test
    void structuredClaudeCommandPassesNativeJsonSchemaWithoutSessionPersistence() {
        List<String> command = ClaudeCliInvoker.buildCommand("claude", "claude-sonnet-5", "{\"type\":\"object\"}", "system");

        assertTrue(command.containsAll(List.of("--json-schema", "{\"type\":\"object\"}", "--no-session-persistence")));
    }

    /**
     * 2026-08-21 Token reduction: When --json-schema is used, --disallowedTools should contain
     * an explicit list that preserves StructuredOutput (reduces overhead ~25k -> ~18.8k tokens).
     */
    @Test
    void structuredClaudeCommandUsesExplicitDisallowedToolsWithSchema() {
        List<String> command = ClaudeCliInvoker.buildCommand("claude", "claude-sonnet-5", "{\"type\":\"object\"}", "system");

        // Verify --disallowedTools is present with the explicit list (not "*")
        int disallowIdx = command.indexOf("--disallowedTools");
        assertTrue(disallowIdx >= 0, "--disallowedTools flag must be present");
        assertTrue(disallowIdx + 1 < command.size(), "--disallowedTools must have a value");

        String disallowValue = command.get(disallowIdx + 1);
        assertTrue(disallowValue.contains("Bash"), "Should contain Bash in disallow list");
        assertTrue(disallowValue.contains("Read"), "Should contain Read in disallow list");
        assertFalse(disallowValue.equals("*"), "With schema, should NOT use wildcard disallow");
        // StructuredOutput must NOT be in the list (it's a tool that JSON schema needs)
        assertFalse(disallowValue.contains("StructuredOutput"), "StructuredOutput must NOT be in disallow list");
    }

    /**
     * 2026-08-21 Token reduction: When no --json-schema is used, --disallowedTools should be "*"
     * (blocks all tools, reduces overhead ~25k -> ~279 tokens).
     */
    @Test
    void nonStructuredClaudeCommandUsesWildcardDisallowedTools() {
        List<String> command = ClaudeCliInvoker.buildCommand("claude", "claude-sonnet-5", null, "system");

        // Verify --disallowedTools is present with wildcard value
        int disallowIdx = command.indexOf("--disallowedTools");
        assertTrue(disallowIdx >= 0, "--disallowedTools flag must be present");
        assertTrue(disallowIdx + 1 < command.size(), "--disallowedTools must have a value");

        String disallowValue = command.get(disallowIdx + 1);
        assertEquals("*", disallowValue, "Without schema, should use wildcard disallow");
    }

    /**
     * 2026-08-01 회귀 방지: userPart(페르소나 캐스트 JSON 포함)를 CLI 인자로 넘기면
     * OS ARG_MAX를 넘겨 "Argument list too long"(E2BIG)이 난다. stdin으로만 전달해야 한다.
     */
    @Test
    void structuredClaudeCommandNeverIncludesUserPartAsArgument() {
        String hugeUserPart = "x".repeat(5_000_000);
        List<String> command = ClaudeCliInvoker.buildCommand("claude", "claude-sonnet-5", null, "system");

        assertTrue(command.stream().noneMatch(arg -> arg.contains(hugeUserPart)));
    }
}
