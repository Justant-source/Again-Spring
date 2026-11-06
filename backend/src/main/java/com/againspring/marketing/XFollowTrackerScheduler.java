package com.againspring.marketing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Syncs the following list every 6 hours (xx:30 KST); cleanup calls {@code sync} directly before its run. */
@Slf4j
@Component
@RequiredArgsConstructor
public class XFollowTrackerScheduler {

    private final XFollowTrackerService trackerService;

    @Scheduled(cron = "0 30 */6 * * *", zone = "Asia/Seoul")
    public void tick() {
        try {
            trackerService.sync(Instant.now());
        } catch (Exception e) {
            log.warn("[x-follow-tracker] scheduled sync failed: {}", e.getMessage());
        }
    }
}
