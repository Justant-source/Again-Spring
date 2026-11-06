package com.againspring.marketing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 04:00 KST daily: unfollow big accounts that ignored us for a week (gated by system_setting). */
@Slf4j
@Component
@RequiredArgsConstructor
public class XFollowCleanupScheduler {

    private final XFollowCleanupService cleanupService;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void tick() {
        try {
            cleanupService.run(Instant.now());
        } catch (Exception e) {
            log.warn("[x-unfollow] cleanup failed: {}", e.getMessage());
        }
    }
}
