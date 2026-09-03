package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class InvokerRouterTest {

    private final ClaudeCliInvoker claude = mock(ClaudeCliInvoker.class);
    private final ClaudeApiInvoker api = mock(ClaudeApiInvoker.class);
    private final CodexCliInvoker codex = mock(CodexCliInvoker.class);
    private final StubInvoker stub = mock(StubInvoker.class);

    @Test
    void routesEveryProvider() {
        InvokerRouter router = new InvokerRouter(claude, api, codex, stub);
        assertSame(claude, router.routeProvider(LlmProvider.CLAUDE));
        assertSame(codex, router.routeProvider(LlmProvider.CODEX));
        assertSame(api, router.routeProvider(LlmProvider.API));
        assertSame(stub, router.routeProvider(LlmProvider.STUB));
    }

    @Test
    void nullProviderIsRejected() {
        InvokerRouter router = new InvokerRouter(claude, api, codex, stub);
        assertThrows(IllegalArgumentException.class, () -> router.routeProvider(null));
    }
}
