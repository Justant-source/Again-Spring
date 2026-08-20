package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlmParseFailureSamplerTest {

    private LlmParseFailureSampler sampler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        sampler = new LlmParseFailureSampler();
        // Set sampling dir to temp directory
        ReflectionTestUtils.setField(sampler, "sampleDir", tempDir.toString());
        // Enable sampling
        ReflectionTestUtils.setField(sampler, "samplingEnabled", true);
    }

    @Test
    void testRecordFailure() {
        sampler.recordFailure("corr123", "hash1", "Invalid JSON", "{invalid json}", 1);

        List<LlmParseFailureSampler.SampleRecord> samples = sampler.getSamples();
        assertEquals(1, samples.size());

        LlmParseFailureSampler.SampleRecord record = samples.get(0);
        assertEquals("corr123", record.getCorrId());
        assertEquals("hash1", record.getPromptHash());
        assertEquals("Invalid JSON", record.getErrorMessage());
        assertEquals(1, record.getAttempt());
    }

    @Test
    void testMaxSamplesLimit() {
        // Record 25 samples (max is 20)
        for (int i = 0; i < 25; i++) {
            sampler.recordFailure("corr" + i, "hash" + i, "Error" + i, "response" + i, 1);
        }

        List<LlmParseFailureSampler.SampleRecord> samples = sampler.getSamples();
        assertEquals(20, samples.size());

        // First 5 should be gone
        assertFalse(samples.stream().anyMatch(s -> s.getCorrId().equals("corr0")));
        assertFalse(samples.stream().anyMatch(s -> s.getCorrId().equals("corr4")));

        // Last 5 should be there
        assertTrue(samples.stream().anyMatch(s -> s.getCorrId().equals("corr20")));
        assertTrue(samples.stream().anyMatch(s -> s.getCorrId().equals("corr24")));
    }

    @Test
    void testResponseTruncation() {
        String longResponse = "x".repeat(1000);
        sampler.recordFailure("corr", "hash", "error", longResponse, 1);

        LlmParseFailureSampler.SampleRecord record = sampler.getSamples().get(0);
        assertTrue(record.getTruncatedResponse().length() <= 500);
    }

    @Test
    void testErrorMessageTruncation() {
        String longError = "error message " + "very long ".repeat(50);
        sampler.recordFailure("corr", "hash", longError, "response", 1);

        LlmParseFailureSampler.SampleRecord record = sampler.getSamples().get(0);
        assertTrue(record.getErrorMessage().length() <= 200);
    }

    @Test
    void testPromptHashTruncation() {
        String longHash = "a".repeat(50);
        sampler.recordFailure("corr", longHash, "error", "response", 1);

        LlmParseFailureSampler.SampleRecord record = sampler.getSamples().get(0);
        assertTrue(record.getPromptHash().length() <= 16);
    }

    @Test
    void testPersistToFile() throws Exception {
        sampler.recordFailure("corr123", "hash1", "JSON parse error", "{invalid}", 1);

        Path failuresFile = tempDir.resolve("parse-failures.tsv");
        assertTrue(Files.exists(failuresFile));

        List<String> lines = Files.readAllLines(failuresFile);
        assertEquals(1, lines.size());

        String line = lines.get(0);
        assertTrue(line.contains("corr123"));
        assertTrue(line.contains("hash1"));
        assertTrue(line.contains("JSON parse error"));
    }

    @Test
    void testSamplingDisabled() {
        ReflectionTestUtils.setField(sampler, "samplingEnabled", false);
        sampler.recordFailure("corr", "hash", "error", "response", 1);

        List<LlmParseFailureSampler.SampleRecord> samples = sampler.getSamples();
        assertEquals(0, samples.size());
    }

    @Test
    void testClearSamples() {
        sampler.recordFailure("corr1", "hash1", "error1", "response1", 1);
        sampler.recordFailure("corr2", "hash2", "error2", "response2", 1);

        assertEquals(2, sampler.getSamples().size());
        sampler.clearSamples();
        assertEquals(0, sampler.getSamples().size());
    }

    @Test
    void testMultipleAttempts() {
        sampler.recordFailure("corr", "hash", "attempt 1 error", "response", 1);
        sampler.recordFailure("corr", "hash", "attempt 2 error", "response", 2);

        List<LlmParseFailureSampler.SampleRecord> samples = sampler.getSamples();
        assertEquals(2, samples.size());
        assertEquals(1, samples.get(0).getAttempt());
        assertEquals(2, samples.get(1).getAttempt());
    }

    @Test
    void testNullValues() {
        // Should handle null gracefully
        sampler.recordFailure("corr", null, null, null, 1);

        List<LlmParseFailureSampler.SampleRecord> samples = sampler.getSamples();
        assertEquals(1, samples.size());

        LlmParseFailureSampler.SampleRecord record = samples.get(0);
        assertEquals("UNKNOWN", record.getPromptHash());
        assertEquals("unknown", record.getErrorMessage());
        assertEquals("", record.getTruncatedResponse());
    }

    @Test
    void testTimestampFormat() {
        sampler.recordFailure("corr", "hash", "error", "response", 1);

        LlmParseFailureSampler.SampleRecord record = sampler.getSamples().get(0);
        // Should be ISO8601 format
        assertNotNull(record.getTimestamp());
        assertTrue(record.getTimestamp().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"));
    }
}
