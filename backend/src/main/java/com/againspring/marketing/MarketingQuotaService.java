package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.MarketingHoldingRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Daily marketing auto-publish caps.
 *
 * <p><b>Phase 2</b>: per-platform caps {@code marketing.cap.{platform}} (default 3 each).
 * Commit selection consumes one slot per story×platform.
 *
 * <p><b>Deprecated (Phase 1)</b>: {@code marketing.daily_text_cap} / {@code marketing.daily_video_cap}
 * remain as fallbacks when a platform key is unset:
 * <ul>
 *   <li>text platforms ({@code x_thread}, {@code instagram_feed}) →
 *       {@code max(1, daily_text_cap / 2)}</li>
 *   <li>video platforms ({@code instagram_reels}, {@code youtube_shorts}) →
 *       {@code daily_video_cap} (each; not split) when that legacy key exists and platform key does not</li>
 * </ul>
 * Waiting-board meta still exposes derived {@code dailyTextCap}/{@code dailyVideoCap}
 * (= sum of text / video platform caps) for UI compatibility.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingQuotaService {

    /** @deprecated Phase 1 shared pool — fallback only. */
    @Deprecated
    public static final String KEY_TEXT_CAP = "marketing.daily_text_cap";
    /** @deprecated Phase 1 shared pool — fallback only. */
    @Deprecated
    public static final String KEY_VIDEO_CAP = "marketing.daily_video_cap";

    public static final String KEY_CAP_PREFIX = "marketing.cap.";

    public static final int DEFAULT_PLATFORM_CAP = 3;
    public static final int DEFAULT_TEXT_CAP = 6;
    public static final int DEFAULT_VIDEO_CAP = 3;
    public static final int MIN_PLATFORM_CAP = 0;
    public static final int MAX_PLATFORM_CAP = 50;
    public static final int MIN_TEXT_CAP = 1;
    public static final int MAX_TEXT_CAP = 50;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 쿼터를 세는 창의 길이(시간). 발행 후 이 시간이 지나면 자리 하나가 돌아온다. */
    private static final int QUOTA_WINDOW_HOURS = 24;

    private final SystemSettingRepository systemSettingRepository;
    private final MarketingHoldingRepository holdingRepository;
    private final MarketingJobRepository jobRepository;
    private final ObjectMapper objectMapper;

    /** @deprecated Phase 1 shape. */
    @Deprecated
    public record Caps(int dailyTextCap, int dailyVideoCap) {}

    public record PlatformCap(String platform, int cap, long usedToday, long remaining) {}

    public record PlatformCaps(
        int xThread,
        int instagramFeed,
        int instagramReels,
        int youtubeShorts
    ) {
        public int forPlatform(String platform) {
            return switch (MarketingPopularityScorer.normalizePlatform(platform)) {
                case MarketingPopularityScorer.PLATFORM_X_THREAD -> xThread;
                case MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED -> instagramFeed;
                case MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS -> instagramReels;
                case MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS -> youtubeShorts;
                default -> 0;
            };
        }

        public Map<String, Integer> asMap() {
            Map<String, Integer> m = new LinkedHashMap<>();
            m.put(MarketingPopularityScorer.PLATFORM_X_THREAD, xThread);
            m.put(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED, instagramFeed);
            m.put(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS, instagramReels);
            m.put(MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS, youtubeShorts);
            return m;
        }
    }

    /**
     * Status for admin + board. Legacy fields are derived from platform caps/usage.
     */
    public record QuotaStatus(
        int dailyTextCap,
        int dailyVideoCap,
        long videosToday,
        long textsToday,
        long remainingPool,
        PlatformCaps platformCaps,
        Map<String, Long> usedTodayByPlatform,
        Map<String, Long> remainingByPlatform
    ) {
        /** Phase 1 ctor compatibility for tests that omit platform maps. */
        public QuotaStatus(
                int dailyTextCap,
                int dailyVideoCap,
                long videosToday,
                long textsToday,
                long remainingPool) {
            this(
                dailyTextCap,
                dailyVideoCap,
                videosToday,
                textsToday,
                remainingPool,
                new PlatformCaps(
                    Math.max(0, dailyTextCap / 2),
                    Math.max(0, dailyTextCap - dailyTextCap / 2),
                    dailyVideoCap,
                    dailyVideoCap),
                Map.of(),
                Map.of());
        }
    }

    public Caps getCaps() {
        PlatformCaps p = getPlatformCaps();
        return new Caps(p.xThread() + p.instagramFeed(), p.instagramReels() + p.youtubeShorts());
    }

    public PlatformCaps getPlatformCaps() {
        int legacyText = readIntOptional(KEY_TEXT_CAP).orElse(DEFAULT_TEXT_CAP);
        int legacyVideo = readIntOptional(KEY_VIDEO_CAP).orElse(DEFAULT_VIDEO_CAP);
        int textFallback = Math.max(1, legacyText / 2);
        return new PlatformCaps(
            readPlatformCap(MarketingPopularityScorer.PLATFORM_X_THREAD, textFallback),
            readPlatformCap(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED, textFallback),
            readPlatformCap(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS, legacyVideo),
            readPlatformCap(MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS, legacyVideo)
        );
    }

    public Instant startOfTodayKst() {
        return LocalDate.now(KST).atStartOfDay(KST).toInstant();
    }

    /**
     * 쿼터를 세는 창의 시작점 — 지금부터 24시간 전.
     *
     * <p>예전에는 KST 자정을 경계로 삼았다. 그러면 자정 직전에 몰려 나간 발행이
     * 다음 날 몫까지 잡아먹고, 그날은 하루 종일 한 건도 못 내보낸다.
     * 실제로 2026-08-25 백필 9건이 자정을 넘겨 발행되면서 08-26 영상 쿼터가
     * (쇼츠 7/3 · 릴스 4/3) 소진돼 그날 영상이 0건이 됐다.
     *
     * <p>롤링 창으로 바꾸면 한 건이 나간 지 24시간이 지나는 순간 그 자리가
     * 하나씩 되살아난다. 몰아치기 뒤에도 하루를 통째로 굶지 않는다.
     */
    Instant rollingWindowStart() {
        return Instant.now().minus(java.time.Duration.ofHours(QUOTA_WINDOW_HOURS));
    }

    public QuotaStatus getStatus() {
        PlatformCaps caps = getPlatformCaps();
        Instant start = rollingWindowStart();
        Map<String, Long> publishedByPlatform = countPublishedByPlatformSince(start);

        Map<String, Long> used = new LinkedHashMap<>();
        Map<String, Long> remaining = new LinkedHashMap<>();
        long remainingSum = 0;
        for (String platform : MarketingPopularityScorer.RANKED_PLATFORMS) {
            long u = publishedByPlatform.getOrDefault(platform, 0L);
            int cap = caps.forPlatform(platform);
            long rem = Math.max(0, cap - u);
            used.put(platform, u);
            remaining.put(platform, rem);
            remainingSum += rem;
        }

        long videosToday = used.getOrDefault(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS, 0L)
            + used.getOrDefault(MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS, 0L);
        long textsToday = used.getOrDefault(MarketingPopularityScorer.PLATFORM_X_THREAD, 0L)
            + used.getOrDefault(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED, 0L);
        int dailyTextCap = caps.xThread() + caps.instagramFeed();
        int dailyVideoCap = caps.instagramReels() + caps.youtubeShorts();

        return new QuotaStatus(
            dailyTextCap,
            dailyVideoCap,
            videosToday,
            textsToday,
            remainingSum,
            caps,
            Map.copyOf(used),
            Map.copyOf(remaining)
        );
    }

    /**
     * Per-platform count of stories actually PUBLISHED since {@code since}.
     * A job's overall status may be PARTIAL (succeeded on one target, failed on
     * another), so this inspects the {@code publications} JSON per platform rather
     * than trusting job-level status alone. Jobs that never reached a publish
     * attempt (REQUESTED/QUEUED/RUNNING/READY sitting unclicked, or FAILED outright)
     * contribute nothing — they must not permanently consume a quota slot.
     */
    Map<String, Long> countPublishedByPlatformSince(Instant since) {
        Map<String, Set<String>> publishedPostsByPlatform = new HashMap<>();
        for (MarketingJob job : jobRepository.findPublishAttemptsSince(since)) {
            String publications = job.getPublications();
            if (publications == null || publications.isBlank()) {
                continue;
            }
            try {
                JsonNode arr = objectMapper.readTree(publications);
                if (!arr.isArray()) {
                    continue;
                }
                for (JsonNode entry : arr) {
                    String platform = entry.path("platform").asText(null);
                    String state = entry.path("state").asText(null);
                    if (platform == null || !"PUBLISHED".equalsIgnoreCase(state)) {
                        continue;
                    }
                    publishedPostsByPlatform
                        .computeIfAbsent(platform, k -> new HashSet<>())
                        .add(job.getPostId());
                }
            } catch (Exception e) {
                log.warn("Failed to parse publications for quota count, job={}: {}",
                    job.getId(), e.getMessage());
            }
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : publishedPostsByPlatform.entrySet()) {
            counts.put(entry.getKey(), (long) entry.getValue().size());
        }
        return counts;
    }

    /** Mutable remaining map for the commit tick (enabled platforms only filled by caller). */
    public Map<String, Integer> remainingCapsMutable() {
        QuotaStatus status = getStatus();
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String platform : MarketingPopularityScorer.RANKED_PLATFORMS) {
            m.put(platform, status.remainingByPlatform().getOrDefault(platform, 0L).intValue());
        }
        return m;
    }

    @Transactional
    public QuotaStatus updateCaps(int dailyTextCap, int dailyVideoCap, String updatedBy) {
        validate(dailyTextCap, dailyVideoCap);
        Instant now = Instant.now();
        // Persist legacy keys (deprecated) and distribute into platform caps.
        saveSetting(KEY_TEXT_CAP, String.valueOf(dailyTextCap), now, updatedBy);
        saveSetting(KEY_VIDEO_CAP, String.valueOf(dailyVideoCap), now, updatedBy);
        int textEach = Math.max(1, dailyTextCap / 2);
        int textFeed = Math.max(0, dailyTextCap - textEach);
        saveSetting(capKey(MarketingPopularityScorer.PLATFORM_X_THREAD), String.valueOf(textEach), now, updatedBy);
        saveSetting(capKey(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED), String.valueOf(textFeed), now, updatedBy);
        saveSetting(capKey(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS), String.valueOf(dailyVideoCap), now, updatedBy);
        saveSetting(capKey(MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS), String.valueOf(dailyVideoCap), now, updatedBy);
        return getStatus();
    }

    @Transactional
    public QuotaStatus updatePlatformCaps(PlatformCaps caps, String updatedBy) {
        validatePlatformCaps(caps);
        Instant now = Instant.now();
        saveSetting(capKey(MarketingPopularityScorer.PLATFORM_X_THREAD), String.valueOf(caps.xThread()), now, updatedBy);
        saveSetting(capKey(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED), String.valueOf(caps.instagramFeed()), now, updatedBy);
        saveSetting(capKey(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS), String.valueOf(caps.instagramReels()), now, updatedBy);
        saveSetting(capKey(MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS), String.valueOf(caps.youtubeShorts()), now, updatedBy);
        // Keep legacy keys as derived sums for older readers.
        saveSetting(KEY_TEXT_CAP, String.valueOf(caps.xThread() + caps.instagramFeed()), now, updatedBy);
        saveSetting(KEY_VIDEO_CAP, String.valueOf(caps.instagramReels() + caps.youtubeShorts()), now, updatedBy);
        return getStatus();
    }

    static void validate(int dailyTextCap, int dailyVideoCap) {
        if (dailyTextCap < MIN_TEXT_CAP || dailyTextCap > MAX_TEXT_CAP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "dailyTextCap must be between " + MIN_TEXT_CAP + " and " + MAX_TEXT_CAP);
        }
        if (dailyVideoCap < 0 || dailyVideoCap > MAX_PLATFORM_CAP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "dailyVideoCap must be between 0 and " + MAX_PLATFORM_CAP);
        }
    }

    static void validatePlatformCaps(PlatformCaps caps) {
        if (caps == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "platformCaps is required");
        }
        requireCap("x_thread", caps.xThread());
        requireCap("instagram_feed", caps.instagramFeed());
        requireCap("instagram_reels", caps.instagramReels());
        requireCap("youtube_shorts", caps.youtubeShorts());
    }

    private static void requireCap(String name, int value) {
        if (value < MIN_PLATFORM_CAP || value > MAX_PLATFORM_CAP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                name + " cap must be between " + MIN_PLATFORM_CAP + " and " + MAX_PLATFORM_CAP);
        }
    }

    public static String capKey(String platform) {
        return KEY_CAP_PREFIX + platform;
    }

    private int readPlatformCap(String platform, int fallbackWhenUnset) {
        return systemSettingRepository.findById(capKey(platform))
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                try {
                    int parsed = Integer.parseInt(raw.trim());
                    if (parsed < MIN_PLATFORM_CAP || parsed > MAX_PLATFORM_CAP) {
                        return DEFAULT_PLATFORM_CAP;
                    }
                    return parsed;
                } catch (Exception e) {
                    return DEFAULT_PLATFORM_CAP;
                }
            })
            .orElse(fallbackWhenUnset >= 0 ? fallbackWhenUnset : DEFAULT_PLATFORM_CAP);
    }

    private java.util.OptionalInt readIntOptional(String key) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                try {
                    return java.util.OptionalInt.of(Integer.parseInt(raw.trim()));
                } catch (Exception e) {
                    return java.util.OptionalInt.empty();
                }
            })
            .orElse(java.util.OptionalInt.empty());
    }

    private int readInt(String key, int defaultValue) {
        return readIntOptional(key).orElse(defaultValue);
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
