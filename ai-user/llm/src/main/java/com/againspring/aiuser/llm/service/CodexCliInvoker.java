package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.exception.ClaudeCodeException;
import com.againspring.aiuser.llm.exception.InvocationCanceledException;
import com.againspring.aiuser.llm.pool.CancelableInvocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Codex CLI bridge.
 * Anthropic/clcocloud API 경로는 런타임에서 사용하지 않는다.
 */
@Slf4j
@Service
public class CodexCliInvoker implements Invoker {

    private static final String USER_PROMPT_SEP = "<<<USER_PROMPT>>>";
    private static final String DEFAULT_SYSTEM =
        "아래 지시에 따라 자연스러운 한국 갈등 커뮤니티 텍스트를 창작합니다.";
    private static final String DEFAULT_WORKDIR = "/tmp";

    @Value("${llm.worker.codex-binary-path:codex}")
    private String codexBinaryPath;

    @Value("${llm.worker.codex-model:gpt-5.4}")
    private String defaultCodexModel;

    public String invoke(String prompt, String model) throws ClaudeCodeException {
        ProcessBuilder pb = null;
        Path outputFile = null;
        try {
            outputFile = Files.createTempFile("againspring-codex-", ".txt");
            pb = buildProcessBuilder(prompt, model, outputFile);
            Process process = pb.start();
            drainStream(process.getInputStream(), "stdout-sync");
            drainStream(process.getErrorStream(), "stderr-sync");
            int exitCode = process.waitFor();
            String result = readOutputFile(outputFile);
            if (!result.isBlank()) {
                validateOutput(result);
                return result;
            }
            throw new ClaudeCodeException("CODEX_ERROR",
                    "Codex CLI exited with code " + exitCode, exitCode, null);
        } catch (ClaudeCodeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error running codex CLI: {}", e.getMessage(), e);
            throw new ClaudeCodeException("UNKNOWN_ERROR", e.getMessage(), -1, null);
        } finally {
            cleanup(outputFile);
        }
    }

    public String invokeWithCancelSupport(String prompt, String model, CancelableInvocation inv)
            throws Exception {
        Path outputFile = Files.createTempFile("againspring-codex-", ".txt");
        ProcessBuilder pb = buildProcessBuilder(prompt, model, outputFile);
        Process process = pb.start();
        inv.attachProcess(process);
        drainStream(process.getInputStream(), "stdout-" + inv.getInvocationId());
        drainStream(process.getErrorStream(), "stderr-" + inv.getInvocationId());

        int exitCode = process.waitFor();
        if (inv.isCanceled()) {
            cleanup(outputFile);
            throw new InvocationCanceledException("Canceled mid-flight", inv.getInvocationId());
        }

        String result = readOutputFile(outputFile);
        cleanup(outputFile);
        if (!result.isBlank()) {
            validateOutput(result);
            return result;
        }
        throw new ClaudeCodeException("CODEX_ERROR",
                "Codex CLI exited with code " + exitCode, exitCode, null);
    }

    private void validateOutput(String answer) throws ClaudeCodeException {
        if (LlmErrorSignature.looksLikeProviderError(answer)) {
            log.error("Codex output looks like a provider error — refusing to return as content: {}", truncate(answer));
            throw new ClaudeCodeException("PROVIDER_ERROR", "Provider error text in Codex output", -1, null);
        }
    }

    private String readOutputFile(Path outputFile) throws Exception {
        if (outputFile == null || !Files.exists(outputFile)) {
            return "";
        }
        return Files.readString(outputFile, StandardCharsets.UTF_8).trim();
    }

    private void cleanup(Path outputFile) {
        if (outputFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(outputFile);
        } catch (Exception ignored) {
        }
    }

    private void drainStream(java.io.InputStream stream, String name) {
        Thread t = new Thread(() -> {
            try (OutputStream sink = OutputStream.nullOutputStream()) {
                stream.transferTo(sink);
            } catch (Exception ignored) {
            }
        }, "codex-drain-" + name);
        t.setDaemon(true);
        t.start();
    }

    private ProcessBuilder buildProcessBuilder(String prompt, String model, Path outputFile) {
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

        String codexPrompt = buildCodexPrompt(systemPart, userPart);
        String resolvedModel = resolveModel(model);

        ProcessBuilder pb = new ProcessBuilder(
                codexBinaryPath,
                "exec",
                "--skip-git-repo-check",
                "--sandbox", "read-only",
                "--cd", DEFAULT_WORKDIR,
                "--color", "never",
                "--output-last-message", outputFile.toString(),
                "--model", resolvedModel,
                codexPrompt
        );
        pb.environment().remove("ANTHROPIC_API_KEY");
        pb.environment().remove("ANTHROPIC_AUTH_TOKEN");
        pb.environment().remove("ANTHROPIC_BASE_URL");
        return pb;
    }

    private String resolveModel(String requestedModel) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return defaultCodexModel;
        }
        String trimmed = requestedModel.trim();
        if (trimmed.startsWith("claude-")) {
            return defaultCodexModel;
        }
        return trimmed;
    }

    private String buildCodexPrompt(String systemPart, String userPart) {
        return """
            [System Instructions]
            %s

            [User Request]
            %s

            중요:
            - 요청된 결과 본문만 출력한다.
            - 설명, 메타 코멘트, 코드블록, 따옴표 래핑을 추가하지 않는다.
            - 한국어 원문 톤을 유지한다.
            """.formatted(systemPart, userPart).trim();
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
