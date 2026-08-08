package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Per-platform auto-publish on/off for marketing (admin settings + scheduler helper).
 *
 * <p>Storage: {@code system_setting} keys {@code marketing.platform.{id}.auto_enabled}.
 * Defaults: runtime-supported platforms ON; unsupported OFF (admin may still enable).
 *
 * <p>Q12: enabling unsupported succeeds with a warning (C); at publish time
 * {@link #resolveTargets} keeps only {@code enabled ∩ supported} and logs skips (D).
 *
 * <p>S4 commit path ({@code MarketingHoldingCommitService}) calls {@link #resolveTargets}
 * when building enqueue targets.
 *
 * <p>Pin ↔ publish format: holding {@code MarketingPinFormat} maps 1:1 to
 * {@link MarketingPublishFormat} via {@link MarketingPublishFormat#fromPin}
 * (VIDEO→VIDEO, TEXT→TEXT). Domain pin stays on the entity; publish format is
 * the resolveTargets input.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingPlatformAutoService {

    public static final String KEY_PREFIX = "marketing.platform.";
    public static final String KEY_SUFFIX = ".auto_enabled";

    /** Display / admin order — all known platforms (Q11). */
    public static final List<String> ALL_PLATFORMS = List.of(
        "x",
        "x_thread",
        "naver_blog",
        "instagram_feed",
        "instagram_reels",
        "youtube_shorts",
        "naver_clip",
        "threads"
    );

    /**
     * Runtime publish set (code constant). Matches active channels in
     * {@code docs/shared/marketing/platforms.md} (x + 24h auto channels).
     * Unimplemented: naver_blog, naver_clip, threads.
     */
    public static final Set<String> RUNTIME_SUPPORTED = Set.of(
        "x",
        "x_thread",
        "instagram_feed",
        "instagram_reels",
        "youtube_shorts"
    );

    private static final List<String> VIDEO_PLATFORMS = List.of(
        "instagram_reels",
        "youtube_shorts",
        "naver_clip"
    );

    private static final List<String> TEXT_PLATFORMS = List.of(
        "x",
        "x_thread",
        "instagram_feed",
        "naver_blog",
        "threads"
    );

    public static final String UNSUPPORTED_ENABLE_WARNING =
        "현재 런타임 미지원 — 자동 발행 시 이 채널은 제외됩니다";

    private final SystemSettingRepository systemSettingRepository;

    public record PlatformStatus(
        String platform,
        boolean autoEnabled,
        boolean runtimeSupported,
        String warning
    ) {}

    public List<PlatformStatus> listPlatforms() {
        List<PlatformStatus> out = new ArrayList<>(ALL_PLATFORMS.size());
        for (String platform : ALL_PLATFORMS) {
            out.add(toStatus(platform, isAutoEnabled(platform)));
        }
        return out;
    }

    public boolean isRuntimeSupported(String platform) {
        return RUNTIME_SUPPORTED.contains(normalize(platform));
    }

    public boolean isAutoEnabled(String platform) {
        String id = requireKnownPlatform(platform);
        return systemSettingRepository.findById(settingKey(id))
            .map(SystemSetting::getSettingValue)
            .map(MarketingPlatformAutoService::parseBoolean)
            .orElseGet(() -> defaultAutoEnabled(id));
    }

    /** Platforms with auto currently on (stored or default), regardless of runtime support. */
    public List<String> listEnabledPlatforms() {
        List<String> enabled = new ArrayList<>();
        for (String platform : ALL_PLATFORMS) {
            if (isAutoEnabled(platform)) {
                enabled.add(platform);
            }
        }
        return enabled;
    }

    @Transactional
    public PlatformStatus setAutoEnabled(String platform, boolean enabled, String updatedBy) {
        String id = requireKnownPlatform(platform);
        Instant now = Instant.now();
        String key = settingKey(id);
        SystemSetting setting = systemSettingRepository.findById(key).orElseGet(() ->
            SystemSetting.builder().settingKey(key).build());
        setting.setSettingValue(enabled ? "true" : "false");
        setting.setUpdatedAt(now);
        setting.setUpdatedBy(updatedBy != null ? updatedBy : "admin");
        systemSettingRepository.save(setting);
        return toStatus(id, enabled);
    }

    /**
     * Build publish targets for a committed format: {@code enabled ∩ supported}.
     * Unsupported-but-enabled platforms are skipped with a log (Q12 D).
     * For VIDEO, {@code instagram_feed} is excluded when {@code instagram_reels} is included (Q17).
     */
    public List<String> resolveTargets(MarketingPublishFormat format, Collection<String> enabled) {
        if (format == null) {
            throw new IllegalArgumentException("format is required");
        }
        Set<String> enabledSet = new LinkedHashSet<>();
        if (enabled != null) {
            for (String raw : enabled) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String id = normalize(raw);
                if (!ALL_PLATFORMS.contains(id)) {
                    log.warn("resolveTargets: ignoring unknown platform '{}'", raw);
                    continue;
                }
                enabledSet.add(id);
            }
        }

        List<String> video = intersectSupported(VIDEO_PLATFORMS, enabledSet);
        List<String> text = intersectSupported(TEXT_PLATFORMS, enabledSet);

        for (String id : enabledSet) {
            if (!RUNTIME_SUPPORTED.contains(id)) {
                log.info("resolveTargets: skipping unsupported enabled platform '{}'", id);
            }
        }

        if (format == MarketingPublishFormat.TEXT) {
            return List.copyOf(text);
        }

        // VIDEO: video channels + text channels; IG feed ⊥ reels
        List<String> targets = new ArrayList<>(video.size() + text.size());
        targets.addAll(video);
        boolean reelsIncluded = video.contains("instagram_reels");
        for (String t : text) {
            if (reelsIncluded && "instagram_feed".equals(t)) {
                log.info("resolveTargets: excluding instagram_feed because instagram_reels is included");
                continue;
            }
            targets.add(t);
        }
        return List.copyOf(targets);
    }

    /** Convenience: resolve using currently stored/default auto-enabled flags. */
    public List<String> resolveTargets(MarketingPublishFormat format) {
        return resolveTargets(format, listEnabledPlatforms());
    }

    /**
     * True when at least one video platform is auto-on ∩ runtime-supported (Q16).
     * When false, effective video cap for the commit tick is 0 (all pool is text).
     */
    public boolean hasEffectiveVideoPlatforms() {
        for (String id : VIDEO_PLATFORMS) {
            if (RUNTIME_SUPPORTED.contains(id) && isAutoEnabled(id)) {
                return true;
            }
        }
        return false;
    }

    static String settingKey(String platform) {
        return KEY_PREFIX + platform + KEY_SUFFIX;
    }

    static boolean defaultAutoEnabled(String platform) {
        return RUNTIME_SUPPORTED.contains(platform);
    }

    private static List<String> intersectSupported(List<String> candidates, Set<String> enabled) {
        List<String> out = new ArrayList<>();
        for (String id : candidates) {
            if (enabled.contains(id) && RUNTIME_SUPPORTED.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private PlatformStatus toStatus(String platform, boolean autoEnabled) {
        boolean supported = RUNTIME_SUPPORTED.contains(platform);
        String warning = (!supported && autoEnabled) ? UNSUPPORTED_ENABLE_WARNING : null;
        return new PlatformStatus(platform, autoEnabled, supported, warning);
    }

    private static String requireKnownPlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "platform is required");
        }
        String id = normalize(platform);
        if (!ALL_PLATFORMS.contains(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown platform: " + platform);
        }
        return id;
    }

    private static String normalize(String platform) {
        return platform.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean parseBoolean(String raw) {
        if (raw == null) {
            return false;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return "true".equals(v) || "1".equals(v) || "yes".equals(v);
    }
}
