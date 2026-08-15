package com.againspring.llmworker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Claude CLI parent and its descendants are one execution unit. Killing only
 * the shell/Node parent leaves child processes consuming worker capacity.
 */
@Slf4j
@Component
public class ProcessTerminator {

    private final long gracefulShutdownMs;
    private final ConcurrentHashMap<Long, ProcessHandle> activeProcesses = new ConcurrentHashMap<>();
    private final AtomicLong terminatedProcesses = new AtomicLong();
    private final AtomicLong forcedTerminations = new AtomicLong();

    public ProcessTerminator(@Value("${llm.worker.process-termination-grace-ms:2000}") long gracefulShutdownMs) {
        this.gracefulShutdownMs = gracefulShutdownMs;
    }

    public TerminationResult terminate(Process process, String reason, String executionId) {
        if (process == null) return TerminationResult.empty();

        List<ProcessHandle> handles = processTree(process);
        closeStreams(process);
        int aliveBefore = (int) handles.stream().filter(ProcessHandle::isAlive).count();
        int requested = destroy(handles, false);
        waitForExit(handles, gracefulShutdownMs);
        int forced = destroy(handles, true);
        int remaining = (int) handles.stream().filter(ProcessHandle::isAlive).count();
        int terminated = Math.max(0, aliveBefore - remaining);
        terminatedProcesses.addAndGet(terminated);
        forcedTerminations.addAndGet(forced);
        activeProcesses.entrySet().removeIf(entry -> !entry.getValue().isAlive());

        log.warn("Claude CLI termination: id={}, reason={}, rootPid={}, requested={}, forced={}, remaining={}",
                executionId, reason, process.pid(), requested, forced, remaining);
        return new TerminationResult(handles.size(), requested, forced, remaining);
    }

    public void register(Process process) {
        activeProcesses.put(process.pid(), process.toHandle());
    }

    public void release(Process process) {
        if (process != null) activeProcesses.remove(process.pid());
    }

    public long getTerminatedProcesses() {
        return terminatedProcesses.get();
    }

    public long getForcedTerminations() {
        return forcedTerminations.get();
    }

    public int getActiveProcessCount() {
        activeProcesses.entrySet().removeIf(entry -> !entry.getValue().isAlive());
        return activeProcesses.size();
    }

    private List<ProcessHandle> processTree(Process process) {
        List<ProcessHandle> handles = new ArrayList<>();
        addDescendantsPostOrder(process.toHandle(), handles);
        return handles;
    }

    private void addDescendantsPostOrder(ProcessHandle handle, List<ProcessHandle> handles) {
        handle.children().forEach(child -> addDescendantsPostOrder(child, handles));
        // Descendants before their parent prevents an orphaned CLI child process.
        handles.add(handle);
    }

    private int destroy(List<ProcessHandle> handles, boolean forcibly) {
        int count = 0;
        for (ProcessHandle handle : handles) {
            if (handle.isAlive() && (forcibly ? handle.destroyForcibly() : handle.destroy())) count++;
        }
        return count;
    }

    private void waitForExit(List<ProcessHandle> handles, long timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (handles.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void closeStreams(Process process) {
        try { process.getOutputStream().close(); } catch (IOException ignored) { }
        try { process.getInputStream().close(); } catch (IOException ignored) { }
        try { process.getErrorStream().close(); } catch (IOException ignored) { }
    }

    public record TerminationResult(int discovered, int requested, int forced, int remaining) {
        static TerminationResult empty() {
            return new TerminationResult(0, 0, 0, 0);
        }
    }
}
