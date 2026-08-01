package com.againspring.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler client-abort detection")
class GlobalExceptionHandlerClientAbortTest {

    @Test
    void detectsBrokenPipeMessage() {
        Exception ex = new IOException("ServletOutputStream failed to write: java.io.IOException: Broken pipe");
        assertTrue(GlobalExceptionHandler.isClientAbort(ex));
    }

    @Test
    void detectsNestedBrokenPipe() {
        Exception ex = new RuntimeException("wrap", new IOException("Broken pipe"));
        assertTrue(GlobalExceptionHandler.isClientAbort(ex));
    }

    @Test
    void ignoresUnrelatedExceptions() {
        assertFalse(GlobalExceptionHandler.isClientAbort(new IllegalStateException("boom")));
        assertFalse(GlobalExceptionHandler.isClientAbort(new IOException("disk full")));
    }
}
