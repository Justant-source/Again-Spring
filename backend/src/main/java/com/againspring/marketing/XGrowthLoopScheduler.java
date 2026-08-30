package com.againspring.marketing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * One-minute KST tick for ritual / inbound / outbound. Each call is isolated so one
 * failure cannot skip the rest of the tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XGrowthLoopScheduler {

    private final XRitualPublisher ritualPublisher;
    private final XInboundService inboundService;
    private final XOutboundService outboundService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void tick() {
        Instant now = Instant.now();
        try {
            ritualPublisher.runIfDue(now);
        } catch (Exception e) {
            log.warn("[x-growth] ritual tick failed: {}", e.getMessage());
        }
        try {
            inboundService.run(now);
        } catch (Exception e) {
            log.warn("[x-growth] inbound tick failed: {}", e.getMessage());
        }
        try {
            outboundService.run(now);
        } catch (Exception e) {
            log.warn("[x-growth] outbound tick failed: {}", e.getMessage());
        }
    }
}
