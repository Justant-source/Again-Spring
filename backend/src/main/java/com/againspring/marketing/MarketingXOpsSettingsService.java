package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * X account operating knobs (ritual posts, inbound replies, outbound replies).
 * Stored in {@code system_setting}. Runtime posting is gated by the three
 * enabled flags — all default <b>false</b> until the growth-loop publishers exist.
 */
@Service
@RequiredArgsConstructor
public class MarketingXOpsSettingsService {

    public static final String KEY_MORNING_TIME = "marketing.x.morning_time";
    public static final String KEY_NIGHT_TIME = "marketing.x.night_time";
    public static final String KEY_STORY_SCOOPS_PER_DAY = "marketing.x.story_scoops_per_day";
    public static final String KEY_OUTBOUND_DAILY_CAP = "marketing.x.outbound_daily_cap";
    public static final String KEY_INBOUND_DAILY_CAP = "marketing.x.inbound_daily_cap";
    public static final String KEY_INBOUND_PER_POST_CAP = "marketing.x.inbound_per_post_cap";
    public static final String KEY_HOT_MIN_REPLIES = "marketing.x.hot_min_replies";
    public static final String KEY_HOT_MAX_AGE_HOURS = "marketing.x.hot_max_age_hours";
    public static final String KEY_RITUAL_ENABLED = "marketing.x.ritual_enabled";
    public static final String KEY_INBOUND_ENABLED = "marketing.x.inbound_enabled";
    public static final String KEY_OUTBOUND_ENABLED = "marketing.x.outbound_enabled";
    public static final String KEY_PERSONA_LEARNING_ENABLED = "marketing.x.persona_learning_enabled";
    public static final String KEY_PERSONA_LEARN_AT = "marketing.x.persona_learn_at";

    public static final String DEFAULT_MORNING_TIME = "07:30";
    public static final String DEFAULT_NIGHT_TIME = "22:00";
    public static final int DEFAULT_STORY_SCOOPS_PER_DAY = 2;
    public static final int DEFAULT_OUTBOUND_DAILY_CAP = 20;
    public static final int DEFAULT_INBOUND_DAILY_CAP = 40;
    public static final int DEFAULT_INBOUND_PER_POST_CAP = 12;
    public static final int DEFAULT_HOT_MIN_REPLIES = 3;
    public static final int DEFAULT_HOT_MAX_AGE_HOURS = 6;
    public static final String DEFAULT_PERSONA_LEARN_AT = "04:30";

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final SystemSettingRepository systemSettingRepository;

    public record XOpsSettings(
        String morningTime,
        String nightTime,
        int storyScoopsPerDay,
        int outboundDailyCap,
        int inboundDailyCap,
        int inboundPerPostCap,
        int hotMinReplies,
        int hotMaxAgeHours,
        boolean ritualEnabled,
        boolean inboundEnabled,
        boolean outboundEnabled,
        boolean personaLearningEnabled,
        String personaLearnAt
    ) {}

    @Transactional(readOnly = true)
    public XOpsSettings get() {
        return new XOpsSettings(
            readTime(KEY_MORNING_TIME, DEFAULT_MORNING_TIME),
            readTime(KEY_NIGHT_TIME, DEFAULT_NIGHT_TIME),
            readInt(KEY_STORY_SCOOPS_PER_DAY, DEFAULT_STORY_SCOOPS_PER_DAY, 0, 10),
            readInt(KEY_OUTBOUND_DAILY_CAP, DEFAULT_OUTBOUND_DAILY_CAP, 0, 100),
            readInt(KEY_INBOUND_DAILY_CAP, DEFAULT_INBOUND_DAILY_CAP, 0, 200),
            readInt(KEY_INBOUND_PER_POST_CAP, DEFAULT_INBOUND_PER_POST_CAP, 0, 50),
            readInt(KEY_HOT_MIN_REPLIES, DEFAULT_HOT_MIN_REPLIES, 0, 50),
            readInt(KEY_HOT_MAX_AGE_HOURS, DEFAULT_HOT_MAX_AGE_HOURS, 1, 48),
            readBool(KEY_RITUAL_ENABLED, false),
            readBool(KEY_INBOUND_ENABLED, false),
            readBool(KEY_OUTBOUND_ENABLED, false),
            readBool(KEY_PERSONA_LEARNING_ENABLED, true),
            readTime(KEY_PERSONA_LEARN_AT, DEFAULT_PERSONA_LEARN_AT)
        );
    }

