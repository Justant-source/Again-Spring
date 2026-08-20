package com.againspring.llmworker.service;

import com.againspring.llmworker.exception.ClaudeCodeException;
import com.againspring.llmworker.exception.InvocationCanceledException;
import com.againspring.llmworker.pool.CancelableInvocation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Claude CLI 프로세스를 spawn하는 서비스.
 * --output-format stream-json: NDJSON 스트리밍으로 첫 토큰부터 점진적 수신.
 * --strict-mcp-config: ~/.claude MCP 설정 무시 (초기화 오버헤드 제거).
 * --no-session-persistence: 세션 저장 스킵 (stateless one-shot).
 * 주의: --bare 금지 — OAuth 파괴.
 * stderr는 별도 스레드로 drain — 파이프 버퍼 데드락 방지.
 */
@Slf4j
@Service
public class ClaudeCliInvoker {

    private static final String DASIBOM_SYSTEM_ROLE =
            "당신은 '다시봄' 감정 정리 도우미입니다. 사용자가 보내는 텍스트에 한국어로만 응답합니다. " +
            "소프트웨어 개발 도움이 아닌, 사람 간의 감정과 관계를 다루는 대화를 합니다. " +
            "아래 지시에 따라 즉시 응답을 시작하세요.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String claudeBinaryPath;
    private final LlmConfigService llmConfigService;
    private final ProcessTerminator processTerminator;

    public ClaudeCliInvoker(
            @Value("${llm.worker.claude-binary-path:claude}") String claudeBinaryPath,
            LlmConfigService llmConfigService,
            ProcessTerminator processTerminator) {
        this.claudeBinaryPath = claudeBinaryPath;
        this.llmConfigService = llmConfigService;
        this.processTerminator = processTerminator;
    }

    /**
     * 동기 invoke (취소 불가). sync POST /v1/invoke 경로.
     * 스트리밍으로 실행하되 최종 결과만 반환.
     */
    public String invoke(String prompt, String model) throws ClaudeCodeException {
        String corrId = UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();
        ProcessBuilder pb = buildProcessBuilder(prompt, model);
        try {
            Process process = pb.start();
            try {
                processTerminator.register(process);
                drainStderr(process, "sync");
                StreamResult result = readStreamingOutput(process, null, corrId, model, 1, startMs);
                int exitCode = process.waitFor();
                if (exitCode != 0 && !result.text.isBlank()) {
                    // 내용이 있으면 성공으로 처리 (일부 CLI 버전 비정상 exit code 방어)
                    return result.text;
                }
                if (exitCode != 0) {
                    long duration = System.currentTimeMillis() - startMs;
                    logLlmStats(model, 1, "CLAUDE_ERROR", 0, 0, 0, 0, 0, "FAIL", duration, corrId);
                    throw new ClaudeCodeException("CLAUDE_ERROR",
                            "Claude CLI exited with code " + exitCode, exitCode, null);
                }
                return result.text;
            } finally {
                processTerminator.release(process);
            }
        } catch (ClaudeCodeException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startMs;
            logLlmStats(model, 1, "UNKNOWN_ERROR", 0, 0, 0, 0, 0, "FAIL", duration, corrId);
            log.error("Unexpected error running claude CLI: {}", e.getMessage(), e);
            throw new ClaudeCodeException("UNKNOWN_ERROR", e.getMessage(), -1, null);
        }
    }

    /**
     * 취소 가능한 invoke. 스트리밍 중 inv.updatePartial(cumulative) 콜백.
     */
    public String invokeWithCancelSupport(String prompt, String model, CancelableInvocation inv)
            throws Exception {
        String corrId = UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();
        ProcessBuilder pb = buildProcessBuilder(prompt, model);
        Process process = pb.start();
        try {
            processTerminator.register(process);
            inv.attachProcess(process);
            drainStderr(process, inv.getInvocationId());

            StreamResult result = readStreamingOutput(process, inv, corrId, model, 1, startMs);
            int exitCode = process.waitFor();

            if (inv.isCanceled()) {
                throw inv.terminationException();
            }
            if (exitCode != 0 && !result.text.isBlank()) return result.text;
            if (exitCode != 0) {
                long duration = System.currentTimeMillis() - startMs;
                logLlmStats(model, 1, "CLAUDE_ERROR", 0, 0, 0, 0, 0, "FAIL", duration, corrId);
                throw new ClaudeCodeException("CLAUDE_ERROR",
                        "Claude CLI exited with code " + exitCode, exitCode, null);
            }
            return result.text;
        } finally {
            processTerminator.release(process);
        }
    }

    // ── 내부 메서드 ──────────────────────────────────────────────────────────────

