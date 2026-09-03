package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyProviderTest {
    @Test
    void readsKeyAndBaseUrlFromConstructorOnly() {
        ApiKeyProvider p = new ApiKeyProvider("sk-test", "https://proxy.example/claude/v1/");
        assertEquals("sk-test", p.getKey());
        assertEquals("https://proxy.example/claude/v1/", p.getBaseUrl());
    }

    @Test
    void blankKeyIsNullAndBaseUrlDefaults() {
        ApiKeyProvider p = new ApiKeyProvider("", "");
        assertNull(p.getKey());
        assertEquals("https://api.anthropic.com", p.getBaseUrl());
    }
}
