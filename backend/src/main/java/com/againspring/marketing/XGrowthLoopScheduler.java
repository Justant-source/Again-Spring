package com.againspring.marketing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Ritual / inbound / original stay on a one-minute KST tick (inbound has a
 * 30-minute reply window; original only fires at 12:30/19:30). Outbound
 * candidate fetches hit X's session APIs, so they run only every 30 minutes
 * during 08:00–22:30 KST. Successful replies per tick come from
 * {@code marketing.x.outbound_per_tick}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XGrowthLoopScheduler {

    private final XRitualPublisher ritualPublisher;
    private final XInboundService inboundService;
    private final XOutboundService outboundService;
    private final XOriginalPostService originalPostService;

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
            originalPostService.runIfDue(now);
        } catch (Exception e) {
            log.warn("[x-growth] original tick failed: {}", e.getMessage());
        }
    }

    /** 08:00, 08:30, … 22:00, 22:30 KST. */
    @Scheduled(cron = "0 0,30 8-22 * * *", zone = "Asia/Seoul")
    public void outboundTick() {
        Instant now = Instant.now();
        try {
            outboundService.run(now);
        } catch (Exception e) {
            log.warn("[x-growth] outbound tick failed: {}", e.getMessage());
        }
    }
}
