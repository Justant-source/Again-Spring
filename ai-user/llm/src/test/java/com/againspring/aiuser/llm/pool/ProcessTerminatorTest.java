package com.againspring.aiuser.llm.pool;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProcessTerminatorTest {

    @Test
    void terminatesParentAndDescendantProcess() throws Exception {
        Process process = new ProcessBuilder("sh", "-c", "sleep 30 & child=$!; echo $child; wait")
                .start();
        long childPid;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            childPid = Long.parseLong(reader.readLine());
        }

        ProcessTerminator terminator = new ProcessTerminator(100);
        terminator.register(process);
        ProcessTerminator.TerminationResult result = terminator.terminate(process, "TIMEOUT", "test-tree");

        assertTrue(process.waitFor(2, TimeUnit.SECONDS));
        assertFalse(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false));
        assertEquals(0, result.remaining());
        assertEquals(0, terminator.getActiveProcessCount());
    }
}
