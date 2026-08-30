package com.againspring.marketing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class XPersonaLearnScheduler {

    private final XPersonaLearnService xPersonaLearnService;

    /** Every minute KST — runs the learn job only at the configured dawn minute. */
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void tick() {
        try {
            XPersonaLearnService.LearnResult r = xPersonaLearnService.runIfDue(Instant.now());
            if (r != null) {
                log.info("[x-persona] learn tick status={} new={}", r.status(), r.newManuals());
            }
        } catch (Exception e) {
            log.warn("[x-persona] learn tick failed: {}", e.getMessage());
        }
    }
}
