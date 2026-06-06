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
                log.warn("Backend=API requested but ANTHROPIC_API_KEY not set — falling back to CLI");
                return cliInvoker;
            }
            return apiInvoker;
        }
        return cliInvoker;  // CLI | null | OFF 모두 CLI
    }
}
