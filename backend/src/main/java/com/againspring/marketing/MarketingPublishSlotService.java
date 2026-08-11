package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * KST evening publish slots for marketing jobs (Phase 1).
 *
 * <p>Holding COMMIT (T+24h) selects stories; this service sets when social publish
 * should actually fire. Keys live in {@code system_setting} as
 * {@code marketing.publish_slot.{platform}=HH:mm}.
 *
 * <h2>Scheduling rule (next occurrence)</h2>
 * <p>Zone = {@code Asia/Seoul}. Given {@code now} and a platform slot time {@code HH:mm}:
 * <ul>
 *   <li>If {@code now} is <em>strictly before</em> today's slot → schedule today at that time.</li>
 *   <li>If {@code now} is at or after today's slot → schedule <em>tomorrow</em> at that time
 *       (same-day slot already passed; do not schedule into the past).</li>
 * </ul>
 * Multi-target jobs (e.g. Reels+Shorts dual) resolve the earliest next occurrence among
 * targets that have a configured/default slot.
 */
@Service
@RequiredArgsConstructor
public class MarketingPublishSlotService {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final String KEY_PREFIX = "marketing.publish_slot.";

    public static final String PLATFORM_INSTAGRAM_FEED = "instagram_feed";
    public static final String PLATFORM_INSTAGRAM_REELS = "instagram_reels";
    public static final String PLATFORM_YOUTUBE_SHORTS = "youtube_shorts";
    public static final String PLATFORM_X_THREAD = "x_thread";

    /** Platforms with Phase-1 evening defaults (admin-configurable). */
    public static final List<String> SLOT_PLATFORMS = List.of(
        PLATFORM_INSTAGRAM_FEED,
        PLATFORM_INSTAGRAM_REELS,
        PLATFORM_YOUTUBE_SHORTS,
        PLATFORM_X_THREAD
    );

    public static final String DEFAULT_INSTAGRAM_FEED = "20:00";
    public static final String DEFAULT_INSTAGRAM_REELS = "20:30";
    public static final String DEFAULT_YOUTUBE_SHORTS = "20:30";
    public static final String DEFAULT_X_THREAD = "21:30";

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final SystemSettingRepository systemSettingRepository;

    public record Slots(
        String instagramFeed,
        String instagramReels,
        String youtubeShorts,
        String xThread
    ) {
        public Map<String, String> asMap() {
            Map<String, String> m = new LinkedHashMap<>();
            m.put(PLATFORM_INSTAGRAM_FEED, instagramFeed);
            m.put(PLATFORM_INSTAGRAM_REELS, instagramReels);
            m.put(PLATFORM_YOUTUBE_SHORTS, youtubeShorts);
            m.put(PLATFORM_X_THREAD, xThread);
            return m;
        }
    }

    public Slots getSlots() {
        return new Slots(
            readSlot(PLATFORM_INSTAGRAM_FEED, DEFAULT_INSTAGRAM_FEED),
            readSlot(PLATFORM_INSTAGRAM_REELS, DEFAULT_INSTAGRAM_REELS),
            readSlot(PLATFORM_YOUTUBE_SHORTS, DEFAULT_YOUTUBE_SHORTS),
            readSlot(PLATFORM_X_THREAD, DEFAULT_X_THREAD)
        );
    }

    @Transactional
    public Slots updateSlots(Slots slots, String updatedBy) {
        validateSlots(slots);
        Instant now = Instant.now();
        saveSetting(keyFor(PLATFORM_INSTAGRAM_FEED), slots.instagramFeed(), now, updatedBy);
        saveSetting(keyFor(PLATFORM_INSTAGRAM_REELS), slots.instagramReels(), now, updatedBy);
        saveSetting(keyFor(PLATFORM_YOUTUBE_SHORTS), slots.youtubeShorts(), now, updatedBy);
        saveSetting(keyFor(PLATFORM_X_THREAD), slots.xThread(), now, updatedBy);
        return getSlots();
    }

