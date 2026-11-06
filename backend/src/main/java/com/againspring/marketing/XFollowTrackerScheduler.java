package com.againspring.marketing;

import com.againspring.repository.marketing.XFollowAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Syncs the following list every 6 hours (xx:30 KST); cleanup calls {@code sync} directly before its run.
 * While nothing is tracked yet, one bootstrap sync runs 3 minutes after startup instead of waiting for the next slot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XFollowTrackerScheduler {

    private final XFollowTrackerService trackerService;
    private final XFollowAccountRepository accountRepository;

    @Scheduled(initialDelay = 180_000L, fixedDelay = Long.MAX_VALUE)
    public void bootstrap() {
        try {
            if (accountRepository.count() == 0) {
                log.info("[x-follow-tracker] no tracked accounts yet — bootstrap sync");
                trackerService.sync(Instant.now());
            }
        } catch (Exception e) {
            log.warn("[x-follow-tracker] bootstrap sync failed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 30 */6 * * *", zone = "Asia/Seoul")
    public void tick() {
        try {
            trackerService.sync(Instant.now());
        } catch (Exception e) {
            log.warn("[x-follow-tracker] scheduled sync failed: {}", e.getMessage());
        }
    }
}
