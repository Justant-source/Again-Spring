package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeCliInvokerStructuredCommandTest {

    @Test
    void structuredClaudeCommandPassesNativeJsonSchemaWithoutSessionPersistence() {
        List<String> command = ClaudeCliInvoker.buildCommand("claude", "claude-sonnet-4-6", "{\"type\":\"object\"}", "system");

        assertTrue(command.containsAll(List.of("--json-schema", "{\"type\":\"object\"}", "--no-session-persistence")));
    }

    /**
     * 2026-08-01 회귀 방지: userPart(페르소나 캐스트 JSON 포함)를 CLI 인자로 넘기면
     * OS ARG_MAX를 넘겨 "Argument list too long"(E2BIG)이 난다. stdin으로만 전달해야 한다.
     */
    @Test
    void structuredClaudeCommandNeverIncludesUserPartAsArgument() {
        String hugeUserPart = "x".repeat(5_000_000);
        List<String> command = ClaudeCliInvoker.buildCommand("claude", "claude-sonnet-4-6", null, "system");

        assertTrue(command.stream().noneMatch(arg -> arg.contains(hugeUserPart)));
    }
}
