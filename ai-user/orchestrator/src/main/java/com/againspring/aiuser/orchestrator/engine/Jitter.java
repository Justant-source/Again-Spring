package com.againspring.aiuser.orchestrator.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 행동 분산 지연 스케줄러.
 * tick 내 행동이 동시에 발사되지 않도록 랜덤 지연(jitter)을 추가.
 * 봇 티 방지: 자연스러운 분산.
 */
@Slf4j
@Component
public class Jitter {

    private static final Random RNG = new Random();
    private final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "jitter-scheduler");
            t.setDaemon(true);
            return t;
        });

    /**
     * Schedule a task with random delay within [minDelayMs, maxDelayMs].
     */
    public void scheduleWithinWindow(long minDelayMs, long maxDelayMs, Runnable task) {
        long delay = minDelayMs + (long)(RNG.nextDouble() * (maxDelayMs - minDelayMs));
        scheduler.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Jitter task failed: {}", e.getMessage(), e);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Schedule within a 10-minute window (600s).
     * Distributes actions across the tick window.
     */
    public void scheduleWithinTick(Runnable task) {
        scheduleWithinWindow(0, 600_000L, task);
    }

    /**
     * Schedule a reply with realistic human delay (5-60 min).
     */
    public void scheduleReplyWithDelay(Runnable task) {
        long minMs = 5 * 60 * 1000L;   // 5 minutes
        long maxMs = 60 * 60 * 1000L;  // 60 minutes
        scheduleWithinWindow(minMs, maxMs, task);
    }
}
