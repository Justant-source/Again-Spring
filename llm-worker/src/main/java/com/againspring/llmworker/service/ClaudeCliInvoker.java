package com.againspring.llmworker.service;

import com.againspring.llmworker.exception.ClaudeCodeException;
import com.againspring.llmworker.exception.InvocationCanceledException;
import com.againspring.llmworker.pool.CancelableInvocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Claude CLI 프로세스를 실제 spawn하는 서비스.
 * --strict-mcp-config, --no-session-persistence 플래그로 콜드스타트 최적화.
 * 주의: --bare는 OAuth 인증을 비활성화하므로 구독 기반 ~/.claude 세션과 호환 불가 → 사용 금지.
 */
@Slf4j
@Service
public class ClaudeCliInvoker {

    // Claude Code CLI가 SW 엔지니어링 모드로 동작하지 않도록 캐릭터를 다시봄 중재자로 설정
    private static final String DASIBOM_SYSTEM_ROLE =
            "당신은 '다시봄' 감정 정리 도우미입니다. 사용자가 보내는 텍스트에 한국어로만 응답합니다. " +
            "소프트웨어 개발 도움이 아닌, 사람 간의 감정과 관계를 다루는 대화를 합니다. " +
            "아래 지시에 따라 즉시 응답을 시작하세요.";

    @Value("${llm.worker.claude-binary-path:claude}")
    private String claudeBinaryPath;

    /**
     * 동기 invoke (취소 불가). sync POST /v1/invoke 경로.
     */
    public String invoke(String prompt, String model) throws ClaudeCodeException {
        ProcessBuilder pb = buildProcessBuilder(prompt, model);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String stderrExcerpt = stderr.length() > 500 ? stderr.substring(0, 500) : stderr;
                log.warn("Claude CLI exited with code {}: {}", exitCode, stderrExcerpt);
                throw new ClaudeCodeException("CLAUDE_ERROR",
                        "Claude CLI exited with code " + exitCode, exitCode, stderrExcerpt);
            }
            return stdout.trim();
        } catch (ClaudeCodeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error running claude CLI: {}", e.getMessage(), e);
            throw new ClaudeCodeException("UNKNOWN_ERROR", e.getMessage(), -1,
                    e.getMessage() != null && e.getMessage().length() > 500
                            ? e.getMessage().substring(0, 500) : e.getMessage());
        }
    }

    /**
     * 취소 가능한 invoke. CancelableInvocation에 Process를 attach해 cancel()로 kill 가능.
     */
    public String invokeWithCancelSupport(String prompt, String model, CancelableInvocation inv)
            throws Exception {
        ProcessBuilder pb = buildProcessBuilder(prompt, model);
        pb.redirectErrorStream(false);

        Process process = pb.start();
        inv.attachProcess(process);  // 이후 외부에서 cancel() → destroyForcibly() 가능

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        // canceled 플래그 우선 체크 (destroyForcibly 후 exitCode 비정상일 수 있음)
        if (inv.isCanceled()) {
            throw new InvocationCanceledException("Canceled mid-flight", inv.getInvocationId());
        }

        if (exitCode != 0) {
            String stderrExcerpt = stderr.length() > 500 ? stderr.substring(0, 500) : stderr;
            log.warn("Claude CLI exited with code {}: {}", exitCode, stderrExcerpt);
            throw new ClaudeCodeException("CLAUDE_ERROR",
                    "Claude CLI exited with code " + exitCode, exitCode, stderrExcerpt);
        }
        return stdout.trim();
    }

    private ProcessBuilder buildProcessBuilder(String prompt, String model) {
        // <conversation_history> 태그 기준으로 system-prompt와 user 파트 분리
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

        // --strict-mcp-config: 기존 ~/.claude MCP 설정 무시 (MCP 초기화 오버헤드 제거)
        // --no-session-persistence: 세션 저장 불필요 (stateless one-shot), 디스크 I/O 감소
        return new ProcessBuilder(
                claudeBinaryPath,
                "--print",
                "--model", model,
                "--strict-mcp-config",
                "--no-session-persistence",
                "--system-prompt", systemPart,
                userPart
        );
    }
}
