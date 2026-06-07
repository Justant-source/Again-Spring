package com.againspring.aiuser.llm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * backend 파라미터("CLI" | "API" | null)에 따라 Invoker를 선택.
 * API 키가 없으면 API 요청도 CLI로 폴백.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvokerRouter {

    private final ClaudeCliInvoker cliInvoker;
    private final ClaudeApiInvoker apiInvoker;

    @Value("${anthropic.api-key:}")
    private String apiKey;

    public Invoker route(String backend) {
        if ("API".equalsIgnoreCase(backend)) {
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("[InvokerRouter] backend=API 요청됐으나 ANTHROPIC_API_KEY 미설정 → CLI 폴백");
                return cliInvoker;
            }
            log.info("[InvokerRouter] ⚠️  backend=API 선택 — Anthropic API 직접 호출 (과금 발생)");
            return apiInvoker;
        }
        log.debug("[InvokerRouter] backend={} → CLI 선택", backend);
        return cliInvoker;  // CLI | null | OFF 모두 CLI
    }
}
