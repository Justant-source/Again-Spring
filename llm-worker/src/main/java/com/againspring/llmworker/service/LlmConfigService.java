package com.againspring.llmworker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 런타임 LLM 설정 (Anthropic 호환 base URL).
 * 컨테이너 시작 시 env ANTHROPIC_BASE_URL 또는 application.yml 값으로 초기화.
 * POST /internal/config/anthropic-base-url 으로 핫-업데이트 가능.
 */
@Slf4j
@Service
public class LlmConfigService {

    private volatile String anthropicBaseUrl;

    public LlmConfigService(
            @Value("${anthropic.base-url:}") String initialBaseUrl) {
        this.anthropicBaseUrl = (initialBaseUrl != null && !initialBaseUrl.isBlank())
                ? initialBaseUrl.strip()
                : null;
        if (this.anthropicBaseUrl != null) {
            log.info("[llm-config] anthropic base URL initialised: {}", this.anthropicBaseUrl);
        }
    }

    /** null = 환경 변수 상속 (Claude CLI가 기본값 사용). */
    public String getAnthropicBaseUrl() {
        return anthropicBaseUrl;
    }

    public void setAnthropicBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            this.anthropicBaseUrl = null;
            log.info("[llm-config] anthropic base URL cleared → env/default");
        } else {
            this.anthropicBaseUrl = url.strip();
            log.info("[llm-config] anthropic base URL updated: {}", this.anthropicBaseUrl);
        }
    }
}
