package com.againspring.aiuser.llm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 생성 backend 파라미터에 따라 CLI 또는 clcocloud API 인보커를 선택한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvokerRouter {

    private final ClaudeCliInvoker cliInvoker;
    private final ClaudeApiInvoker apiInvoker;

    public Invoker route(String backend) {
        if ("API".equalsIgnoreCase(backend)) {
            log.debug("[InvokerRouter] backend={} → Claude API 선택", backend);
            return apiInvoker;
        }
        log.debug("[InvokerRouter] backend={} → Claude CLI 선택", backend);
        return cliInvoker;
    }
}
