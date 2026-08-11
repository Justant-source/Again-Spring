package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configurable popularity score weights for marketing ranking.
 *
 * <p><b>Phase 2</b>: per-platform weights at
 * {@code marketing.score.weights.{platform}.{hook|vote_skew|comments|votes|views|has_partner}}
 * (defaults = plan §3).
 *
 * <p><b>Deprecated (Phase 1)</b>: flat keys {@code marketing.score.weight_views|comments|votes}
 * remain readable for the waiting-board composite score; prefer platform weights for commit.
 */
@Service
@RequiredArgsConstructor
public class MarketingScoreWeightService {

    /** @deprecated Phase 1 flat weights — board preview only. */
    @Deprecated
    public static final String KEY_WEIGHT_VIEWS = "marketing.score.weight_views";
    /** @deprecated Phase 1 flat weights — board preview only. */
    @Deprecated
    public static final String KEY_WEIGHT_COMMENTS = "marketing.score.weight_comments";
    /** @deprecated Phase 1 flat weights — board preview only. */
    @Deprecated
    public static final String KEY_WEIGHT_VOTES = "marketing.score.weight_votes";

    public static final String KEY_WEIGHTS_PREFIX = "marketing.score.weights.";
    /** When true, weekly job lightly nudges per-platform weights from recent stats. Default false. */
    public static final String KEY_AUTO_ADJUST = "marketing.score.auto_adjust";

    public static final double DEFAULT_WEIGHT_VIEWS = 0.1;
    public static final double DEFAULT_WEIGHT_COMMENTS = 1.0;
    public static final double DEFAULT_WEIGHT_VOTES = 0.5;
    public static final boolean DEFAULT_AUTO_ADJUST = false;

    public static final double MIN_WEIGHT = 0.0;
    public static final double MAX_WEIGHT = 100.0;

    public static final String SIGNAL_HOOK = "hook";
    public static final String SIGNAL_VOTE_SKEW = "vote_skew";
    public static final String SIGNAL_COMMENTS = "comments";
    public static final String SIGNAL_VOTES = "votes";
    public static final String SIGNAL_VIEWS = "views";
    public static final String SIGNAL_HAS_PARTNER = "has_partner";

    public static final java.util.List<String> SIGNAL_KEYS = java.util.List.of(
        SIGNAL_HOOK, SIGNAL_VOTE_SKEW, SIGNAL_COMMENTS, SIGNAL_VOTES, SIGNAL_VIEWS, SIGNAL_HAS_PARTNER);

    private final SystemSettingRepository systemSettingRepository;

    /** @deprecated Phase 1 composite — waiting board only. */
    @Deprecated
    public record Weights(double weightViews, double weightComments, double weightVotes) {}

    public record AllPlatformWeights(
        MarketingPopularityScorer.PlatformWeights xThread,
        MarketingPopularityScorer.PlatformWeights instagramFeed,
        MarketingPopularityScorer.PlatformWeights instagramReels,
        MarketingPopularityScorer.PlatformWeights youtubeShorts
    ) {
        public MarketingPopularityScorer.PlatformWeights forPlatform(String platform) {
            return switch (MarketingPopularityScorer.normalizePlatform(platform)) {
                case MarketingPopularityScorer.PLATFORM_X_THREAD -> xThread;
                case MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED -> instagramFeed;
                case MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS -> instagramReels;
                case MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS -> youtubeShorts;
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown ranked platform: " + platform);
            };
        }
    }

    /** Phase 1 defaults for board composite ranking. */
    public Weights getWeights() {
        return new Weights(
            readDouble(KEY_WEIGHT_VIEWS, DEFAULT_WEIGHT_VIEWS),
            readDouble(KEY_WEIGHT_COMMENTS, DEFAULT_WEIGHT_COMMENTS),
            readDouble(KEY_WEIGHT_VOTES, DEFAULT_WEIGHT_VOTES)
        );
    }

