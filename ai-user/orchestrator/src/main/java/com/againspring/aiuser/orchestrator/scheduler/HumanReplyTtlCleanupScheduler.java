package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.service.threadplan.HumanReplyTtlCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Optional cron for human-reply TTL cleanup. No-op while {@code ttl-cleanup-enabled=false}
 * (default) so prod is never wiped on deploy/startup without an explicit admin trigger.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HumanReplyTtlCleanupScheduler {
    private final HumanReplyTtlCleanupService cleanupService;

    @Scheduled(cron = "${ai-user.human-reply.ttl-cleanup-cron:0 15 */6 * * *}")
    public void run() {
        try {
            cleanupService.run(Instant.now(), false);
        } catch (Exception e) {
            log.error("human-reply TTL cleanup failed: {}", e.getMessage(), e);
        }
    }
}
