package com.againspring.aiuser.llm.pool;

import com.againspring.aiuser.llm.exception.LlmTimeoutException;
import com.againspring.aiuser.llm.service.Invoker;
import com.againspring.aiuser.llm.service.InvokerRouter;
import com.againspring.aiuser.llm.service.LlmProvider;
import com.againspring.aiuser.llm.service.ProviderHealthRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmWorkerPoolTimeoutTest {

    @Test
    void timeoutTerminatesAttachedProcessAndFreesSlot() throws Exception {
        AtomicReference<Process> started = new AtomicReference<>();
        Invoker slow = new Invoker() {
            @Override public String invoke(String prompt, String model) throws com.againspring.aiuser.llm.exception.LlmException {
                try {
                    Process p = new ProcessBuilder("sh", "-c", "sleep 30").start();
                    started.set(p);
                    ExecutionSlot.attachCurrent(p);
                    p.waitFor();
                    return "late";
                } catch (Exception e) { throw new RuntimeException(e); }
            }
            @Override public String invokeWithCancelSupport(String p, String m, CancelableInvocation inv) { return ""; }
        };
        InvokerRouter router = mock(InvokerRouter.class);
        when(router.routeProvider(any())).thenReturn(slow);

        LlmWorkerPool pool = new LlmWorkerPool(null, router, new ProcessTerminator(200),
                new ProviderHealthRegistry(10, java.time.Clock.systemUTC()));
        ReflectionTestUtils.setField(pool, "poolSize", 1);
        ReflectionTestUtils.setField(pool, "queueCapacity", 2);
        ReflectionTestUtils.setField(pool, "queueWaitTimeoutMs", 5000L);
        ReflectionTestUtils.setField(pool, "defaultTimeoutMs", 60000L);
        ReflectionTestUtils.setField(pool, "defaultModel", "m");
        pool.init();

        assertThrows(LlmTimeoutException.class,
            () -> pool.executeSyncTask("p", "m", 500, "corr", LlmProvider.CLAUDE));

        Process p = started.get();
        assertNotNull(p);
        p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
        assertFalse(p.isAlive(), "timeout must kill the CLI process tree");
        Thread.sleep(200);
        assertEquals(1, pool.getMetrics().getAvailable(), "slot must be freed after timeout");
        assertEquals(1, pool.getMetrics().getTimedOut());
        pool.shutdown();
    }
}
