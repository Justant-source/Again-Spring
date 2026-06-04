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
 * 변경: <<<USER_PROMPT>>> 구분자 사용 (다시봄 배심원과 구별)
 */
@Slf4j
@Service
public class ClaudeCliInvoker {

    private static final String USER_PROMPT_SEP = "<<<USER_PROMPT>>>";
    private static final String DEFAULT_SYSTEM =
        "당신은 한국 갈등 커뮤니티 '다시봄'의 일반 사용자입니다. 지시에 따라 자연스러운 한국 커뮤니티 텍스트를 생성합니다.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${llm.worker.claude-binary-path:claude}")
    private String claudeBinaryPath;

    /**
     * 동기 invoke (취소 불가). sync POST /generate endpoints 경로.
     * 스트리밍으로 실행하되 최종 결과만 반환.
     */
    public String invoke(String prompt, String model) throws ClaudeCodeException {
        ProcessBuilder pb = buildProcessBuilder(prompt, model);
        try {
            Process process = pb.start();
            drainStderr(process, "sync");
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

    /**
     * 취소 가능한 invoke. 스트리밍 중 inv.updatePartial(cumulative) 콜백.
     */
    public String invokeWithCancelSupport(String prompt, String model, CancelableInvocation inv)
            throws Exception {
        ProcessBuilder pb = buildProcessBuilder(prompt, model);
        Process process = pb.start();
        inv.attachProcess(process);
        drainStderr(process, inv.getInvocationId());

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

    // ── 내부 메서드 ──────────────────────────────────────────────────────────────

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
                        String r = node.path("result").asText("");
                        if (!r.isBlank()) finalResult = r;
                    }
                } catch (Exception ignored) {
                    // 파싱 불가 라인 무시
                }
            }
        }
        // result 이벤트 우선 (깔끔한 최종 텍스트), 없으면 누적 partial 사용
        String answer = finalResult.isBlank() ? accumulated.toString() : finalResult;
        return answer.trim();
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

    private ProcessBuilder buildProcessBuilder(String prompt, String model) {
        int sepIdx = prompt.indexOf(USER_PROMPT_SEP);
        String systemPart;
        String userPart;
        if (sepIdx >= 0) {
            systemPart = prompt.substring(0, sepIdx).trim();
            userPart = prompt.substring(sepIdx + USER_PROMPT_SEP.length()).trim();
        } else {
            systemPart = DEFAULT_SYSTEM;
            userPart = prompt;
        }

        return new ProcessBuilder(
                claudeBinaryPath,
                "--print",
                "--output-format", "stream-json",
                "--verbose",
                "--include-partial-messages",
                "--model", model,
                "--strict-mcp-config",
                "--no-session-persistence",
                "--system-prompt", systemPart,
                userPart
        );
    }
}
