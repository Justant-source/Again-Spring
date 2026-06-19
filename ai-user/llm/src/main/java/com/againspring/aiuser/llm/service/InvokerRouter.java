package com.againspring.aiuser.llm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * backend 파라미터와 무관하게 Claude CLI만 사용한다.
 * legacy backend=API 요청은 무시한다 — clcocloud API 경로는 비활성.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvokerRouter {

    private final ClaudeCliInvoker cliInvoker;

    public Invoker route(String backend) {
        if ("API".equalsIgnoreCase(backend)) {
            log.warn("[InvokerRouter] backend=API 요청 무시 — clcocloud API는 비활성, Claude CLI만 사용");
        }
        log.debug("[InvokerRouter] backend={} → Claude CLI 선택", backend);
        return cliInvoker;
    }
}
