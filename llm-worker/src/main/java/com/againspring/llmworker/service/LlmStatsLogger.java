package com.againspring.llmworker.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Builder for [LLMSTATS] single-line log format.
 * Ensures consistent, grep-friendly output across all LLM call sites.
 *
 * Format (single line, tab/newline safe):
 * [LLMSTATS] ts={ISO8601} sys=AS type={TYPE} model={model} attempt={1-3} retryReason={CODE}
 *   in={N} out={M} cache_read={P} cache_write={Q} cache_hit={R%} result={OK|RETRY|FAIL}
 *   duration_ms={T} corrId={uuid}
 *
 * Note: This is a local copy (not imported from ai-user/llm module) to avoid cross-module
 * dependencies. Kept in sync with ai-user/llm/LlmStatsLogger.
 */
public class LlmStatsLogger {
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneId.of("UTC"));

    private String ts;
    private String sys = "AS";
    private String type;
    private String model;
    private int attempt = 1;
    private String retryReason = "NONE";
    private int inTokens;
    private int outTokens;
    private int cacheReadTokens;
    private int cacheWriteTokens;
    private int cacheHitPercent;
    private String result = "OK";
    private long durationMs;
    private String corrId;

    public LlmStatsLogger(String type, String model, String corrId) {
        this.ts = ISO_FORMATTER.format(Instant.now());
        this.type = type;
        this.model = model;
        this.corrId = corrId;
    }

    public LlmStatsLogger attempt(int attempt) {
        this.attempt = attempt;
        return this;
    }

    public LlmStatsLogger retryReason(String reason) {
        this.retryReason = reason != null ? reason : "NONE";
        return this;
    }

    public LlmStatsLogger tokens(int in, int out) {
        this.inTokens = in;
        this.outTokens = out;
        return this;
    }

    public LlmStatsLogger cacheTokens(int read, int write) {
        this.cacheReadTokens = read;
        this.cacheWriteTokens = write;
        return this;
    }

    public LlmStatsLogger cacheHitPercent(int percent) {
        this.cacheHitPercent = percent;
        return this;
    }

    public LlmStatsLogger result(String result) {
        this.result = result != null ? result : "OK";
        return this;
    }

    public LlmStatsLogger duration(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    public String build() {
        // Single-line, tab-safe output for grep + jq parsing
        return String.format(
            "[LLMSTATS] ts=%s sys=%s type=%s model=%s attempt=%d retryReason=%s " +
            "in=%d out=%d cache_read=%d cache_write=%d cache_hit=%d%% result=%s " +
            "duration_ms=%d corrId=%s",
            ts, sys, type, model, attempt, retryReason,
            inTokens, outTokens, cacheReadTokens, cacheWriteTokens, cacheHitPercent, result,
            durationMs, corrId
        );
    }
}
