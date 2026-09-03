package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliAuthFailureDetectorTest {
    @Test
    void detectsKnownAuthMessages() {
        assertTrue(CliAuthFailureDetector.isAuthFailure("Error: Not logged in. Please run /login"));
        assertTrue(CliAuthFailureDetector.isAuthFailure("Your organization has disabled Claude subscription access for Claude Code"));
        assertTrue(CliAuthFailureDetector.isAuthFailure("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"}}"));
        assertTrue(CliAuthFailureDetector.isAuthFailure("OAuth token has expired"));
        assertTrue(CliAuthFailureDetector.isAuthFailure("invalid_grant"));
        assertTrue(CliAuthFailureDetector.isAuthFailure("HTTP 401 Unauthorized"));
    }

    @Test
    void ignoresOrdinaryErrors() {
        assertFalse(CliAuthFailureDetector.isAuthFailure("rate_limit_error: overloaded"));
        assertFalse(CliAuthFailureDetector.isAuthFailure(""));
        assertFalse(CliAuthFailureDetector.isAuthFailure(null));
    }
}
