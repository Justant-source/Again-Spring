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

    /**
     * 2026-08-21 프롬프트 모드 OFF: 명령어는 현재와 byte-identical
     */
    @Test
    void flagOffProducesIdenticalCommand() {
        List<String> legacyCommand = ClaudeCliInvoker.buildCommand("claude", "claude-sonnet-5", "{\"type\":\"object\"}", "system", false);
        List<String> defaultCommand = ClaudeCliInvoker.buildCommand("claude", "claude-sonnet-5", "{\"type\":\"object\"}", "system");

        assertEquals(legacyCommand, defaultCommand, "Flag OFF must produce identical command to default behavior");
    }

    /**
     * 2026-08-21 프롬프트 모드 ON (스키마 있음): --disallowedTools "*", 스키마 지시 프롬프트에 포함
     */
    @Test
    void flagOnWithSchemaUsesWildcardDisallow() {
        List<String> command = ClaudeCliInvoker.buildCommand("claude", "claude-sonnet-5", "{\"type\":\"object\"}", "system", true);

        // Must use wildcard disallow (not explicit list)
        int disallowIdx = command.indexOf("--disallowedTools");
        assertTrue(disallowIdx >= 0, "--disallowedTools must be present");
        assertEquals("*", command.get(disallowIdx + 1), "Prompt mode should use wildcard --disallowedTools");

        // Must NOT have --json-schema
        assertFalse(command.contains("--json-schema"), "Prompt mode should not pass --json-schema");
    }

    /**
     * 2026-08-21 프롬프트 모드 ON (스키마 없음): --disallowedTools "*"
     */
    @Test
    void flagOnNoSchemaUsesWildcardDisallow() {
        List<String> command = ClaudeCliInvoker.buildCommand("claude", "claude-sonnet-5", null, "system", true);

        int disallowIdx = command.indexOf("--disallowedTools");
        assertEquals("*", command.get(disallowIdx + 1), "No schema should use wildcard --disallowedTools");
        assertFalse(command.contains("--json-schema"), "No --json-schema expected");
    }

    /**
     * 2026-08-21 프롬프트 모드 OFF (스키마 있음): 기존 명령어와 동일 (explicit disallow list + --json-schema)
     */
    @Test
    void flagOffWithSchemaPreservesExplicitDisallow() {
        List<String> command = ClaudeCliInvoker.buildCommand("claude", "claude-sonnet-5", "{\"type\":\"object\"}", "system", false);

        int disallowIdx = command.indexOf("--disallowedTools");
        String disallowValue = command.get(disallowIdx + 1);
        assertFalse(disallowValue.equals("*"), "Flag OFF should not use wildcard");
        assertTrue(disallowValue.contains("Bash"), "Explicit list should be present");
        assertTrue(command.contains("--json-schema"), "--json-schema must be present");
    }
}
