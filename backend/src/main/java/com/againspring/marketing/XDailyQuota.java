package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Per-day reply budget with a human-like spread. A KST weekday draws its target from
 * {@code marketing.x.outbound_weekday_range} (default 200–250), a Saturday, Sunday or
 * Korean public holiday from {@code outbound_offday_range} (default 230–300). The draw is
 * seeded by the date, so it is stable across restarts and across the day.
 * {@code marketing.x.holidays_extra} (comma-separated yyyy-MM-dd) adds holidays the
 * built-in list does not know.
 */
@Component
@RequiredArgsConstructor
public class XDailyQuota {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final String KEY_WEEKDAY_RANGE = "marketing.x.outbound_weekday_range";
    public static final String KEY_OFFDAY_RANGE = "marketing.x.outbound_offday_range";
    public static final String KEY_HOLIDAYS_EXTRA = "marketing.x.holidays_extra";

    /** Korean public holidays incl. substitute days. Extend via {@code holidays_extra}. */
    static final Set<LocalDate> BUILTIN_HOLIDAYS = Set.of(
        LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9),
        LocalDate.of(2026, 12, 25),
        LocalDate.of(2027, 1, 1),
        LocalDate.of(2027, 2, 6), LocalDate.of(2027, 2, 7), LocalDate.of(2027, 2, 8),
        LocalDate.of(2027, 2, 9),
        LocalDate.of(2027, 3, 1), LocalDate.of(2027, 5, 5), LocalDate.of(2027, 5, 13));

    private final SystemSettingRepository systemSettingRepository;

    public boolean isOffDay(LocalDate day) {
        DayOfWeek dow = day.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
            || BUILTIN_HOLIDAYS.contains(day) || extraHolidays().contains(day);
    }

    public int targetFor(LocalDate day) {
        int[] range = isOffDay(day)
            ? readRange(KEY_OFFDAY_RANGE, 230, 300)
            : readRange(KEY_WEEKDAY_RANGE, 200, 250);
        return pick(day, range[0], range[1]);
    }

    public int targetFor(Instant now) {
        return targetFor(now.atZone(KST).toLocalDate());
    }

    static int pick(LocalDate day, int lo, int hi) {
        if (hi <= lo) {
            return lo;
        }
        long seed = day.toEpochDay() * 0x9E3779B97F4A7C15L + 0x1234567L;
        return lo + new Random(seed).nextInt(hi - lo + 1);
    }

    private int[] readRange(String key, int defLo, int defHi) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                try {
                    String[] p = raw.trim().split("-");
                    int lo = Integer.parseInt(p[0].trim());
                    int hi = Integer.parseInt(p[1].trim());
                    return lo >= 0 && hi >= lo && hi <= 300 ? new int[] {lo, hi} : null;
                } catch (Exception e) {
                    return null;
                }
            })
            .orElse(new int[] {defLo, defHi});
    }

    private Set<LocalDate> extraHolidays() {
        Set<LocalDate> out = new HashSet<>();
        systemSettingRepository.findById(KEY_HOLIDAYS_EXTRA)
            .map(SystemSetting::getSettingValue)
            .ifPresent(raw -> {
                for (String t : raw.split(",")) {
                    try {
                        out.add(LocalDate.parse(t.trim()));
                    } catch (Exception ignored) {
                        // skip malformed entry
                    }
                }
            });
        return out;
    }
}
