package com.againspring.aiuser.llm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * backend 파라미터("CLI" | "API" | null)에 따라 Invoker를 선택.
 * API 키: ApiKeyProvider 경유 (DB 우선 → 환경변수 폴백). 키 없으면 CLI 폴백.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvokerRouter {

    private final ClaudeCliInvoker cliInvoker;
    private final ClaudeApiInvoker apiInvoker;
    private final ApiKeyProvider   apiKeyProvider;

    public Invoker route(String backend) {
        if ("API".equalsIgnoreCase(backend)) {
            String key = apiKeyProvider.getKey();
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("backend=API 요청됐으나 ANTHROPIC_API_KEY 미설정");
            }
            log.info("[InvokerRouter] ⚠️  backend=API 선택 — Anthropic API 직접 호출 (과금 발생)");
            return apiInvoker;
        }
        log.debug("[InvokerRouter] backend={} → CLI 선택", backend);
        return cliInvoker;  // CLI | null | OFF 모두 CLI
    }
}
