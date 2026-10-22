package com.againspring.marketing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spreads X outbound replies and follow-backs over the day like a person scrolling
 * and replying, instead of fixed-cron batches. Every minute it checks whether the next
 * action is due; each action is ONE reply (or 1–3 follows), after which the next due time
 * is drawn from the remaining budget, the hour-of-day activity weight, log-normal-ish
 * jitter, and an occasional "away from phone" break.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XHumanPacer {

    /** KST hour → relative activity. 0 means asleep (no actions). */
    static final double[] HOUR_WEIGHT = {
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.3,
        0.7, 1.0, 1.0, 1.2, 1.7, 1.3, 1.0, 1.0,
        1.0, 1.2, 1.5, 1.6, 1.8, 1.8, 1.4, 0.7
    };

    private final XOutboundService outboundService;
    private final XFollowBackService followBackService;
    private final XDailyQuota dailyQuota;

    private Instant nextReplyAt;
    private Instant nextFollowAt;

    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void tick() {
        Instant now = Instant.now();
        if (weight(now) <= 0) {
            return;
        }
        try {
            if (nextReplyAt == null) {
                nextReplyAt = now.plusSeconds(ThreadLocalRandom.current().nextInt(30, 600));
            }
            if (!now.isBefore(nextReplyAt)) {
                int target = dailyQuota.targetFor(now);
                int done = outboundService.countPostedToday(now);
                boolean posted = outboundService.runOne(now, target);
                int remaining = Math.max(0, target - done - (posted ? 1 : 0));
                nextReplyAt = now.plus(posted
                    ? nextReplyGap(remaining, now, ThreadLocalRandom.current())
                    : Duration.ofMinutes(4 + ThreadLocalRandom.current().nextInt(0, 8)));
                log.info("[x-pacer] reply posted={} target={} done={} next={}", posted, target,
                    done + (posted ? 1 : 0), nextReplyAt);
            }
        } catch (Exception e) {
            log.warn("[x-pacer] reply failed: {}", e.getMessage());
            nextReplyAt = now.plus(Duration.ofMinutes(10));
        }
        try {
            if (nextFollowAt == null) {
                nextFollowAt = now.plusSeconds(ThreadLocalRandom.current().nextInt(300, 1500));
            }
            if (!now.isBefore(nextFollowAt)) {
                followBackService.run(now);
                nextFollowAt = now.plus(Duration.ofMinutes(
                    ThreadLocalRandom.current().nextInt(20, 56)));
            }
        } catch (Exception e) {
            log.warn("[x-pacer] follow-back failed: {}", e.getMessage());
            nextFollowAt = now.plus(Duration.ofMinutes(30));
        }
    }

    static double weight(Instant now) {
        return HOUR_WEIGHT[now.atZone(XDailyQuota.KST).getHour()];
    }

    /**
     * Gap until the next reply. Mean = (time left weighted by activity) spread over the
     * remaining replies, scaled by this hour's weight; then jittered (0.4–2.2×) and,
     * with 8% chance, stretched by a 15–45 min break.
     */
    static Duration nextReplyGap(int remaining, Instant now, Random rng) {
        if (remaining <= 0) {
            return Duration.ofMinutes(30);
        }
        ZonedDateTime z = now.atZone(XDailyQuota.KST);
        int h = z.getHour();
        double minutesLeftInHour = 60 - z.getMinute();
        double weightLeft = HOUR_WEIGHT[h] * minutesLeftInHour / 60.0;
        for (int i = h + 1; i < 24; i++) {
            weightLeft += HOUR_WEIGHT[i];
        }
        double w = Math.max(HOUR_WEIGHT[h], 0.1);
        double repliesPerHourNow = weightLeft <= 0 ? remaining : remaining * w / weightLeft;
        double meanMinutes = 60.0 / Math.max(repliesPerHourNow, 0.2);
        double jitter = Math.min(2.2, Math.max(0.4, Math.exp(rng.nextGaussian() * 0.45)));
        double minutes = meanMinutes * jitter;
        if (rng.nextDouble() < 0.08) {
            minutes += 15 + rng.nextInt(31);
        }
        double seconds = Math.min(minutes * 60, 90 * 60);
        return Duration.ofSeconds((long) Math.max(60, seconds));
    }
}
