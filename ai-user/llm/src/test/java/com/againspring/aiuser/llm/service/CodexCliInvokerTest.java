package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexCliInvokerTest {

    @Test
    void extractsOnlyCompletedAgentMessageFromCodexJsonStream() throws Exception {
        String text = CodexCliInvoker.extractFinalText(List.of(
                "{\"type\":\"thread.started\"}",
                "{\"type\":\"item.completed\",\"item\":{\"type\":\"command_execution\",\"text\":\"ignore\"}}",
                "{\"type\":\"item.completed\",\"item\":{\"type\":\"agent_message\",\"text\":\"첫 문장\"}}",
                "not-json",
                "{\"type\":\"item.completed\",\"item\":{\"type\":\"agent_message\",\"text\":\" 둘째 문장\"}}"));

        assertEquals("첫 문장둘째 문장", text);
    }

    @Test
    void structuredCodexCommandUsesReadOnlySandboxAndSharedSchemaPath() {
        List<String> command = CodexCliInvoker.buildCommand("codex", "gpt-5.6-terra", "/tmp/thread-plan.schema.json");

        assertTrue(command.containsAll(List.of("--sandbox", "read-only", "--output-schema", "/tmp/thread-plan.schema.json")));
        assertTrue(command.contains("--ephemeral"));
    }
}
