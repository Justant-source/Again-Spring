package com.againspring.aiuser.orchestrator.service.threadplan;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class QuietHoursTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    void isQuiet_rejectsAuthorSlotsBetween2And6Kst() {
        LocalDate day = LocalDate.of(2026, 8, 4);
        assertThat(QuietHours.isQuiet(at(day, 1, 59))).isFalse();
        assertThat(QuietHours.isQuiet(at(day, 2, 0))).isTrue();
        assertThat(QuietHours.isQuiet(at(day, 3, 30))).isTrue();
        assertThat(QuietHours.isQuiet(at(day, 5, 59))).isTrue();
        assertThat(QuietHours.isQuiet(at(day, 6, 0))).isFalse();
        assertThat(QuietHours.isQuiet(at(day, 6, 5))).isFalse();
        assertThat(QuietHours.isQuiet(at(day, 22, 0))).isFalse();
    }

    @Test
    void enforceAuthorSlot_bumpsQuietToResume() {
        LocalDate day = LocalDate.of(2026, 8, 4);
        Instant quiet = at(day, 3, 15);
        Instant enforced = QuietHours.enforceAuthorSlot(quiet);
        assertThat(QuietHours.isQuiet(enforced)).isFalse();
        ZonedDateTime local = enforced.atZone(KST);
        assertThat(local.getHour()).isEqualTo(6);
        assertThat(local.getMinute()).isEqualTo(5);
        assertThat(local.toLocalDate()).isEqualTo(day);
    }

    @Test
    void enforceAuthorSlot_leavesNonQuietUnchanged() {
        Instant afternoon = at(LocalDate.of(2026, 8, 4), 14, 20);
        assertThat(QuietHours.enforceAuthorSlot(afternoon)).isEqualTo(afternoon);
    }

    @Test
    void nextResumeAfter_duringQuiet_returnsToday0605() {
        Instant quiet = at(LocalDate.of(2026, 8, 4), 4, 0);
        Instant resume = QuietHours.nextResumeAfter(quiet);
        ZonedDateTime local = resume.atZone(KST);
        assertThat(local.getHour()).isEqualTo(6);
        assertThat(local.getMinute()).isEqualTo(5);
        assertThat(local.toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 4));
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(KST).toInstant();
    }
}
