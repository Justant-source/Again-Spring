package com.againspring.aiuser.llm.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Collects JSON parse failures from StructuredGenerationService into bounded sample store.
 * Purpose: diagnose which prompts consistently produce malformed JSON.
 *
 * <p>Storage: rolling files under configurable directory (default /tmp/llm-parse-failures).
 * Keeps max ~20 samples, overwrites oldest when limit reached.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmParseFailureSampler {
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneId.of("UTC"));
    private static final int MAX_SAMPLES = 20;
    private static final int MAX_RESPONSE_LEN = 500;  // Truncate large responses
    private static final int MAX_PROMPT_HASH_LEN = 16;

    @Value("${llm.parse-failure-sampling.enabled:true}")
    private boolean samplingEnabled;

    @Value("${llm.parse-failure-sampling.dir:/tmp/llm-parse-failures}")
    private String sampleDir;

    private volatile List<SampleRecord> samples = Collections.synchronizedList(new ArrayList<>());

    @Data
    public static class SampleRecord {
        private String timestamp;
        private String corrId;
        private String promptHash;
        private String errorMessage;
        private String truncatedResponse;
        private int attempt;
    }

    /**
     * Record a parse failure. Called by StructuredGenerationService on JSON parse exception.
     */
    public void recordFailure(String corrId, String promptHash, String errorMessage, String rawResponse, int attempt) {
        if (!samplingEnabled) return;

        try {
            SampleRecord sample = new SampleRecord();
            sample.setTimestamp(ISO_FORMATTER.format(Instant.now()));
            sample.setCorrId(corrId);
            sample.setPromptHash(promptHash != null ? promptHash.substring(0, Math.min(promptHash.length(), MAX_PROMPT_HASH_LEN)) : "UNKNOWN");
            sample.setErrorMessage(errorMessage != null ? errorMessage.substring(0, Math.min(errorMessage.length(), 200)) : "unknown");
            sample.setTruncatedResponse(rawResponse != null ? rawResponse.substring(0, Math.min(rawResponse.length(), MAX_RESPONSE_LEN)) : "");
            sample.setAttempt(attempt);

            samples.add(sample);
            if (samples.size() > MAX_SAMPLES) {
                samples.remove(0);
            }

            persistToFile(sample);
        } catch (Exception e) {
            log.warn("Failed to record parse failure sample: {}", e.getMessage());
        }
    }

    /**
     * Persist sample to file for later analysis.
     * One sample per line, TSV format for easy grep/parsing.
     */
    private void persistToFile(SampleRecord sample) throws IOException {
        Path dir = Paths.get(sampleDir);
        Files.createDirectories(dir);

        Path file = dir.resolve("parse-failures.tsv");
        String line = String.format("%s\t%s\t%s\t%s\t%s\t%d%n",
            sample.getTimestamp(),
            sample.getCorrId(),
            sample.getPromptHash(),
            sample.getErrorMessage().replace("\t", " ").replace("\n", " "),
            sample.getTruncatedResponse().replace("\t", " ").replace("\n", " "),
            sample.getAttempt());

        Files.write(file, line.getBytes(), java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND);
    }

    public List<SampleRecord> getSamples() {
        return Collections.unmodifiableList(new ArrayList<>(samples));
    }

    public void clearSamples() {
        samples.clear();
    }
}
