package com.againspring.llm.bridge.exception;

import com.againspring.llm.LLMException;

/**
 * Exception specific to Claude Code CLI execution.
 * Includes exit code and stderr excerpt.
 */
public class ClaudeCodeException extends LLMException {
    private final int exitCode;
    private final String stderrExcerpt;

    public ClaudeCodeException(String errorCode, String message, String correlationId) {
        this(errorCode, message, correlationId, -1, "");
    }

    public ClaudeCodeException(String errorCode, String message, String correlationId,
                             int exitCode, String stderrExcerpt) {
        super(errorCode, message, correlationId);
        this.exitCode = exitCode;
        this.stderrExcerpt = stderrExcerpt;
    }

    public ClaudeCodeException(String errorCode, String message, Throwable cause,
                             String correlationId, int exitCode, String stderrExcerpt) {
        super(errorCode, message, cause, correlationId);
        this.exitCode = exitCode;
        this.stderrExcerpt = stderrExcerpt;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStderrExcerpt() {
        return stderrExcerpt;
    }
}
