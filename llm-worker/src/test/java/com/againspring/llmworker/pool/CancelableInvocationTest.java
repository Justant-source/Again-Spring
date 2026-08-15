package com.againspring.llmworker.pool;

import com.againspring.llmworker.exception.LlmTimeoutException;
import com.againspring.llmworker.service.ProcessTerminator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CancelableInvocationTest {

    @Test
    void timeoutBeforeProcessAttachmentKillsLateStartedProcess() throws Exception {
        ProcessTerminator terminator = new ProcessTerminator(100);
        CancelableInvocation invocation = new CancelableInvocation("late-process", "sync", terminator);

        assertTrue(invocation.timeout());
        Process process = new ProcessBuilder("sh", "-c", "sleep 30").start();
        invocation.attachProcess(process);

        ExecutionException error = assertThrows(ExecutionException.class,
                () -> invocation.getResultFuture().get(1, TimeUnit.SECONDS));
        assertInstanceOf(LlmTimeoutException.class, error.getCause());
        assertTrue(process.waitFor(2, TimeUnit.SECONDS));
    }
}