    /**
     * Next KST occurrence of {@code platform}'s configured slot after {@code now}.
     * Unknown / unconfigured platforms → empty.
     */
    public Optional<Instant> nextSlotForPlatform(String platform, Instant now) {
        if (platform == null || platform.isBlank() || now == null) {
            return Optional.empty();
        }
        String id = platform.trim().toLowerCase(Locale.ROOT);
        Optional<LocalTime> slotTime = slotTimeFor(id);
        if (slotTime.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(nextOccurrence(now, slotTime.get()));
    }

    /**
     * Earliest next slot among {@code targets}. Empty if none have a slot.
     */
    public Optional<Instant> nextSlotForTargets(Collection<String> targets, Instant now) {
        if (targets == null || targets.isEmpty() || now == null) {
            return Optional.empty();
        }
        Instant earliest = null;
        for (String target : targets) {
            Optional<Instant> candidate = nextSlotForPlatform(target, now);
            if (candidate.isEmpty()) {
                continue;
            }
            if (earliest == null || candidate.get().isBefore(earliest)) {
                earliest = candidate.get();
            }
        }
        return Optional.ofNullable(earliest);
    }

    /**
     * Pure calculation: next Asia/Seoul occurrence of {@code slotTime} at/after {@code now}.
     *
     * <p>Rule: if {@code now} &lt; today's slot → today; else → tomorrow
     * (at-or-after today's slot means the window already opened/passed).
     */
    public static Instant nextOccurrence(Instant now, LocalTime slotTime) {
        LocalDate today = LocalDate.ofInstant(now, KST);
        Instant todaySlot = today.atTime(slotTime).atZone(KST).toInstant();
        if (now.isBefore(todaySlot)) {
            return todaySlot;
        }
        return today.plusDays(1).atTime(slotTime).atZone(KST).toInstant();
    }

    public static String keyFor(String platform) {
        return KEY_PREFIX + platform;
    }

    static void validateSlots(Slots slots) {
        requireValidHhMm("instagramFeed", slots.instagramFeed());
        requireValidHhMm("instagramReels", slots.instagramReels());
        requireValidHhMm("youtubeShorts", slots.youtubeShorts());
        requireValidHhMm("xThread", slots.xThread());
    }

    static LocalTime parseHhMm(String raw) {
        try {
            return LocalTime.parse(raw.trim(), HH_MM);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "slot must be HH:mm (24h), got: " + raw);
        }
    }

    private static void requireValidHhMm(String name, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required (HH:mm)");
        }
        try {
            LocalTime.parse(raw.trim(), HH_MM);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                name + " must be HH:mm (24h), got: " + raw);
        }
    }

    private Optional<LocalTime> slotTimeFor(String platformId) {
        String defaultHhMm = defaultFor(platformId);
        if (defaultHhMm == null && !SLOT_PLATFORMS.contains(platformId)) {
            // Only known slot platforms are stored; deferred platforms have no evening default.
            String stored = systemSettingRepository.findById(keyFor(platformId))
                .map(SystemSetting::getSettingValue)
                .orElse(null);
            if (stored == null || stored.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(LocalTime.parse(stored.trim(), HH_MM));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }
        if (defaultHhMm == null) {
            return Optional.empty();
        }
        String hhMm = readSlot(platformId, defaultHhMm);
        try {
            return Optional.of(LocalTime.parse(hhMm.trim(), HH_MM));
        } catch (DateTimeParseException e) {
            return Optional.of(LocalTime.parse(defaultHhMm, HH_MM));
        }
    }

    private static String defaultFor(String platformId) {
        return switch (platformId) {
            case PLATFORM_INSTAGRAM_FEED -> DEFAULT_INSTAGRAM_FEED;
            case PLATFORM_INSTAGRAM_REELS -> DEFAULT_INSTAGRAM_REELS;
            case PLATFORM_YOUTUBE_SHORTS -> DEFAULT_YOUTUBE_SHORTS;
            case PLATFORM_X_THREAD -> DEFAULT_X_THREAD;
            default -> null;
        };
    }

    private String readSlot(String platform, String defaultValue) {
        return systemSettingRepository.findById(keyFor(platform))
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                if (raw == null || raw.isBlank()) {
                    return defaultValue;
                }
                try {
                    LocalTime.parse(raw.trim(), HH_MM);
                    return raw.trim();
                } catch (DateTimeParseException e) {
                    return defaultValue;
                }
            })
            .orElse(defaultValue);
    }

    private void saveSetting(String key, String value, Instant now, String updatedBy) {
        SystemSetting setting = systemSettingRepository.findById(key).orElseGet(() ->
            SystemSetting.builder().settingKey(key).build());
        setting.setSettingValue(value.trim());
        setting.setUpdatedAt(now);
        setting.setUpdatedBy(updatedBy);
        systemSettingRepository.save(setting);
    }
}
