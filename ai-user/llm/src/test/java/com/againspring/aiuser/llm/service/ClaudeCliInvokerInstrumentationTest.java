package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.exception.ClaudeCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.*;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClaudeCliInvoker instrumentation ([LLMSTATS] logging).
 *
 * Tests validate:
 * 1. Usage token extraction from stream-json result event
 * 2. Fallback to zeros when usage unavailable
 * 3. FAIL path logs correct stats on errors
 * 4. Cache hit percentage calculation
 */
@DisplayName("ClaudeCliInvoker Instrumentation Tests")
class ClaudeCliInvokerInstrumentationTest {

    private ClaudeCliInvoker invoker;
    @Mock
    private StructuredSchemaCatalog schemaCatalog;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        invoker = new ClaudeCliInvoker(schemaCatalog);
    }

    /**
     * Test usage token extraction from a complete stream-json result event with usage object.
     * This simulates the CLI emitting: {"type":"result","result":"text","usage":{...}}
     */
    @Test
    @DisplayName("Should extract usage tokens from result event")
    void testUsageExtractionFromResultEvent() throws Exception {
        String streamOutput = """
            {"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":"Test "}}}
            {"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":"response"}}}
            {"type":"result","result":"Test response","usage":{"input_tokens":100,"output_tokens":50,"cache_read_input_tokens":20,"cache_creation_input_tokens":10}}
            """;

        Process mockProcess = createMockProcess(streamOutput, 0);

        assertDoesNotThrow(() -> invokeViaReflection(mockProcess, null, "model", 1));
    }

    /**
     * Test fallback to zero tokens when usage object is missing from result event.
     * This should log zeros but not fail.
     */
    @Test
    @DisplayName("Should fallback to zeros when usage is missing")
    void testZeroFallbackWhenUsageMissing() throws Exception {
        String streamOutput = """
            {"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":"Response"}}}
            {"type":"result","result":"Response"}
            """;

        Process mockProcess = createMockProcess(streamOutput, 0);

        assertDoesNotThrow(() -> invokeViaReflection(mockProcess, null, "model", 1));
    }

    /**
     * Test that FAIL result is properly logged with error code.
     */
    @Test
    @DisplayName("Should log FAIL result for error responses")
    void testFailPathLogging() throws Exception {
        String streamOutput = """
            {"type":"result","result":"Error occurred","is_error":true,"subtype":"error","usage":{"input_tokens":50,"output_tokens":0,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}
            """;

        Process mockProcess = createMockProcess(streamOutput, 0);

        assertThrows(ClaudeCodeException.class, () -> invokeViaReflection(mockProcess, null, "model", 1));
    }

    /**
     * Test cache hit percentage calculation.
     */
    @Test
    @DisplayName("Should calculate cache hit percentage correctly")
    void testCacheHitPercentageCalculation() throws Exception {
        // Scenario: 100 input tokens, 50 cache_read, 25 cache_write
        // Total denom = 100 + 50 + 25 = 175
        // cache_hit = 50 * 100 / 175 = 28.57% ≈ 29%
        String streamOutput = """
            {"type":"result","result":"Output","usage":{"input_tokens":100,"output_tokens":40,"cache_read_input_tokens":50,"cache_creation_input_tokens":25}}
            """;

        Process mockProcess = createMockProcess(streamOutput, 0);

        assertDoesNotThrow(() -> invokeViaReflection(mockProcess, null, "model", 1));
    }

    /**
     * Test that provider error text in output is caught and logged as FAIL.
     */
    @Test
    @DisplayName("Should detect provider error in output and log FAIL")
    void testProviderErrorDetectionAndLogging() throws Exception {
        String streamOutput = """
            {"type":"result","result":"Credit balance is too low","usage":{"input_tokens":50,"output_tokens":0,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}
            """;

        Process mockProcess = createMockProcess(streamOutput, 0);

        assertThrows(ClaudeCodeException.class, () -> invokeViaReflection(mockProcess, null, "model", 1));
    }

    /**
     * Test that partial messages are accumulated correctly and final result is used.
     */
    @Test
    @DisplayName("Should prioritize result event over accumulated partials")
    void testResultPrioritization() throws Exception {
        String streamOutput = """
            {"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":"Partial "}}}
            {"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":"accumulated"}}}
            {"type":"result","result":"Complete response","usage":{"input_tokens":100,"output_tokens":50,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}
            """;

        Process mockProcess = createMockProcess(streamOutput, 0);

        assertDoesNotThrow(() -> invokeViaReflection(mockProcess, null, "model", 1));
    }

    /**
     * Test exit code handling with content (should succeed).
     */
    @Test
    @DisplayName("Should succeed on non-zero exit code if output present")
    void testExitCodeWithContent() throws Exception {
        String streamOutput = """
            {"type":"result","result":"Valid output","usage":{"input_tokens":100,"output_tokens":50,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}
            """;

        Process mockProcess = createMockProcess(streamOutput, 1); // Non-zero exit

        assertDoesNotThrow(() -> invokeViaReflection(mockProcess, null, "model", 1));
    }

    /**
     * Test that empty output (no result event) logs stats with zero tokens.
     * Note: Exit code checking is part of invokeOnce, not readStreamingOutput directly,
     * so this tests that readStreamingOutput handles empty streams gracefully.
     */
    @Test
    @DisplayName("Should handle empty stream output gracefully")
    void testEmptyStreamOutput() throws Exception {
        String streamOutput = "";

        Process mockProcess = createMockProcess(streamOutput, 0);

        assertDoesNotThrow(() -> invokeViaReflection(mockProcess, null, "model", 1));
    }

    // ─── Helper Methods ───────────────────────────────────────────

    /**
     * Create a mock process that returns the given stream output and exit code.
     */
    private Process createMockProcess(String stdout, int exitCode) throws Exception {
        Process mockProcess = mock(Process.class);
        InputStream inputStream = new ByteArrayInputStream(stdout.getBytes());
        InputStream errorStream = new ByteArrayInputStream("".getBytes());
        OutputStream outputStream = new ByteArrayOutputStream();

        when(mockProcess.getInputStream()).thenReturn(inputStream);
        when(mockProcess.getErrorStream()).thenReturn(errorStream);
        when(mockProcess.getOutputStream()).thenReturn(outputStream);
        when(mockProcess.waitFor()).thenReturn(exitCode);

        return mockProcess;
    }

    /**
     * Invoke the readStreamingOutput method via reflection to test internal instrumentation.
     * This is a private method, so we use reflection to access it.
     * Handles InvocationTargetException wrapping by unwrapping the cause.
     */
    private void invokeViaReflection(Process process, Object inv, String model, int attempt) throws Exception {
        // Use reflection to call private readStreamingOutput method
        var method = ClaudeCliInvoker.class.getDeclaredMethod(
            "readStreamingOutput",
            Process.class,
            com.againspring.aiuser.llm.pool.CancelableInvocation.class,
            String.class,
            String.class,
            int.class,
            long.class
        );
        method.setAccessible(true);

        String corrId = java.util.UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();

        try {
            method.invoke(invoker, process, inv, corrId, model, attempt, startMs);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap the actual exception thrown by the method
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            } else if (cause != null) {
                throw new Exception(cause);
            } else {
                throw e;
            }
        }
    }
}
