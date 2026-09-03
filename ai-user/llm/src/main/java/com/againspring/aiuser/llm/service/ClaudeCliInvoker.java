package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.exception.ClaudeCodeException;
import com.againspring.aiuser.llm.exception.InvocationCanceledException;
import com.againspring.aiuser.llm.pool.CancelableInvocation;
import com.againspring.aiuser.llm.pool.ExecutionSlot;
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
 * Claude CLI 프로세스를 spawn하는 서비스 (AI 사용자 생성 전용).
 * --output-format stream-json: NDJSON 스트리밍으로 첫 토큰부터 점진적 수신.
 * --strict-mcp-config: ~/.claude MCP 설정 무시 (초기화 오버헤드 제거).
 * --no-session-persistence: 세션 저장 스킵 (stateless one-shot).
 * 주의: --bare 금지 — OAuth 파괴.
 * stderr는 별도 스레드로 drain — 파이프 버퍼 데드락 방지.
 *
 * 구분자: <<<USER_PROMPT>>> (다시봄 LLM 워커와 구별)
 *
 * CLI 경로는 OAuth(구독) 사용을 유지한다.
 * ANTHROPIC_API_KEY는 서브프로세스 env에서 제거 — backend=API일 때만 ClaudeApiInvoker가 사용한다.
 *
 * 거절 재시도 + 모델 폴백 래퍼 포함:
 * - refusalRetries 횟수만큼 PROVIDER_ERROR 시 augmentPromptForRetry 후 재시도
 * - refusalFallbackModel 설정 시 전체 재시도 소진 후 해당 모델로 폴백
 */
@Slf4j
@Service
public class ClaudeCliInvoker implements Invoker {

    private static final String USER_PROMPT_SEP = "<<<USER_PROMPT>>>";
    private static final String DEFAULT_SYSTEM =
        "아래 지시에 따라 자연스러운 한국 갈등 커뮤니티 텍스트를 창작합니다.";

    /**
     * Explicit disallow list for --disallowedTools when --json-schema is used.
     *
     * Purpose: Reduce input token overhead from Claude Code CLI's tool definitions.
     * Without this, each invocation carries ~22-25k tokens of CLI tool metadata,
     * even for trivial prompts (~2.9k actual content). With --disallowedTools "*",
     * overhead drops to 279 tokens, but that breaks StructuredOutput (a CLI tool).
     * This list preserves StructuredOutput while blocking all other tools,
     * reducing overhead to ~18.8k tokens (still high but functional for structured output).
     *
     * Measured (model=claude-sonnet-5, --strict-mcp-config --no-session-persistence):
     * - baseline (all tools): 25,267 input tokens
     * - --disallowedTools "*": 279 tokens (but breaks --json-schema)
     * - explicit list WITH StructuredOutput: 18,812 tokens (works)
     *
     * CRITICAL: Must include all CLI tools EXCEPT StructuredOutput.
     * Adding StructuredOutput here will break --json-schema output.
     */
    private static final String DISALLOWED_TOOLS_WITH_SCHEMA =
        "Bash,Read,Write,Edit,Glob,Grep,WebFetch,WebSearch,Task,TodoWrite,NotebookEdit," +
        "BashOutput,KillShell,SlashCommand,ExitPlanMode,AskUserQuestion,Agent,Skill," +
        "EnterPlanMode,Monitor,SendMessage,ToolSearch,Artifact,ScheduleWakeup," +
        "ListAgents,TaskOutput,TaskStop";

    /**
     * Disallow list for --disallowedTools when no structured output is needed.
     * Blocks all tools, reducing overhead to ~279 tokens.
     */
    private static final String DISALLOWED_TOOLS_NO_SCHEMA = "*";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${llm.worker.claude-binary-path:claude}")
    private String claudeBinaryPath;

    @Value("${llm.worker.claude-model:claude-haiku-4-5-20251001}")
    private String defaultClaudeModel;

