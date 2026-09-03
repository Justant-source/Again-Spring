package com.againspring.aiuser.llm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** provider enum 하나로 인보커를 고른다. 문자열 backend 라우팅은 폐기(2026-09). */
@Slf4j
@Service
public class InvokerRouter {

    private final ClaudeCliInvoker claude;
    private final ClaudeApiInvoker api;
    private final CodexCliInvoker codex;
    private final StubInvoker stub;

    public InvokerRouter(ClaudeCliInvoker claude, ClaudeApiInvoker api, CodexCliInvoker codex, StubInvoker stub) {
        this.claude = claude;
        this.api = api;
        this.codex = codex;
        this.stub = stub;
    }

    public Invoker routeProvider(LlmProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is required");
        return switch (provider) {
            case CLAUDE -> claude;
            case CODEX -> codex;
            case API -> api;
            case STUB -> stub;
        };
    }
}
