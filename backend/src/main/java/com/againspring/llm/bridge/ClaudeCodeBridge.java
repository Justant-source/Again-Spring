package com.againspring.llm.bridge;

import com.againspring.llm.*;
import com.againspring.llm.bridge.exception.ClaudeCodeException;
import com.againspring.llm.bridge.exception.InvocationCanceledException;
import com.againspring.llm.bridge.exception.LLMCapacityException;
import com.againspring.llm.monitoring.LLMCallLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * LLM Provider implementation using Claude Code CLI.
 * Invokes `claude --print --model <model> "<prompt>"` via ProcessBuilder, reads stdout.
 * Delegates to ClaudeCodeWorkerPool for concurrency management and timeout enforcement.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "claude-code", matchIfMissing = true)
public class ClaudeCodeBridge implements LLMProvider {

    // Claude Code CLI의 기본 SW엔지니어링 시스템 프롬프트를 교체해 캐릭터 이탈 방지
    private static final String DASIBOM_SYSTEM_ROLE =
            "당신은 '다시봄' 감정 정리 도우미입니다. 사용자가 보내는 텍스트에 한국어로만 응답합니다. " +
            "소프트웨어 개발 도움이 아닌, 사람 간의 감정과 관계를 다루는 대화를 합니다. " +
            "아래 지시에 따라 즉시 응답을 시작하세요.";

    private final ClaudeCodeWorkerPool workerPool;
    private final PromptSanitizer sanitizer;
    private final LLMCallLogger callLogger;

    @Value("${llm.claude-code.binary-path:claude}")
    private String claudeBinaryPath;

    @Value("${llm.claude-code.model:claude-haiku-4-5-20251001}")
    private String claudeModel;

    @Value("${llm.claude-code.default-timeout-ms:60000}")
    private long defaultTimeoutMs;

    public ClaudeCodeBridge(ClaudeCodeWorkerPool workerPool, PromptSanitizer sanitizer,
                           LLMCallLogger callLogger) {
        this.workerPool = workerPool;
        this.sanitizer = sanitizer;
        this.callLogger = callLogger;
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

    /**
     * V1.5 카톡식: 단순 문자열 프롬프트로 모델을 직접 호출.
     * Sanitization은 호출자(예: ChatPromptAssembler)에서 사용자 입력을 별도 처리한다고 가정.
     */
    public String invoke(String prompt) throws ClaudeCodeException {
        return invoke(prompt, claudeModel);
    }

    public String invoke(String prompt, String model) throws ClaudeCodeException {
        String correlationId = UUID.randomUUID().toString();
        try {
            java.time.Duration timeout = java.time.Duration.ofMillis(defaultTimeoutMs);
            return workerPool.execute(
                    () -> runClaudeCommand(prompt, model),
                    timeout,
                    correlationId);
        } catch (ClaudeCodeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in ClaudeCodeBridge.invoke(String): {}", e.getMessage(), e);
            String detail = e.getMessage() == null ? "" : e.getMessage();
            throw new ClaudeCodeException("UNKNOWN_ERROR", detail, correlationId, -1,
                    detail.length() > 500 ? detail.substring(0, 500) : detail);
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
     * 취소 가능한 LLM 호출. 반환된 CancelableInvocation으로 외부에서 cancel() 가능.
     * 기존 invoke(String, String)은 변경 없이 유지 — ReportGenerationService 등 다른 호출처 사용 중.
     */
    public CancelableInvocation invokeCancelable(String prompt, String model, String sessionId) {
        CancelableInvocation inv = new CancelableInvocation(
            UUID.randomUUID().toString(), sessionId);

        CompletableFuture.runAsync(() -> {
            boolean acquired = false;
            try {
                // 기존 Pool Semaphore에 참여 — 동시성 한도(3) 공유
                acquired = workerPool.acquirePermit(2000L);
                if (!acquired) {
                    inv.getResultFuture().completeExceptionally(
                        new LLMCapacityException("Worker pool exhausted", inv.getInvocationId()));
                    return;
                }
                if (inv.isCanceled()) return;

                String result = runClaudeCommandWithInvocation(prompt, model, inv);
                if (!inv.isCanceled()) {
                    inv.getResultFuture().complete(result);
                }
            } catch (InvocationCanceledException e) {
                // cancel()이 이미 future를 completeExceptionally 했으므로 무시
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                inv.getResultFuture().completeExceptionally(e);
            } catch (Exception e) {
                if (!inv.isCanceled()) {
                    inv.getResultFuture().completeExceptionally(e);
                }
            } finally {
                if (acquired) workerPool.releasePermit();
            }
        });

        return inv;
    }

    private String runClaudeCommandWithInvocation(
            String prompt, String model, CancelableInvocation inv) throws Exception {

        // <conversation_history> 태그 기준으로 시스템 지시(system-prompt)와
        // 대화 이력+현재 메시지(user input)를 분리해 각각 올바른 위치로 전달
        ProcessBuilder pb;
        int splitIdx = prompt.indexOf("<conversation_history>");
        if (splitIdx > 0) {
            String systemPart = prompt.substring(0, splitIdx).trim();
            String userPart = prompt.substring(splitIdx).trim();
            pb = new ProcessBuilder(claudeBinaryPath, "--print", "--model", model,
                    "--system-prompt", systemPart, userPart);
        } else {
            pb = new ProcessBuilder(claudeBinaryPath, "--print", "--model", model,
                    "--system-prompt", DASIBOM_SYSTEM_ROLE, prompt);
        }
        pb.redirectErrorStream(false);

        Process process = pb.start();
        inv.attachProcess(process);  // 이제 외부에서 destroyForcibly() 가능

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        // isCanceled 플래그 우선 — destroyForcibly() 후 exitCode가 비정상일 수 있음
        if (inv.isCanceled()) {
            throw new InvocationCanceledException("Canceled mid-flight", inv.getInvocationId());
        }

        if (exitCode != 0) {
            String stderrExcerpt = stderr.length() > 500 ? stderr.substring(0, 500) : stderr;
            throw new ClaudeCodeException("CLAUDE_ERROR",
                "Claude CLI exited with code " + exitCode, null, exitCode, stderrExcerpt);
        }

        return stdout.trim();
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
     * Command format: claude --print --model <model> "<prompt>"
     */
    private String runClaudeCommand(String prompt) throws Exception {
        return runClaudeCommand(prompt, claudeModel);
    }

    private String runClaudeCommand(String prompt, String model) throws Exception {
        ProcessBuilder pb;
        int splitIdx = prompt.indexOf("<conversation_history>");
        if (splitIdx > 0) {
            String systemPart = prompt.substring(0, splitIdx).trim();
            String userPart = prompt.substring(splitIdx).trim();
            pb = new ProcessBuilder(claudeBinaryPath, "--print", "--model", model,
                    "--system-prompt", systemPart, userPart);
        } else {
            pb = new ProcessBuilder(claudeBinaryPath, "--print", "--model", model,
                    "--system-prompt", DASIBOM_SYSTEM_ROLE, prompt);
        }
        pb.redirectErrorStream(false);

        Process process = pb.start();

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

        return stdout.trim();
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