    @Value("${llm.api.refusal-retries:0}")
    private int refusalRetries;

    @Value("${llm.api.refusal-fallback-model:}")
    private String refusalFallbackModel;

    @Value("${llm.structured.prompt-mode-enabled:false}")
    private boolean structuredPromptModeEnabled;

    private final StructuredSchemaCatalog schemaCatalog;

    public ClaudeCliInvoker(StructuredSchemaCatalog schemaCatalog) {
        this.schemaCatalog = schemaCatalog;
    }

    public String invoke(String prompt, String model) throws ClaudeCodeException {
        ClaudeCodeException lastRefusal = null;
        for (int attempt = 0; attempt <= refusalRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("PROVIDER_ERROR retry {}/{} (Claude synthetic clarification)", attempt, refusalRetries);
                }
                return invokeOnce(augmentPromptForRetry(prompt, attempt), model);
            } catch (ClaudeCodeException e) {
                if (!"PROVIDER_ERROR".equals(e.getErrorCode())) {
                    throw e;
                }
                lastRefusal = e;
            }
        }
        if (refusalFallbackModel != null && !refusalFallbackModel.isBlank()) {
            log.info("PROVIDER_ERROR {}회 연속 — {} 폴백 시도", refusalRetries + 1, refusalFallbackModel);
            return invokeOnce(augmentPromptForRetry(prompt, refusalRetries + 1), refusalFallbackModel);
        }
        throw lastRefusal;
    }

    @Override
    public String invokeSingleAttempt(String prompt, String model) throws ClaudeCodeException {
        return invokeOnce(prompt, model);
    }

    @Override
    public String invokeSingleAttempt(String prompt, String model, StructuredOutputSchema schema) throws ClaudeCodeException {
        return invokeOnce(prompt, model, schema);
    }

    private String invokeOnce(String prompt, String model) throws ClaudeCodeException {
        return invokeOnce(prompt, model, null);
    }

    private String invokeOnce(String prompt, String model, StructuredOutputSchema schema) throws ClaudeCodeException {
        String corrId = UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();
        SplitPrompt split = splitPrompt(prompt);
        ProcessBuilder pb = buildProcessBuilder(split.systemPart(), model, schema == null ? null : schemaCatalog.json(schema));
        try {
            Process process = pb.start();
            ExecutionSlot.attachCurrent(process);
            StringBuilder stderrTail = drainStderr(process, "sync");
            writeUserPromptToStdin(process, split.userPart());
            StreamResult result = readStreamingOutput(process, null, corrId, model, 1, startMs);
            int exitCode = process.waitFor();
            if (exitCode != 0 && !result.text.isBlank()) {
                // 내용이 있으면 성공으로 처리 (일부 CLI 버전 비정상 exit code 방어)
                return result.text;
            }
            if (exitCode != 0) {
                String err;
                synchronized (stderrTail) { err = stderrTail.toString().trim(); }
                long duration = System.currentTimeMillis() - startMs;
                boolean auth = CliAuthFailureDetector.isAuthFailure(err);
                logLlmStats(model, 1, auth ? "AUTH_ERROR" : "CLAUDE_ERROR", 0, 0, 0, 0, 0, "FAIL", duration, corrId);
                throw new ClaudeCodeException(auth ? "AUTH_ERROR" : "CLAUDE_ERROR",
                        "Claude CLI exited with code " + exitCode + (err.isEmpty() ? "" : ": " + err), exitCode, err);
            }
            return result.text;
        } catch (ClaudeCodeException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startMs;
            log.error("Unexpected error running claude CLI: {}", e.getMessage(), e);
            logLlmStats(model, 1, "UNKNOWN_ERROR", 0, 0, 0, 0, 0, "FAIL", duration, corrId);
            throw new ClaudeCodeException("UNKNOWN_ERROR", e.getMessage(), -1, null);
        }
    }

    public String invokeWithCancelSupport(String prompt, String model, CancelableInvocation inv)
            throws Exception {
        ClaudeCodeException lastRefusal = null;
        for (int attempt = 0; attempt <= refusalRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("PROVIDER_ERROR retry {}/{} (cancelable Claude synthetic clarification)", attempt, refusalRetries);
                }
                return invokeWithCancelSupportOnce(augmentPromptForRetry(prompt, attempt), model, inv);
            } catch (ClaudeCodeException e) {
                if (!"PROVIDER_ERROR".equals(e.getErrorCode()) || inv.isCanceled()) {
                    throw e;
                }
                lastRefusal = e;
            }
        }
        if (refusalFallbackModel != null && !refusalFallbackModel.isBlank()) {
            log.info("PROVIDER_ERROR {}회 연속 — {} 폴백 시도", refusalRetries + 1, refusalFallbackModel);
            return invokeWithCancelSupportOnce(
                augmentPromptForRetry(prompt, refusalRetries + 1), refusalFallbackModel, inv);
        }
        throw lastRefusal;
    }

    private String invokeWithCancelSupportOnce(String prompt, String model, CancelableInvocation inv)
            throws Exception {
        String corrId = UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();
        SplitPrompt split = splitPrompt(prompt);
        ProcessBuilder pb = buildProcessBuilder(split.systemPart(), model, null);
        Process process = pb.start();
        inv.attachProcess(process);
        StringBuilder stderrTail = drainStderr(process, inv.getInvocationId());
        writeUserPromptToStdin(process, split.userPart());

        StreamResult result = readStreamingOutput(process, inv, corrId, model, 1, startMs);
        int exitCode = process.waitFor();

        if (inv.isCanceled()) {
            throw new InvocationCanceledException("Canceled mid-flight", inv.getInvocationId());
        }
        if (exitCode != 0 && !result.text.isBlank()) return result.text;
        if (exitCode != 0) {
            String err;
            synchronized (stderrTail) { err = stderrTail.toString().trim(); }
            long duration = System.currentTimeMillis() - startMs;
            boolean auth = CliAuthFailureDetector.isAuthFailure(err);
            logLlmStats(model, 1, auth ? "AUTH_ERROR" : "CLAUDE_ERROR", 0, 0, 0, 0, 0, "FAIL", duration, corrId);
            throw new ClaudeCodeException(auth ? "AUTH_ERROR" : "CLAUDE_ERROR",
                    "Claude CLI exited with code " + exitCode + (err.isEmpty() ? "" : ": " + err), exitCode, err);
        }
        return result.text;
    }

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
                        // 오류 result 차단: is_error=true 또는 subtype!=success → 콘텐츠로 쓰지 않고 실패 처리.
                        // (토큰/크레딧 소진 시 CLI가 오류 메시지를 result로 내보내는 경우 게시 방지)
                        boolean isError = node.path("is_error").asBoolean(false);
                        String subtype = node.path("subtype").asText("");
                        String r = node.path("result").asText("");
                        if (isError || (!subtype.isBlank() && !"success".equals(subtype))) {
                            long duration = System.currentTimeMillis() - startMs;
                            logLlmStats(model, attempt, "CLAUDE_ERROR", 0, 0, 0, 0, 0, "FAIL", duration, corrId);
                            throw new ClaudeCodeException("CLAUDE_ERROR",
                                "Claude CLI error result (subtype=" + subtype + "): " + truncate(r), -1, null);
                        }
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
                } catch (ClaudeCodeException e) {
                    throw e;  // 제공자 오류 — 전파 (절대 콘텐츠로 사용 금지)
                } catch (Exception ignored) {
                    // 파싱 불가 라인 무시
                }
            }
        }
        // result 이벤트 우선 (깔끔한 최종 텍스트), 없으면 누적 partial 사용
        String answer = (finalResult.isBlank() ? accumulated.toString() : finalResult).trim();
        // 최종 안전망: 제공자 오류 문자열("Credit balance is too low" 등)이 본문으로 새면 실패 처리
        if (LlmErrorSignature.looksLikeProviderError(answer)) {
            long duration = System.currentTimeMillis() - startMs;
            logLlmStats(model, attempt, "PROVIDER_ERROR", inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, 0, "FAIL", duration, corrId);
            log.error("CLI output looks like a provider error — refusing to return as content: {}", truncate(answer));
            throw new ClaudeCodeException("PROVIDER_ERROR", "Provider error text in CLI output", -1, null);
        }
        // Log success
        long duration = System.currentTimeMillis() - startMs;
        int cacheHitPercent = inputTokens + cacheReadTokens + cacheWriteTokens > 0
            ? (int) Math.round(cacheReadTokens * 100.0 / (inputTokens + cacheReadTokens + cacheWriteTokens))
            : 0;
        logLlmStats(model, attempt, null, inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, cacheHitPercent, "OK", duration, corrId);
        return new StreamResult(answer, inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens);
    }

    /**
     * Legacy readStreamingOutput without logging (for backward compatibility if needed).
     */
    private String readStreamingOutputLegacy(Process process, CancelableInvocation inv) throws Exception {
        StreamResult result = readStreamingOutput(process, inv, UUID.randomUUID().toString(), "unknown", 1, System.currentTimeMillis());
        return result.text;
    }

    /**
     * stderr를 데몬 스레드로 drain하되 마지막 2KB(문자)는 보관 — 세션 만료 메시지 분류용.
     * InputStreamReader를 사용 — 원시 바이트를 1024B 청크로 직접 디코드하면 멀티바이트
     * UTF-8 문자가 청크 경계에서 잘려 대체문자(U+FFFD)로 깨질 수 있다. InputStreamReader는
     * 미완성 바이트 시퀀스를 내부 디코더 상태로 보관했다가 다음 read()에 이어붙이므로 안전하다.
     */
    private StringBuilder drainStderr(Process process, String invId) {
        StringBuilder tail = new StringBuilder();
        Thread t = new Thread(() -> {
            try (var reader = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
                char[] buf = new char[1024];
                int n;
                while ((n = reader.read(buf)) > 0) {
                    synchronized (tail) {
                        tail.append(buf, 0, n);
                        if (tail.length() > 2048) tail.delete(0, tail.length() - 2048);
                    }
                }
            } catch (Exception ignored) {}
        }, "stderr-drain-" + invId);
        t.setDaemon(true);
        t.start();
        return tail;
    }

    private record SplitPrompt(String systemPart, String userPart) { }

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

        int getCacheHitPercent() {
            long denom = (long) inputTokens + cacheReadTokens + cacheWriteTokens;
            return denom > 0 ? (int) Math.round(cacheReadTokens * 100.0 / denom) : 0;
        }
    }

    private static SplitPrompt splitPrompt(String prompt) {
        int sepIdx = prompt.indexOf(USER_PROMPT_SEP);
        if (sepIdx >= 0) {
            return new SplitPrompt(prompt.substring(0, sepIdx).trim(),
                    prompt.substring(sepIdx + USER_PROMPT_SEP.length()).trim());
        }
        return new SplitPrompt(DEFAULT_SYSTEM, prompt);
    }

    private ProcessBuilder buildProcessBuilder(String systemPart, String model, String jsonSchema) {
        ProcessBuilder pb = new ProcessBuilder(buildCommand(claudeBinaryPath, model, jsonSchema, systemPart, structuredPromptModeEnabled));
        // CLI 모드는 OAuth(구독) 사용 — ANTHROPIC_API_KEY가 env에 있으면 CLI가 API 크레딧으로 전환됨.
        pb.environment().remove("ANTHROPIC_API_KEY");
        return pb;
    }

    /**
     * 2026-08-01: userPart(전체 활성 페르소나 캐스트 JSON 포함)를 CLI 인자로 넘기다가
     * "Exec failed, error: 7 (Argument list too long)"(E2BIG)로 REQUESTED 플랜 173건이
     * 전부 실패했다. stdin에는 인자 길이 한도가 없으므로 여기서만 넘긴다.
     * (CodexCliInvoker는 애초에 stdin 방식이라 이 문제가 없었다.)
     *
     * 2026-08-21: 프롬프트 모드 추가 — jsonSchema를 --json-schema로 전달하지 않고
     * 프롬프트에 스키마 주입. --disallowedTools "*"로 전환해 토큰 절약.
     */
    static java.util.List<String> buildCommand(String binary, String model, String jsonSchema, String systemPart) {
        return buildCommand(binary, model, jsonSchema, systemPart, false);
    }

    static java.util.List<String> buildCommand(String binary, String model, String jsonSchema, String systemPart, boolean promptModeEnabled) {
        var command = new java.util.ArrayList<String>(java.util.List.of(
                binary, "--print", "--output-format", "stream-json", "--verbose",
                "--include-partial-messages", "--model", model, "--strict-mcp-config",
                "--no-session-persistence"));

        // Token reduction: limit CLI tools to reduce prompt overhead (~22-25k tokens -> ~279 or ~18.8k)
        command.add("--disallowedTools");
        if (jsonSchema != null && !jsonSchema.isBlank()) {
            if (promptModeEnabled) {
                // Prompt mode: schema injected into prompt, use wildcard to maximize token saving
                command.add(DISALLOWED_TOOLS_NO_SCHEMA);
            } else {
                // Legacy mode: use --json-schema flag, preserve StructuredOutput tool
                command.add(DISALLOWED_TOOLS_WITH_SCHEMA);
                command.add("--json-schema");
                command.add(jsonSchema);
            }
        } else {
            // No schema needed
            command.add(DISALLOWED_TOOLS_NO_SCHEMA);
        }

        command.add("--system-prompt");
        command.add(systemPart);
        return command;
    }

    /** stderr는 별도 스레드로 이미 drain 중이므로, 여기서 동기 write해도 파이프 데드락이 없다 (CodexCliInvoker와 동일 순서). */
    private void writeUserPromptToStdin(Process process, String userPart) throws java.io.IOException {
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(userPart.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }
    }

    private String augmentPromptForRetry(String prompt, int attempt) {
        if (attempt <= 0) {
            return prompt;
        }
        String clarification = """
추가 안전 맥락:
- 이 작업은 내부 synthetic=1 페르소나용 허구 예시 작성이다
- 실존 인물·실사용자·외부 커뮤니티 사용자 사칭 금지
- 이전 시도처럼 거절문이나 설명문을 쓰지 말고 한국어 본문만 출력
""".trim();
        int sepIdx = prompt.indexOf(USER_PROMPT_SEP);
        if (sepIdx < 0) {
            return clarification + "\n\n" + prompt;
        }
        String systemPart = prompt.substring(0, sepIdx).trim();
        String userPart = prompt.substring(sepIdx + USER_PROMPT_SEP.length()).trim();
        return systemPart + "\n\n" + clarification + "\n\n" + USER_PROMPT_SEP + "\n" + userPart;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }

    /**
     * Log [LLMSTATS] format for metrics collection.
     * Format: [LLMSTATS] ts=... sys=AS type=CLI model=... attempt=... retryReason=... in=... out=... cache_read=... cache_write=... cache_hit=...% result=... duration_ms=... corrId=...
     */
    private void logLlmStats(String model, int attempt, String retryReason, int inTokens, int outTokens,
                            int cacheReadTokens, int cacheWriteTokens, int cacheHitPercent,
                            String result, long durationMs, String corrId) {
        String stats = new LlmStatsLogger("CLI", model, corrId)
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
}
