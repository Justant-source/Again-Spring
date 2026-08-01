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

/**
 * Claude CLI 프로세스를 spawn하는 서비스 (AI 사용자 생성 전용).
 * --output-format stream-json: NDJSON 스트리밍으로 첫 토큰부터 점진적 수신.
 * --strict-mcp-config: ~/.claude MCP 설정 무시 (초기화 오버헤드 제거).
 * --no-session-persistence: 세션 저장 스킵 (stateless one-shot).
 * 주의: --bare 금지 — OAuth 파괴.
 * stderr는 별도 스레드로 drain — 파이프 버퍼 데드락 방지.
 *
 * 구분자: <<<USER_PROMPT>>> (다시봄 배심원 워커와 구별)
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${llm.worker.claude-binary-path:claude}")
    private String claudeBinaryPath;

    @Value("${llm.worker.claude-model:claude-haiku-4-5-20251001}")
    private String defaultClaudeModel;

    @Value("${llm.api.refusal-retries:0}")
    private int refusalRetries;

    @Value("${llm.api.refusal-fallback-model:}")
    private String refusalFallbackModel;

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
        SplitPrompt split = splitPrompt(prompt);
        ProcessBuilder pb = buildProcessBuilder(split.systemPart(), model, schema == null ? null : schemaCatalog.json(schema));
        try {
            Process process = pb.start();
            drainStderr(process, "sync");
            writeUserPromptToStdin(process, split.userPart());
            String result = readStreamingOutput(process, null);
            int exitCode = process.waitFor();
            if (exitCode != 0 && !result.isBlank()) {
                // 내용이 있으면 성공으로 처리 (일부 CLI 버전 비정상 exit code 방어)
                return result;
            }
            if (exitCode != 0) {
                throw new ClaudeCodeException("CLAUDE_ERROR",
                        "Claude CLI exited with code " + exitCode, exitCode, null);
            }
            return result;
        } catch (ClaudeCodeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error running claude CLI: {}", e.getMessage(), e);
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
        SplitPrompt split = splitPrompt(prompt);
        ProcessBuilder pb = buildProcessBuilder(split.systemPart(), model, null);
        Process process = pb.start();
        inv.attachProcess(process);
        drainStderr(process, inv.getInvocationId());
        writeUserPromptToStdin(process, split.userPart());

        String result = readStreamingOutput(process, inv);
        int exitCode = process.waitFor();

        if (inv.isCanceled()) {
            throw new InvocationCanceledException("Canceled mid-flight", inv.getInvocationId());
        }
        if (exitCode != 0 && !result.isBlank()) return result;
        if (exitCode != 0) {
            throw new ClaudeCodeException("CLAUDE_ERROR",
                    "Claude CLI exited with code " + exitCode, exitCode, null);
        }
        return result;
    }

    /**
     * stdout을 NDJSON 라인 단위로 읽으며 Claude CLI stream-json 이벤트 파싱.
     *
     * --include-partial-messages 시:
     *   {"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":"..."}}}
     *   → 토큰 단위 누적, updatePartial 호출
     *
     * 최종 이벤트 (공통):
     *   {"type":"result","result":"최종 전체 텍스트"}
     */
    private String readStreamingOutput(Process process, CancelableInvocation inv) throws Exception {
        StringBuilder accumulated = new StringBuilder();
        String finalResult = "";
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
                            throw new ClaudeCodeException("CLAUDE_ERROR",
                                "Claude CLI error result (subtype=" + subtype + "): " + truncate(r), -1, null);
                        }
                        if (!r.isBlank()) finalResult = r;
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
            log.error("CLI output looks like a provider error — refusing to return as content: {}", truncate(answer));
            throw new ClaudeCodeException("PROVIDER_ERROR", "Provider error text in CLI output", -1, null);
        }
        return answer;
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

    private record SplitPrompt(String systemPart, String userPart) { }

    private static SplitPrompt splitPrompt(String prompt) {
        int sepIdx = prompt.indexOf(USER_PROMPT_SEP);
        if (sepIdx >= 0) {
            return new SplitPrompt(prompt.substring(0, sepIdx).trim(),
                    prompt.substring(sepIdx + USER_PROMPT_SEP.length()).trim());
        }
        return new SplitPrompt(DEFAULT_SYSTEM, prompt);
    }

    private ProcessBuilder buildProcessBuilder(String systemPart, String model, String jsonSchema) {
        ProcessBuilder pb = new ProcessBuilder(buildCommand(claudeBinaryPath, model, jsonSchema, systemPart));
        // CLI 모드는 OAuth(구독) 사용 — ANTHROPIC_API_KEY가 env에 있으면 CLI가 API 크레딧으로 전환됨.
        pb.environment().remove("ANTHROPIC_API_KEY");
        return pb;
    }

    /**
     * 2026-08-01: userPart(전체 활성 페르소나 캐스트 JSON 포함)를 CLI 인자로 넘기다가
     * "Exec failed, error: 7 (Argument list too long)"(E2BIG)로 REQUESTED 플랜 173건이
     * 전부 실패했다. stdin에는 인자 길이 한도가 없으므로 여기서만 넘긴다.
     * (CodexCliInvoker는 애초에 stdin 방식이라 이 문제가 없었다.)
     */
    static java.util.List<String> buildCommand(String binary, String model, String jsonSchema, String systemPart) {
        var command = new java.util.ArrayList<String>(java.util.List.of(
                binary, "--print", "--output-format", "stream-json", "--verbose",
                "--include-partial-messages", "--model", model, "--strict-mcp-config",
                "--no-session-persistence"));
        if (jsonSchema != null && !jsonSchema.isBlank()) {
            command.add("--json-schema");
            command.add(jsonSchema);
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
}
