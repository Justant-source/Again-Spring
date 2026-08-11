package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.enums.PostCategory;
import com.againspring.repository.ai.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Phase 3 theme boost storage (plan §4.3): emotion×category multipliers per platform.
 *
 * <p>Keys: {@code marketing.theme.boost.{platform}.{emotion}.{category}} (default 1.0).
 * Shadow mode defaults on; apply is confirm + weekly cooldown + clamp/delta caps.
 */
@Service
@RequiredArgsConstructor
public class MarketingThemeBoostService {

    public static final String KEY_BOOST_PREFIX = "marketing.theme.boost.";
    public static final String KEY_SHADOW = "marketing.theme.shadow";
    public static final String KEY_LAST_APPLY_AT = "marketing.theme.last_apply_at";
    public static final String KEY_MIN_N = "marketing.theme.min_n";
    public static final String KEY_BOOST_MIN = "marketing.theme.boost_min";
    public static final String KEY_BOOST_MAX = "marketing.theme.boost_max";
    public static final String KEY_DELTA_CAP = "marketing.theme.delta_cap";

    public static final double DEFAULT_BOOST = 1.0;
    public static final boolean DEFAULT_SHADOW = true;
    public static final int DEFAULT_MIN_N = 3;
    public static final double DEFAULT_BOOST_MIN = 0.7;
    public static final double DEFAULT_BOOST_MAX = 1.3;
    public static final double DEFAULT_DELTA_CAP = 0.05;

    public static final long APPLY_COOLDOWN_DAYS = 7;

    public static final List<String> EMOTIONS = List.of(
        "shock", "anger", "tension", "sad", "hype");

    public static final Set<String> EMOTION_SET = Set.copyOf(EMOTIONS);

    public static final List<String> CATEGORIES = List.of(
        PostCategory.COUPLE.name(),
        PostCategory.MARRIED.name(),
        PostCategory.FRIEND.name(),
        PostCategory.FAMILY.name(),
        PostCategory.WORK.name(),
        PostCategory.OTHER.name());

    public static final Set<String> CATEGORY_SET = Set.copyOf(CATEGORIES);

    private final SystemSettingRepository systemSettingRepository;

    /** One cell change for {@link #applyChanges}. */
    public record ThemeBoostChange(String emotion, String category, double boost) {}

    /** Result of a successful apply (mirrors admin apply response shape). */
    public record ApplyResult(
        int applied,
        Map<String, Map<String, Double>> before,
        Map<String, Map<String, Double>> after,
        Instant cooldownUntil
    ) {}

    /**
     * Stored boost for a cell. Missing / unknown emotion or category → {@code 1.0}.
     */
    public double getBoost(String platform, String emotion, String category) {
        String plat = MarketingPopularityScorer.normalizePlatform(platform);
        String emo = normalizeEmotion(emotion);
        String cat = normalizeCategory(category);
        if (!MarketingPopularityScorer.isRankedPlatform(plat)
            || emo == null
            || cat == null) {
            return DEFAULT_BOOST;
        }
        return readBoost(settingKey(plat, emo, cat));
    }

    /**
     * Full emotion → category → boost matrix for a ranked platform (defaults filled).
     */
    public Map<String, Map<String, Double>> getMatrix(String platform) {
        String plat = requireRankedPlatform(platform);
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        for (String emotion : EMOTIONS) {
            Map<String, Double> row = new LinkedHashMap<>();
            for (String category : CATEGORIES) {
                row.put(category, readBoost(settingKey(plat, emotion, category)));
            }
            out.put(emotion, row);
        }
        return out;
    }

