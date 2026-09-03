package com.againspring.aiuser.llm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** API provider 자격은 env로만 받는다. DB(system_setting) 조회는 폐기 — 워커는 무상태다(2026-09). */
@Service
public class ApiKeyProvider {
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private final String key;
    private final String baseUrl;

    public ApiKeyProvider(@Value("${anthropic.api-key:}") String key,
                          @Value("${anthropic.base-url:}") String baseUrl) {
        this.key = (key == null || key.isBlank()) ? null : key.trim();
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl.trim();
    }

    public String getKey() { return key; }
    public String getBaseUrl() { return baseUrl; }
}
