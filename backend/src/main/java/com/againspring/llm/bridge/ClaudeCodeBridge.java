package com.againspring.llm.bridge;

import com.againspring.llm.*;
import com.againspring.llm.bridge.exception.ClaudeCodeException;
import com.againspring.llm.monitoring.LLMCallLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * LLM Provider implementation using Claude Code CLI.
 * Invokes `claude -p <prompt>` via ProcessBuilder, reads stdout, parses JSON response.
 * Delegates to ClaudeCodeWorkerPool for concurrency management and timeout enforcement.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "claude-code", matchIfMissing = true)
public class ClaudeCodeBridge implements LLMProvider {

    private final ClaudeCodeWorkerPool workerPool;
    private final PromptSanitizer sanitizer;
    private final LLMCallLogger callLogger;
    private final ObjectMapper objectMapper;

    @Value("${llm.claude-code.binary-path:/usr/local/bin/claude}")
    private String claudeBinaryPath;

    @Value("${llm.claude-code.default-timeout-ms:30000}")
    private long defaultTimeoutMs;

    public ClaudeCodeBridge(ClaudeCodeWorkerPool workerPool, PromptSanitizer sanitizer,
                           LLMCallLogger callLogger) {
        this.workerPool = workerPool;
        this.sanitizer = sanitizer;
        this.callLogger = callLogger;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public LLMResponse invoke(LLMRequest request) throws LLMException {
        String correlationId = request.getCorrelationId() != null
                ? request.getCorrelationId()
                : UUID.randomUUID().toString();

        Instant startTime = Instant.now();
        try {
            // Sanitize user input
            String safeUserInput = sanitizer.sanitize(request.getUserInput(), correlationId);

            // Assemble final prompt (system + layers + user input)
            String finalPrompt = assembleFinalPrompt(request.getSystemPrompt(),
                    request.getLayers(), safeUserInput);

            // Execute via worker pool
            java.time.Duration timeout = request.getTimeout() != null
                    ? request.getTimeout()
                    : java.time.Duration.ofMillis(defaultTimeoutMs);

            String rawOutput = workerPool.execute(
                    () -> runClaudeCommand(finalPrompt),
                    timeout,
                    correlationId);

            long latencyMs = java.time.Instant.now().toEpochMilli() - startTime.toEpochMilli();

            LLMResponse response = LLMResponse.builder()
                    .rawText(rawOutput)
                    .tokensUsed(estimateTokens(rawOutput))
                    .latencyMs(latencyMs)
                    .provider(getProviderName())
                    .correlationId(correlationId)
                    .isFallback(false)
                    .build();

            callLogger.logCall(request, response, null);
            return response;

        } catch (LLMException e) {
            callLogger.logCall(request, null, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in ClaudeCodeBridge: {}", e.getMessage(), e);
            callLogger.logCall(request, null, e);
            throw new ClaudeCodeException("UNKNOWN_ERROR", e.getMessage(), correlationId, -1,
                    e.getMessage().length() > 500 ? e.getMessage().substring(0, 500) : e.getMessage());
        }
    }

    @Override
    public CompletableFuture<LLMResponse> invokeAsync(LLMRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return invoke(request);
            } catch (LLMException e) {
                throw new CompletionException(e);
            }
        });
    }

    @Override
    public String getProviderName() {
        return "claude-code";
    }

    @Override
    public boolean isHealthy() {
        try {
            ProcessBuilder pb = new ProcessBuilder(claudeBinaryPath, "--version");
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.warn("Claude Code health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Assemble final prompt from system prompt, layers, and user input.
     */
    private String assembleFinalPrompt(String systemPrompt, java.util.List<PromptLayer> layers,
                                       String userInput) {
        StringBuilder sb = new StringBuilder();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append("<system>\n").append(systemPrompt).append("\n</system>\n\n");
        }

        if (layers != null && !layers.isEmpty()) {
            sb.append("<context>\n");
            layers.stream()
                    .sorted((a, b) -> Integer.compare(a.order(), b.order()))
                    .forEach(layer -> sb.append(layer.content()).append("\n"));
            sb.append("</context>\n\n");
        }

        if (userInput != null && !userInput.isBlank()) {
            sb.append("<user_input>\n").append(userInput).append("\n</user_input>\n");
        }

        return sb.toString();
    }

    /**
     * Run Claude Code CLI subprocess and read output.
     */
    private String runClaudeCommand(String prompt) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                claudeBinaryPath, "-p",
                "--output-format", "json"
        );
        pb.redirectErrorStream(false);

        Process process = pb.start();

        // Write prompt to stdin
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }

        // Read stdout
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // Read stderr
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

        // Wait for completion
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            String stderrExcerpt = stderr.length() > 500 ? stderr.substring(0, 500) : stderr;
            throw new ClaudeCodeException("CLAUDE_ERROR", "Claude CLI exited with code " + exitCode,
                    null, exitCode, stderrExcerpt);
        }

        // Parse JSON output (expect { "content": "..." } or similar)
        try {
            JsonNode jsonNode = objectMapper.readTree(stdout);
            if (jsonNode.has("content")) {
                return jsonNode.get("content").asText();
            } else if (jsonNode.has("text")) {
                return jsonNode.get("text").asText();
            }
            return stdout;  // Fallback to raw stdout
        } catch (Exception e) {
            // If JSON parsing fails, return raw stdout
            log.warn("Failed to parse Claude output as JSON, returning raw: {}", e.getMessage());
            return stdout;
        }
    }

    /**
     * Rough token estimate (1 token ≈ 4 chars, or ~1 token per word).
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Conservative estimate: divide by 4 (Claude typical)
        return Math.max(1, text.length() / 4);
    }
}
