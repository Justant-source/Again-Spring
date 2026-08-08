package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Configurable popularity score weights for marketing auto-publish ranking.
 *
 * <p>Score = {@code wViews * views + wComments * top_level_comments + wVotes * votes};
 * tie-break {@code created_at DESC}. Stored in {@code system_setting}.
 */
@Service
@RequiredArgsConstructor
public class MarketingScoreWeightService {

    public static final String KEY_WEIGHT_VIEWS = "marketing.score.weight_views";
    public static final String KEY_WEIGHT_COMMENTS = "marketing.score.weight_comments";
    public static final String KEY_WEIGHT_VOTES = "marketing.score.weight_votes";

    public static final double DEFAULT_WEIGHT_VIEWS = 0.1;
    public static final double DEFAULT_WEIGHT_COMMENTS = 1.0;
    public static final double DEFAULT_WEIGHT_VOTES = 0.5;

    public static final double MIN_WEIGHT = 0.0;
    public static final double MAX_WEIGHT = 100.0;

    private final SystemSettingRepository systemSettingRepository;

    public record Weights(double weightViews, double weightComments, double weightVotes) {}

    public Weights getWeights() {
        return new Weights(
            readDouble(KEY_WEIGHT_VIEWS, DEFAULT_WEIGHT_VIEWS),
            readDouble(KEY_WEIGHT_COMMENTS, DEFAULT_WEIGHT_COMMENTS),
            readDouble(KEY_WEIGHT_VOTES, DEFAULT_WEIGHT_VOTES)
        );
    }

    @Transactional
    public Weights updateWeights(double weightViews, double weightComments, double weightVotes,
                                 String updatedBy) {
        validate(weightViews, weightComments, weightVotes);
        Instant now = Instant.now();
        saveSetting(KEY_WEIGHT_VIEWS, formatWeight(weightViews), now, updatedBy);
        saveSetting(KEY_WEIGHT_COMMENTS, formatWeight(weightComments), now, updatedBy);
        saveSetting(KEY_WEIGHT_VOTES, formatWeight(weightVotes), now, updatedBy);
        return getWeights();
    }

    static void validate(double weightViews, double weightComments, double weightVotes) {
        requireInRange("weightViews", weightViews);
        requireInRange("weightComments", weightComments);
        requireInRange("weightVotes", weightVotes);
    }

    private static void requireInRange(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)
            || value < MIN_WEIGHT || value > MAX_WEIGHT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                name + " must be between " + MIN_WEIGHT + " and " + MAX_WEIGHT);
        }
    }

    private static String formatWeight(double value) {
        // Avoid scientific notation; trim trailing zeros for clean storage.
        return Double.toString(value);
    }

    private double readDouble(String key, double defaultValue) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                try {
                    double parsed = Double.parseDouble(raw.trim());
                    if (Double.isNaN(parsed) || Double.isInfinite(parsed)
                        || parsed < MIN_WEIGHT || parsed > MAX_WEIGHT) {
                        return defaultValue;
                    }
                    return parsed;
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
