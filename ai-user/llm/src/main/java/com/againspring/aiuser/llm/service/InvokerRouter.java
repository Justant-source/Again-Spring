package com.againspring.aiuser.llm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 생성 backend 파라미터에 따라 CLI 또는 clcocloud API 인보커를 선택한다.
 */
@Slf4j
@Service
public class InvokerRouter {

    private final ClaudeCliInvoker cliInvoker;
    private final ClaudeApiInvoker apiInvoker;
    private final CodexCliInvoker codexCliInvoker;

    @Autowired
    public InvokerRouter(ClaudeCliInvoker cliInvoker, ClaudeApiInvoker apiInvoker, CodexCliInvoker codexCliInvoker) {
        this.cliInvoker = cliInvoker;
        this.apiInvoker = apiInvoker;
        this.codexCliInvoker = codexCliInvoker;
    }

    /** Kept for focused legacy unit tests and callers that only route CLI/API. */
    public InvokerRouter(ClaudeCliInvoker cliInvoker, ClaudeApiInvoker apiInvoker) {
        this(cliInvoker, apiInvoker, null);
    }

    public Invoker route(String backend) {
        if ("API".equalsIgnoreCase(backend)) {
            log.debug("[InvokerRouter] backend={} → Claude API 선택", backend);
            return apiInvoker;
        }
        log.debug("[InvokerRouter] backend={} → Claude CLI 선택", backend);
        return cliInvoker;
    }

    /** New plan APIs only: session CLI provider selection, with no API fallback. */
    public Invoker routeProvider(LlmProvider provider) {
        if (provider == LlmProvider.CODEX) {
            if (codexCliInvoker == null) throw new IllegalStateException("Codex CLI invoker is unavailable");
            return codexCliInvoker;
        }
        return cliInvoker;
    }
}
