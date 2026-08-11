package com.againspring.marketing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules platform stats collect (daily) and weekly report auto_adjust (Mon 09:00 KST).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingPlatformStatsScheduler {

    private final MarketingPlatformStatsCollector collector;
    private final MarketingScoreAutoAdjustService autoAdjustService;

    /** Daily 06:30 KST — best-effort ASM collect → AS marketing_publication_stats. */
    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Seoul")
    public void collectDaily() {
        try {
            var summary = collector.collectScheduled();
            log.info("marketing platform stats collect: {}", summary);
        } catch (Exception e) {
            log.warn("marketing platform stats collect failed: {}", e.getMessage());
        }
    }

    /** Weekly Monday 09:00 KST — nudge weights only when auto_adjust=true. */
    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")
    public void weeklyAutoAdjust() {
        try {
            var result = autoAdjustService.runWeeklyAdjust();
            log.info("marketing.score.auto_adjust weekly: enabled={} applied={} reason={}",
                result.enabled(), result.applied(), result.reason());
        } catch (Exception e) {
            log.warn("marketing.score.auto_adjust weekly failed: {}", e.getMessage());
        }
    }
}
