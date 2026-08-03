package com.againspring.aiuser.orchestrator.service.threadplan;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * KST quiet-hour gate for <strong>author</strong> publish slots (hard ban 02:00–06:00).
 * Partner answer slots are intentionally not gated here — they may land in this window.
 */
public final class QuietHours {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** Inclusive start hour (KST). */
    public static final int START_HOUR_INCLUSIVE = 2;
    /** Exclusive end hour (KST). */
    public static final int END_HOUR_EXCLUSIVE = 6;
    /** Minutes past quiet end when deferred publishes resume (matches {@code ThreadPlanPublisher}). */
    public static final int RESUME_MINUTE = 5;

    private QuietHours() { }

    public static boolean isQuiet(Instant instant) {
        return isQuiet(instant, START_HOUR_INCLUSIVE, END_HOUR_EXCLUSIVE);
    }

    public static boolean isQuiet(Instant instant, int startInclusive, int endExclusive) {
        if (instant == null) return false;
        int hour = instant.atZone(KST).getHour();
        return hour >= startInclusive && hour < endExclusive;
    }

    public static boolean isQuietNow() {
        return isQuiet(Instant.now());
    }

    /**
     * If {@code candidate} falls in quiet hours, bump to quiet-end + {@link #RESUME_MINUTE}
     * on the same KST calendar day (or the next day when the quiet window already started
     * after midnight and the bump would still be quiet — not applicable for 02–06→06:05).
     * Non-quiet candidates are returned unchanged.
     */
    public static Instant enforceAuthorSlot(Instant candidate) {
        return enforceAuthorSlot(candidate, START_HOUR_INCLUSIVE, END_HOUR_EXCLUSIVE);
    }

    public static Instant enforceAuthorSlot(Instant candidate, int startInclusive, int endExclusive) {
        if (candidate == null) return null;
        if (!isQuiet(candidate, startInclusive, endExclusive)) return candidate;
        ZonedDateTime local = candidate.atZone(KST);
        ZonedDateTime resume = local.toLocalDate()
                .atTime(endExclusive, RESUME_MINUTE)
                .atZone(KST);
        // If somehow still quiet (misconfigured end), push one more day.
        if (isQuiet(resume.toInstant(), startInclusive, endExclusive)) {
            resume = resume.plusDays(1);
        }
        return resume.toInstant();
    }

    /**
     * Next quiet-end resume instant for a publish deferred during quiet hours
     * (same-day 06:05 KST, matching {@code ThreadPlanPublisher#nextActiveKst}).
     */
    public static Instant nextResumeAfter(Instant from) {
        ZonedDateTime local = (from == null ? Instant.now() : from).atZone(KST);
        ZonedDateTime resume = local.withHour(END_HOUR_EXCLUSIVE)
                .withMinute(RESUME_MINUTE)
                .withSecond(0)
                .withNano(0);
        if (!resume.toInstant().isAfter(local.toInstant())) {
            resume = resume.plusDays(1);
        }
        return resume.toInstant();
    }
}
