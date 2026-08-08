package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Daily marketing auto-publish caps (shared post pool).
 *
 * <p>{@code dailyTextCap} is the shared KST-day ceiling for marketed posts.
 * S4: one COMMITTED story = one pool slot (multi-platform jobs do not add extra).
 * Videos are a subset hard-capped by {@code dailyVideoCap}; remaining slots are text.
 */
@Service
@RequiredArgsConstructor
public class MarketingQuotaService {

    public static final String KEY_TEXT_CAP = "marketing.daily_text_cap";
    public static final String KEY_VIDEO_CAP = "marketing.daily_video_cap";
    public static final int DEFAULT_TEXT_CAP = 6;
    public static final int DEFAULT_VIDEO_CAP = 3;
    public static final int MIN_TEXT_CAP = 1;
    public static final int MAX_TEXT_CAP = 50;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SystemSettingRepository systemSettingRepository;
    private final MarketingJobRepository marketingJobRepository;

    public record Caps(int dailyTextCap, int dailyVideoCap) {}

    public record QuotaStatus(
        int dailyTextCap,
        int dailyVideoCap,
        long videosToday,
        long textsToday,
        long remainingPool
    ) {}

    public Caps getCaps() {
        return new Caps(readInt(KEY_TEXT_CAP, DEFAULT_TEXT_CAP), readInt(KEY_VIDEO_CAP, DEFAULT_VIDEO_CAP));
    }

    public Instant startOfTodayKst() {
        return LocalDate.now(KST).atStartOfDay(KST).toInstant();
    }

    public QuotaStatus getStatus() {
        Caps caps = getCaps();
        Instant start = startOfTodayKst();
        long videosToday = marketingJobRepository.countVideoJobsCreatedSince(start);
        long marketedToday = marketingJobRepository.countDistinctMarketedPostsSince(start);
        // Prefer story-based texts (= marketed − video). Fall back to text-only job count
        // when marketed < videos (shouldn't happen) so remaining stays non-negative.
        long textsToday = Math.max(0, marketedToday - videosToday);
        long remainingPool = Math.max(0, caps.dailyTextCap() - marketedToday);
        return new QuotaStatus(
            caps.dailyTextCap(),
            caps.dailyVideoCap(),
            videosToday,
            textsToday,
            remainingPool
        );
    }

    @Transactional
    public QuotaStatus updateCaps(int dailyTextCap, int dailyVideoCap, String updatedBy) {
        validate(dailyTextCap, dailyVideoCap);
        Instant now = Instant.now();
        saveSetting(KEY_TEXT_CAP, String.valueOf(dailyTextCap), now, updatedBy);
        saveSetting(KEY_VIDEO_CAP, String.valueOf(dailyVideoCap), now, updatedBy);
        return getStatus();
    }

    static void validate(int dailyTextCap, int dailyVideoCap) {
        if (dailyTextCap < MIN_TEXT_CAP || dailyTextCap > MAX_TEXT_CAP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "dailyTextCap must be between " + MIN_TEXT_CAP + " and " + MAX_TEXT_CAP);
        }
        if (dailyVideoCap < 0 || dailyVideoCap > dailyTextCap) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "dailyVideoCap must be between 0 and dailyTextCap");
        }
    }

    private int readInt(String key, int defaultValue) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                try {
                    return Integer.parseInt(raw.trim());
                } catch (Exception e) {
                    return defaultValue;
                }
            })
            .orElse(defaultValue);
    }

    private void saveSetting(String key, String value, Instant now, String updatedBy) {
        SystemSetting setting = systemSettingRepository.findById(key).orElseGet(() ->
            SystemSetting.builder().settingKey(key).build());
        setting.setSettingValue(value);
        setting.setUpdatedAt(now);
        setting.setUpdatedBy(updatedBy);
        systemSettingRepository.save(setting);
    }
}