    public boolean isShadow() {
        return systemSettingRepository.findById(KEY_SHADOW)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                if (raw == null) {
                    return DEFAULT_SHADOW;
                }
                String v = raw.trim().toLowerCase(Locale.ROOT);
                if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "off".equals(v)) {
                    return false;
                }
                if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v)) {
                    return true;
                }
                return DEFAULT_SHADOW;
            })
            .orElse(DEFAULT_SHADOW);
    }

    @Transactional
    public void setShadow(boolean shadow) {
        Instant now = Instant.now();
        saveSetting(KEY_SHADOW, shadow ? "true" : "false", now, "marketing.theme");
    }

    /** True when no prior apply or {@link #APPLY_COOLDOWN_DAYS} have elapsed since last apply. */
    public boolean canApplyNow() {
        Instant until = cooldownUntil();
        return until == null || !Instant.now().isBefore(until);
    }

    /**
     * Instant when the next apply is allowed, or {@code null} if apply is allowed now
     * (no {@code last_apply_at} yet).
     */
    public Instant cooldownUntil() {
        Instant last = readLastApplyAt();
        if (last == null) {
            return null;
        }
        return last.plus(APPLY_COOLDOWN_DAYS, ChronoUnit.DAYS);
    }

    /**
     * Persist confirmed theme boost changes for one platform.
     *
     * <ul>
     *   <li>{@code confirm} must be true</li>
     *   <li>weekly cooldown from {@link #KEY_LAST_APPLY_AT}</li>
     *   <li>each boost clamped to [boost_min, boost_max]</li>
     *   <li>per-cell delta vs current capped at ±delta_cap</li>
     * </ul>
     *
     * @throws IllegalStateException on confirm/cooldown/delta/range violations
     * @throws IllegalArgumentException on unknown platform / emotion / category / empty changes
     */
    @Transactional
    public ApplyResult applyChanges(String platform, List<ThemeBoostChange> changes, boolean confirm) {
        if (!confirm) {
            throw new IllegalStateException("confirm=true is required to apply theme boosts");
        }
        if (!canApplyNow()) {
            Instant until = cooldownUntil();
            throw new IllegalStateException(
                "theme boost apply on cooldown until " + (until != null ? until : "unknown"));
        }
        if (changes == null || changes.isEmpty()) {
            throw new IllegalArgumentException("changes is required");
        }

        String plat = requireRankedPlatform(platform);
        double boostMin = readDouble(KEY_BOOST_MIN, DEFAULT_BOOST_MIN);
        double boostMax = readDouble(KEY_BOOST_MAX, DEFAULT_BOOST_MAX);
        double deltaCap = readDouble(KEY_DELTA_CAP, DEFAULT_DELTA_CAP);

        Map<String, Map<String, Double>> before = getMatrix(plat);
        Instant now = Instant.now();
        int applied = 0;

        for (ThemeBoostChange change : changes) {
            if (change == null) {
                throw new IllegalArgumentException("change entry must not be null");
            }
            String emo = normalizeEmotion(change.emotion());
            String cat = normalizeCategory(change.category());
            if (emo == null) {
                throw new IllegalArgumentException("unknown emotion: " + change.emotion());
            }
            if (cat == null) {
                throw new IllegalArgumentException("unknown category: " + change.category());
            }
            if (Double.isNaN(change.boost()) || Double.isInfinite(change.boost())) {
                throw new IllegalStateException(
                    "boost must be a finite number for " + emo + "/" + cat);
            }

            double current = readBoost(settingKey(plat, emo, cat));
            double clamped = Math.max(boostMin, Math.min(boostMax, change.boost()));
            double delta = clamped - current;
            if (Math.abs(delta) > deltaCap + 1e-9) {
                throw new IllegalStateException(
                    "boost delta for " + emo + "/" + cat + " exceeds ±" + deltaCap
                        + " (current=" + current + ", requested=" + clamped
                        + ", clamped from " + change.boost() + ")");
            }

            saveSetting(settingKey(plat, emo, cat), formatDouble(clamped), now, "marketing.theme");
            applied++;
        }

        saveSetting(KEY_LAST_APPLY_AT, now.toString(), now, "marketing.theme");
        Map<String, Map<String, Double>> after = getMatrix(plat);
        Instant until = now.plus(APPLY_COOLDOWN_DAYS, ChronoUnit.DAYS);
        return new ApplyResult(applied, before, after, until);
    }

    public int getMinN() {
        double v = readDouble(KEY_MIN_N, DEFAULT_MIN_N);
        return (int) Math.round(v);
    }

    public double getBoostMin() {
        return readDouble(KEY_BOOST_MIN, DEFAULT_BOOST_MIN);
    }

    public double getBoostMax() {
        return readDouble(KEY_BOOST_MAX, DEFAULT_BOOST_MAX);
    }

    public double getDeltaCap() {
        return readDouble(KEY_DELTA_CAP, DEFAULT_DELTA_CAP);
    }

    public static String settingKey(String platform, String emotion, String category) {
        return KEY_BOOST_PREFIX + platform + "." + emotion + "." + category;
    }

    static String normalizeEmotion(String emotion) {
        if (emotion == null || emotion.isBlank()) {
            return null;
        }
        String e = emotion.trim().toLowerCase(Locale.ROOT);
        return EMOTION_SET.contains(e) ? e : null;
    }

    static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String c = category.trim().toUpperCase(Locale.ROOT);
        return CATEGORY_SET.contains(c) ? c : null;
    }

    private String requireRankedPlatform(String platform) {
        String plat = MarketingPopularityScorer.normalizePlatform(platform);
        if (!MarketingPopularityScorer.isRankedPlatform(plat)) {
            throw new IllegalArgumentException("Unknown ranked platform: " + platform);
        }
        return plat;
    }

    private Instant readLastApplyAt() {
        return systemSettingRepository.findById(KEY_LAST_APPLY_AT)
            .map(SystemSetting::getSettingValue)
            .flatMap(raw -> {
                if (raw == null || raw.isBlank()) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(Instant.parse(raw.trim()));
                } catch (Exception e) {
                    return Optional.empty();
                }
            })
            .orElse(null);
    }

    private double readBoost(String key) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                try {
                    double parsed = Double.parseDouble(raw.trim());
                    if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                        return DEFAULT_BOOST;
                    }
                    return parsed;
                } catch (Exception e) {
                    return DEFAULT_BOOST;
                }
            })
            .orElse(DEFAULT_BOOST);
    }

    private double readDouble(String key, double defaultValue) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                try {
                    double parsed = Double.parseDouble(raw.trim());
                    if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                        return defaultValue;
                    }
                    return parsed;
                } catch (Exception e) {
                    return defaultValue;
                }
            })
            .orElse(defaultValue);
    }

    private static String formatDouble(double value) {
        return Double.toString(value);
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