    public boolean isAutoAdjustEnabled() {
        return systemSettingRepository.findById(KEY_AUTO_ADJUST)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                if (raw == null) {
                    return DEFAULT_AUTO_ADJUST;
                }
                String v = raw.trim().toLowerCase();
                return "true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v);
            })
            .orElse(DEFAULT_AUTO_ADJUST);
    }

    @Transactional
    public boolean updateAutoAdjust(boolean enabled, String updatedBy) {
        Instant now = Instant.now();
        saveSetting(KEY_AUTO_ADJUST, enabled ? "true" : "false", now, updatedBy);
        return isAutoAdjustEnabled();
    }

    public AllPlatformWeights getPlatformWeights() {
        return new AllPlatformWeights(
            readPlatform(MarketingPopularityScorer.PLATFORM_X_THREAD, defaultsX()),
            readPlatform(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED, defaultsFeed()),
            readPlatform(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS, defaultsReels()),
            readPlatform(MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS, defaultsShorts())
        );
    }

    public MarketingPopularityScorer.PlatformWeights getWeightsFor(String platform) {
        return getPlatformWeights().forPlatform(platform);
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

    @Transactional
    public AllPlatformWeights updatePlatformWeights(AllPlatformWeights weights, String updatedBy) {
        ObjectsRequire(weights);
        validatePlatform(weights.xThread());
        validatePlatform(weights.instagramFeed());
        validatePlatform(weights.instagramReels());
        validatePlatform(weights.youtubeShorts());
        Instant now = Instant.now();
        persistPlatform(MarketingPopularityScorer.PLATFORM_X_THREAD, weights.xThread(), now, updatedBy);
        persistPlatform(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED, weights.instagramFeed(), now, updatedBy);
        persistPlatform(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS, weights.instagramReels(), now, updatedBy);
        persistPlatform(MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS, weights.youtubeShorts(), now, updatedBy);
        return getPlatformWeights();
    }

    /** Partial update: only provided platforms are written. */
    @Transactional
    public AllPlatformWeights updatePlatformWeightsPartial(
            Map<String, MarketingPopularityScorer.PlatformWeights> byPlatform,
            String updatedBy) {
        if (byPlatform == null || byPlatform.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "platforms is required");
        }
        Instant now = Instant.now();
        for (Map.Entry<String, MarketingPopularityScorer.PlatformWeights> e : byPlatform.entrySet()) {
            String id = MarketingPopularityScorer.normalizePlatform(e.getKey());
            if (!MarketingPopularityScorer.isRankedPlatform(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown platform: " + e.getKey());
            }
            validatePlatform(e.getValue());
            persistPlatform(id, e.getValue(), now, updatedBy);
        }
        return getPlatformWeights();
    }

    static void validate(double weightViews, double weightComments, double weightVotes) {
        requireInRange("weightViews", weightViews);
        requireInRange("weightComments", weightComments);
        requireInRange("weightVotes", weightVotes);
    }

    static void validatePlatform(MarketingPopularityScorer.PlatformWeights w) {
        if (w == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "platform weights required");
        }
        requireInRange("hook", w.hook());
        requireInRange("voteSkew", w.voteSkew());
        requireInRange("comments", w.comments());
        requireInRange("votes", w.votes());
        requireInRange("views", w.views());
        requireInRange("hasPartner", w.hasPartner());
    }

    public static String settingKey(String platform, String signal) {
        return KEY_WEIGHTS_PREFIX + platform + "." + signal;
    }

    /** Plan §3 defaults. */
    public static MarketingPopularityScorer.PlatformWeights defaultsReels() {
        return new MarketingPopularityScorer.PlatformWeights(2.0, 1.5, 1.2, 0.8, 0.3, 1.0);
    }

    public static MarketingPopularityScorer.PlatformWeights defaultsShorts() {
        return new MarketingPopularityScorer.PlatformWeights(1.2, 1.8, 1.0, 1.0, 0.5, 0.8);
    }

    public static MarketingPopularityScorer.PlatformWeights defaultsX() {
        return new MarketingPopularityScorer.PlatformWeights(1.0, 1.0, 2.0, 0.8, 0.4, 1.5);
    }

    public static MarketingPopularityScorer.PlatformWeights defaultsFeed() {
        return new MarketingPopularityScorer.PlatformWeights(1.5, 1.2, 1.0, 0.8, 0.3, 1.2);
    }

    private MarketingPopularityScorer.PlatformWeights readPlatform(
            String platform, MarketingPopularityScorer.PlatformWeights defaults) {
        return new MarketingPopularityScorer.PlatformWeights(
            readDouble(settingKey(platform, SIGNAL_HOOK), defaults.hook()),
            readDouble(settingKey(platform, SIGNAL_VOTE_SKEW), defaults.voteSkew()),
            readDouble(settingKey(platform, SIGNAL_COMMENTS), defaults.comments()),
            readDouble(settingKey(platform, SIGNAL_VOTES), defaults.votes()),
            readDouble(settingKey(platform, SIGNAL_VIEWS), defaults.views()),
            readDouble(settingKey(platform, SIGNAL_HAS_PARTNER), defaults.hasPartner())
        );
    }

    private void persistPlatform(
            String platform,
            MarketingPopularityScorer.PlatformWeights w,
            Instant now,
            String updatedBy) {
        saveSetting(settingKey(platform, SIGNAL_HOOK), formatWeight(w.hook()), now, updatedBy);
        saveSetting(settingKey(platform, SIGNAL_VOTE_SKEW), formatWeight(w.voteSkew()), now, updatedBy);
        saveSetting(settingKey(platform, SIGNAL_COMMENTS), formatWeight(w.comments()), now, updatedBy);
        saveSetting(settingKey(platform, SIGNAL_VOTES), formatWeight(w.votes()), now, updatedBy);
        saveSetting(settingKey(platform, SIGNAL_VIEWS), formatWeight(w.views()), now, updatedBy);
        saveSetting(settingKey(platform, SIGNAL_HAS_PARTNER), formatWeight(w.hasPartner()), now, updatedBy);
    }

    private static void ObjectsRequire(AllPlatformWeights weights) {
        if (weights == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weights is required");
        }
    }

    private static void requireInRange(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)
            || value < MIN_WEIGHT || value > MAX_WEIGHT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                name + " must be between " + MIN_WEIGHT + " and " + MAX_WEIGHT);
        }
    }

    private static String formatWeight(double value) {
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

    /** Admin-friendly nested map platform → signal → weight. */
    public Map<String, Map<String, Double>> toNestedMap(AllPlatformWeights all) {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        putNested(out, MarketingPopularityScorer.PLATFORM_X_THREAD, all.xThread());
        putNested(out, MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED, all.instagramFeed());
        putNested(out, MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS, all.instagramReels());
        putNested(out, MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS, all.youtubeShorts());
        return out;
    }

    private static void putNested(
            Map<String, Map<String, Double>> out,
            String platform,
            MarketingPopularityScorer.PlatformWeights w) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put(SIGNAL_HOOK, w.hook());
        m.put(SIGNAL_VOTE_SKEW, w.voteSkew());
        m.put(SIGNAL_COMMENTS, w.comments());
        m.put(SIGNAL_VOTES, w.votes());
        m.put(SIGNAL_VIEWS, w.views());
        m.put(SIGNAL_HAS_PARTNER, w.hasPartner());
        out.put(platform, m);
    }

    public static MarketingPopularityScorer.PlatformWeights fromSignalMap(Map<String, Double> m) {
        if (m == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weights map required");
        }
        return new MarketingPopularityScorer.PlatformWeights(
            requireSignal(m, SIGNAL_HOOK),
            requireSignal(m, SIGNAL_VOTE_SKEW),
            requireSignal(m, SIGNAL_COMMENTS),
            requireSignal(m, SIGNAL_VOTES),
            requireSignal(m, SIGNAL_VIEWS),
            requireSignal(m, SIGNAL_HAS_PARTNER)
        );
    }

    private static double requireSignal(Map<String, Double> m, String key) {
        Double v = m.get(key);
        if (v == null) {
            // camelCase aliases from FE
            v = switch (key) {
                case SIGNAL_VOTE_SKEW -> first(m, "voteSkew");
                case SIGNAL_HAS_PARTNER -> first(m, "hasPartner");
                default -> null;
            };
        }
        if (v == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing weight: " + key);
        }
        return v;
    }

    private static Double first(Map<String, Double> m, String key) {
        return m.get(key);
    }
}