    /**
     * stdout을 NDJSON 라인 단위로 읽으며 Claude CLI stream-json 이벤트 파싱.
     *
     * --include-partial-messages 시:
     *   {"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":"..."}}}
     *   → 토큰 단위 누적, updatePartial 호출
     *
     * 최종 이벤트 (공통):
     *   {"type":"result","result":"최종 전체 텍스트","usage":{...}}
     *
     * @param corrId correlation ID for logging
     * @param model model name for logging
     * @param attempt attempt number for logging
     * @param startMs start time in ms for duration calculation
     */
    private StreamResult readStreamingOutput(Process process, CancelableInvocation inv, String corrId, String model, int attempt, long startMs) throws Exception {
        StringBuilder accumulated = new StringBuilder();
        String finalResult = "";
        int inputTokens = 0;
        int outputTokens = 0;
        int cacheReadTokens = 0;
        int cacheWriteTokens = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    JsonNode node = MAPPER.readTree(line);
                    String type = node.path("type").asText("");
                    if ("stream_event".equals(type)) {
                        // --include-partial-messages: 내부 event 필드에 실제 Anthropic SSE 이벤트 포함
                        JsonNode event = node.path("event");
                        if ("content_block_delta".equals(event.path("type").asText(""))) {
                            JsonNode delta = event.path("delta");
                            if ("text_delta".equals(delta.path("type").asText(""))) {
                                String chunk = delta.path("text").asText("");
                                if (!chunk.isEmpty()) {
                                    accumulated.append(chunk);
                                    if (inv != null) inv.updatePartial(accumulated.toString());
                                }
                            }
                        }
                    } else if ("result".equals(type)) {
                        String r = node.path("result").asText("");
                        if (!r.isBlank()) finalResult = r;
                        // Extract usage data from result event
                        JsonNode usage = node.path("usage");
                        if (usage != null && !usage.isMissingNode()) {
                            inputTokens = usage.path("input_tokens").asInt(0);
                            outputTokens = usage.path("output_tokens").asInt(0);
                            cacheReadTokens = usage.path("cache_read_input_tokens").asInt(0);
                            cacheWriteTokens = usage.path("cache_creation_input_tokens").asInt(0);
                        }
                    }
                } catch (Exception ignored) {
                    // 파싱 불가 라인 무시
                }
            }
        }
        // result 이벤트 우선 (깔끔한 최종 텍스트), 없으면 누적 partial 사용
        String answer = (finalResult.isBlank() ? accumulated.toString() : finalResult).trim();
        // Log success
        long duration = System.currentTimeMillis() - startMs;
        int cacheHitPercent = inputTokens + cacheReadTokens + cacheWriteTokens > 0
            ? (int) Math.round(cacheReadTokens * 100.0 / (inputTokens + cacheReadTokens + cacheWriteTokens))
            : 0;
        logLlmStats(model, attempt, null, inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, cacheHitPercent, "OK", duration, corrId);
        return new StreamResult(answer, inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens);
    }

    /** stderr를 데몬 스레드로 drain — 파이프 버퍼 포화(데드락) 방지 */
    private void drainStderr(Process process, String invId) {
        Thread t = new Thread(() -> {
            try (OutputStream sink = OutputStream.nullOutputStream()) {
                process.getErrorStream().transferTo(sink);
            } catch (Exception ignored) {}
        }, "stderr-drain-" + invId);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Result + usage data extracted from stream-json.
     * Used internally to pass both the output text and token metrics through the pipeline.
     */
    private static class StreamResult {
        final String text;
        final int inputTokens;
        final int outputTokens;
        final int cacheReadTokens;
        final int cacheWriteTokens;

        StreamResult(String text, int inputTokens, int outputTokens, int cacheReadTokens, int cacheWriteTokens) {
            this.text = text;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.cacheReadTokens = cacheReadTokens;
            this.cacheWriteTokens = cacheWriteTokens;
        }
    }

    /**
     * Log [LLMSTATS] format for metrics collection.
     * Format: [LLMSTATS] ts=... sys=AS type=... model=... attempt=... retryReason=... in=... out=... cache_read=... cache_write=... cache_hit=...% result=... duration_ms=... corrId=...
     */
    private void logLlmStats(String model, int attempt, String retryReason, int inTokens, int outTokens,
                            int cacheReadTokens, int cacheWriteTokens, int cacheHitPercent,
                            String result, long durationMs, String corrId) {
        String stats = new LlmStatsLogger("INVOKE", model, corrId)
            .attempt(attempt)
            .retryReason(retryReason)
            .tokens(inTokens, outTokens)
            .cacheTokens(cacheReadTokens, cacheWriteTokens)
            .cacheHitPercent(cacheHitPercent)
            .result(result)
            .duration(durationMs)
            .build();
        log.info(stats);
    }

    private ProcessBuilder buildProcessBuilder(String prompt, String model) {
        int splitIdx = prompt.indexOf("<conversation_history>");
        String systemPart;
        String userPart;
        if (splitIdx > 0) {
            systemPart = prompt.substring(0, splitIdx).trim();
            userPart = prompt.substring(splitIdx).trim();
        } else {
            systemPart = DASIBOM_SYSTEM_ROLE;
            userPart = prompt;
        }

        var command = buildCommand(claudeBinaryPath, model, systemPart);
        command.add(userPart);

        ProcessBuilder pb = new ProcessBuilder(command);
        String baseUrl = llmConfigService.getAnthropicBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            pb.environment().put("ANTHROPIC_BASE_URL", baseUrl);
        }
        return pb;
    }

    /**
     * Builds the claude CLI command arguments.
     * Static for testability.
     *
     * @param binary path to claude binary
     * @param model model name
     * @param systemPart system prompt
     * @return list of command arguments (excluding userPart, which should be appended by caller)
     */
    static java.util.List<String> buildCommand(String binary, String model, String systemPart) {
        var command = new java.util.ArrayList<String>(java.util.List.of(
                binary, "--print", "--output-format", "stream-json", "--verbose",
                "--include-partial-messages", "--model", model, "--strict-mcp-config",
                "--no-session-persistence"));

        // Token reduction: disallow all CLI tools to reduce prompt overhead.
        // Measured empirically: ~25,267 input tokens without this flag,
        // drops to ~279 tokens with --disallowedTools "*".
        // This worker never uses structured output (--json-schema), so "*" is safe.
        command.add("--disallowedTools");
        command.add("*");

        command.add("--system-prompt");
        command.add(systemPart);
        return command;
    }
}
