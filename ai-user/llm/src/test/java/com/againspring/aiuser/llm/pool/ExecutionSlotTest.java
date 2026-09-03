package com.againspring.aiuser.llm.pool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionSlotTest {

    @Test
    void terminateKillsAttachedProcessTree() throws Exception {
        ProcessTerminator terminator = new ProcessTerminator(200);
        ExecutionSlot slot = ExecutionSlot.open("corr-1");
        try {
            Process p = new ProcessBuilder("sh", "-c", "sleep 30").start();
            ExecutionSlot.attachCurrent(p);
            assertTrue(p.isAlive());
            assertTrue(slot.terminate(terminator, "test-timeout"));
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            assertFalse(p.isAlive());
        } finally {
            slot.close();
        }
    }

    @Test
    void terminateWithoutProcessIsFalse() {
        ProcessTerminator terminator = new ProcessTerminator(200);
        ExecutionSlot slot = ExecutionSlot.open("corr-2");
        try {
            assertFalse(slot.terminate(terminator, "nothing"));
        } finally {
            slot.close();
        }
    }

    @Test
    void attachWithoutSlotIsNoop() throws Exception {
        Process p = new ProcessBuilder("true").start();
        ExecutionSlot.attachCurrent(p); // must not throw
        p.waitFor();
    }
}