    @Transactional
    public XOpsSettings update(XOpsSettings incoming, String updatedBy) {
        validate(incoming);
        Instant now = Instant.now();
        String by = updatedBy != null && !updatedBy.isBlank() ? updatedBy : "admin";
        saveSetting(KEY_MORNING_TIME, normalizeHhMm(incoming.morningTime()), now, by);
        saveSetting(KEY_NIGHT_TIME, normalizeHhMm(incoming.nightTime()), now, by);
        saveSetting(KEY_STORY_SCOOPS_PER_DAY, String.valueOf(incoming.storyScoopsPerDay()), now, by);
        saveSetting(KEY_OUTBOUND_DAILY_CAP, String.valueOf(incoming.outboundDailyCap()), now, by);
        saveSetting(KEY_INBOUND_DAILY_CAP, String.valueOf(incoming.inboundDailyCap()), now, by);
        saveSetting(KEY_INBOUND_PER_POST_CAP, String.valueOf(incoming.inboundPerPostCap()), now, by);
        saveSetting(KEY_HOT_MIN_REPLIES, String.valueOf(incoming.hotMinReplies()), now, by);
        saveSetting(KEY_HOT_MAX_AGE_HOURS, String.valueOf(incoming.hotMaxAgeHours()), now, by);
        saveSetting(KEY_RITUAL_ENABLED, String.valueOf(incoming.ritualEnabled()), now, by);
        saveSetting(KEY_INBOUND_ENABLED, String.valueOf(incoming.inboundEnabled()), now, by);
        saveSetting(KEY_OUTBOUND_ENABLED, String.valueOf(incoming.outboundEnabled()), now, by);
        saveSetting(KEY_PERSONA_LEARNING_ENABLED, String.valueOf(incoming.personaLearningEnabled()), now, by);
        saveSetting(KEY_PERSONA_LEARN_AT, normalizeHhMm(incoming.personaLearnAt()), now, by);
        return get();
    }

    static void validate(XOpsSettings s) {
        requireHhMm("morningTime", s.morningTime());
        requireHhMm("nightTime", s.nightTime());
        requireRange("storyScoopsPerDay", s.storyScoopsPerDay(), 0, 10);
        requireRange("outboundDailyCap", s.outboundDailyCap(), 0, 100);
        requireRange("inboundDailyCap", s.inboundDailyCap(), 0, 200);
        requireRange("inboundPerPostCap", s.inboundPerPostCap(), 0, 50);
        requireRange("hotMinReplies", s.hotMinReplies(), 0, 50);
        requireRange("hotMaxAgeHours", s.hotMaxAgeHours(), 1, 48);
        requireHhMm("personaLearnAt", s.personaLearnAt());
    }

    private static String normalizeHhMm(String raw) {
        String t = raw.trim();
        if (t.length() >= 8 && t.charAt(2) == ':' && t.charAt(5) == ':') {
            t = t.substring(0, 5);
        }
        return t;
    }

    private static void requireHhMm(String name, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required (HH:mm)");
        }
        try {
            LocalTime.parse(normalizeHhMm(raw), HH_MM);
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                name + " must be HH:mm (24h), got: " + raw);
        }
    }

    private static void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                name + " must be between " + min + " and " + max);
        }
    }

    private String readTime(String key, String defaultValue) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .filter(v -> v != null && !v.isBlank())
            .map(String::trim)
            .map(v -> {
                try {
                    return normalizeHhMm(v);
                } catch (IndexOutOfBoundsException e) {
                    return v;
                }
            })
            .filter(v -> {
                try {
                    LocalTime.parse(v, HH_MM);
                    return true;
                } catch (DateTimeParseException e) {
                    return false;
                }
            })
            .orElse(defaultValue);
    }

    private int readInt(String key, int defaultValue, int min, int max) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                try {
                    int parsed = Integer.parseInt(raw.trim());
                    if (parsed < min || parsed > max) {
                        return defaultValue;
                    }
                    return parsed;
                } catch (Exception e) {
                    return defaultValue;
                }
            })
            .orElse(defaultValue);
    }

    private boolean readBool(String key, boolean defaultValue) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                if (raw == null) {
                    return defaultValue;
                }
                String t = raw.trim();
                if ("true".equalsIgnoreCase(t) || "1".equals(t) || "on".equalsIgnoreCase(t)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(t) || "0".equals(t) || "off".equalsIgnoreCase(t)) {
                    return false;
                }
                return defaultValue;
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
