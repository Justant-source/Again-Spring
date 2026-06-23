package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class InvokerRouterTest {

    @Test
    void routeReturnsApiInvokerWhenBackendIsApi() {
        ClaudeCliInvoker cliInvoker = mock(ClaudeCliInvoker.class);
        ClaudeApiInvoker apiInvoker = mock(ClaudeApiInvoker.class);
        InvokerRouter router = new InvokerRouter(cliInvoker, apiInvoker);

        assertSame(apiInvoker, router.route("API"));
        assertSame(apiInvoker, router.route("api"));
    }

    @Test
    void routeFallsBackToCliForNullOrNonApiBackend() {
        ClaudeCliInvoker cliInvoker = mock(ClaudeCliInvoker.class);
        ClaudeApiInvoker apiInvoker = mock(ClaudeApiInvoker.class);
        InvokerRouter router = new InvokerRouter(cliInvoker, apiInvoker);

        assertSame(cliInvoker, router.route(null));
        assertSame(cliInvoker, router.route("CLI"));
        assertSame(cliInvoker, router.route("unknown"));
    }
}
