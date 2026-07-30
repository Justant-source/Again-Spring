package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.exception.ClaudeCodeException;
import com.againspring.aiuser.llm.exception.InvocationCanceledException;
import com.againspring.aiuser.llm.pool.CancelableInvocation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless Codex CLI bridge.  It deliberately uses the logged-in CLI session,
 * never an OpenAI API key. A process exists only while a request is executing.
 */
@Slf4j
@Service
public class CodexCliInvoker implements Invoker {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${llm.worker.codex-binary-path:codex}")
    private String codexBinaryPath;

    private final StructuredSchemaCatalog schemaCatalog;

    public CodexCliInvoker(StructuredSchemaCatalog schemaCatalog) {
        this.schemaCatalog = schemaCatalog;
    }

    @Override
    public String invoke(String prompt, String model) throws ClaudeCodeException {
        return invokeOnce(prompt, model, null);
    }

    @Override
    public String invokeSingleAttempt(String prompt, String model, StructuredOutputSchema schema) throws ClaudeCodeException {
        return invokeOnce(prompt, model, schema);
    }

    private String invokeOnce(String prompt, String model, StructuredOutputSchema schema) throws ClaudeCodeException {
        if (model == null || model.isBlank()) {
            throw new ClaudeCodeException("CODEX_ERROR", "Codex model is required", -1, null);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(buildCommand(codexBinaryPath, model,
                    schema == null ? null : schemaCatalog.codexPath(schema)));
            // Keep CLI subscription/session authentication authoritative.
            pb.environment().remove("OPENAI_API_KEY");
            pb.environment().remove("CODEX_API_KEY");
            Process process = pb.start();
            drainStderr(process);
            try (var stdin = process.getOutputStream()) {
                stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }
            String result = readJsonEvents(process);
            int exit = process.waitFor();
            if (exit != 0) {
                throw new ClaudeCodeException("CODEX_ERROR", "Codex CLI exited with code " + exit, exit, null);
            }
            if (LlmErrorSignature.looksLikeProviderError(result)) {
                throw new ClaudeCodeException("PROVIDER_ERROR", "Provider error text in Codex CLI output", exit, null);
            }
            if (result.isBlank()) {
                throw new ClaudeCodeException("CODEX_ERROR", "Codex CLI returned no final text", exit, null);
            }
            return result;
        } catch (ClaudeCodeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error running Codex CLI: {}", e.getMessage(), e);
            throw new ClaudeCodeException("CODEX_ERROR", e.getMessage(), -1, null);
        }
    }

    static List<String> buildCommand(String binary, String model, String outputSchemaPath) {
        List<String> command = new ArrayList<>(List.of(binary, "exec", "--ephemeral", "--skip-git-repo-check",
                "--json", "--sandbox", "read-only", "--model", model));
        if (outputSchemaPath != null && !outputSchemaPath.isBlank()) {
            command.add("--output-schema");
            command.add(outputSchemaPath);
        }
        command.add("-");
        return command;
    }

    /** Legacy async endpoint does not route to Codex today; keep interface compatibility without reusing sessions. */
    @Override
    public String invokeWithCancelSupport(String prompt, String model, CancelableInvocation inv) throws Exception {
        if (inv.isCanceled()) throw new InvocationCanceledException("Canceled before Codex invocation", inv.getInvocationId());
        String result = invoke(prompt, model);
        if (inv.isCanceled()) throw new InvocationCanceledException("Canceled during Codex invocation", inv.getInvocationId());
        return result;
    }

    static String extractFinalText(List<String> eventLines) throws Exception {
        StringBuilder result = new StringBuilder();
        for (String line : eventLines) {
            if (line == null || line.isBlank()) continue;
            JsonNode node;
            try {
                node = MAPPER.readTree(line);
            } catch (Exception ignored) {
                continue; // Codex may emit non-event diagnostics despite --json.
            }
            // Codex exec's durable final payload is item.completed with
            // item.type=agent_message. Do not concatenate progress/tool events.
            if (!"item.completed".equals(node.path("type").asText(""))) continue;
            JsonNode item = node.path("item");
            if (!"agent_message".equals(item.path("type").asText(""))) continue;
            String text = item.path("text").asText("").trim();
            if (!text.isBlank()) result.append(text);
        }
        return result.toString().trim();
    }

    private String readJsonEvents(Process process) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        return extractFinalText(lines);
    }

    private void drainStderr(Process process) {
        Thread t = new Thread(() -> {
            try (OutputStream sink = OutputStream.nullOutputStream()) {
                process.getErrorStream().transferTo(sink);
            } catch (Exception ignored) { }
        }, "codex-stderr-drain");
        t.setDaemon(true);
        t.start();
    }
}
