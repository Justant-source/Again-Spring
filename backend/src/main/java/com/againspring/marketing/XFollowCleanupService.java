package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XFollowAccount;
import com.againspring.domain.marketing.XFollowEvent;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XFollowAccountRepository;
import com.againspring.repository.marketing.XFollowEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Early-morning cleanup of big accounts we followed that never reacted: not mutual, followed
 * at least {@code unfollow_after_days} ago, no like/repost/reply from them since the follow, and
 * follower count in [{@code unfollow_min_followers}, {@code unfollow_max_followers}). Runs only on a complete, fresh tracker snapshot.
 *
 * <p>Gates (system_setting): {@code unfollow_cleanup_enabled} (default false),
 * {@code unfollow_dry_run} (default true: report only, no X / DB change).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XFollowCleanupService {

    public static final String KEY_ENABLED = "marketing.x.unfollow_cleanup_enabled";
    public static final String KEY_DRY_RUN = "marketing.x.unfollow_dry_run";
    public static final String KEY_MIN_FOLLOWERS = "marketing.x.unfollow_min_followers";
    public static final String KEY_MAX_FOLLOWERS = "marketing.x.unfollow_max_followers";
    public static final String KEY_AFTER_DAYS = "marketing.x.unfollow_after_days";
    public static final String KEY_DAILY_CAP = "marketing.x.unfollow_daily_cap";

    static final int DEFAULT_MIN_FOLLOWERS = 10_000;
    /** Mega/official accounts (celebrities, brands) are kept: they never follow back by design. */
    static final int DEFAULT_MAX_FOLLOWERS = 1_000_000;
    static final int DEFAULT_AFTER_DAYS = 7;
    static final int DEFAULT_DAILY_CAP = 30;
    static final String REASON = "NO_ACTION_7D";
    static final Duration COUNT_TTL = Duration.ofDays(3);
    static final int LOOKUP_BATCH = 8;
    static final int MAX_LOOKUPS_PER_RUN = 60;
    static final int UNFOLLOW_BATCH = 3;

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public record CleanupResult(boolean ran, boolean dryRun, int candidates, int qualified,
        int unfollowed, String abortedReason) {
        static CleanupResult skipped() {
            return new CleanupResult(false, false, 0, 0, 0, null);
        }
    }

    private final SystemSettingRepository systemSettingRepository;
    private final AsmClient asmClient;
    private final XFollowTrackerService trackerService;
    private final XFollowAccountRepository accountRepository;
    private final XFollowEventRepository eventRepository;
    private final TelegramNotifier telegramNotifier;

    /** Replaceable in tests. */
    Sleeper sleeper = Thread::sleep;

    public CleanupResult run(Instant now) {
        if (!readBool(KEY_ENABLED, false)) {
            return CleanupResult.skipped();
        }
        boolean dryRun = readBool(KEY_DRY_RUN, true);
        int minFollowers = readInt(KEY_MIN_FOLLOWERS, DEFAULT_MIN_FOLLOWERS, 100, 10_000_000);
        int maxFollowers = readInt(KEY_MAX_FOLLOWERS, DEFAULT_MAX_FOLLOWERS, 1_000, Integer.MAX_VALUE);
        int afterDays = readInt(KEY_AFTER_DAYS, DEFAULT_AFTER_DAYS, 1, 60);
        int dailyCap = readInt(KEY_DAILY_CAP, DEFAULT_DAILY_CAP, 0, 100);

        XFollowTrackerService.SyncResult sync;
        try {
            sync = trackerService.sync(now);
        } catch (Exception e) {
            return abort(dryRun, 0, 0, 0, "tracker sync exception: " + e.getMessage());
        }
        if (sync == null || !sync.ok()) {
            return abort(dryRun, 0, 0, 0, "tracker sync not ok");
        }
        if (!sync.snapshotComplete()) {
            return abort(dryRun, 0, 0, 0, "following snapshot incomplete");
        }

        List<XFollowAccount> candidates = accountRepository
            .findByStatusAndFollowsUsFalseAndFollowedAtLessThanEqual(
                XFollowAccount.Status.FOLLOWING, now.minus(Duration.ofDays(afterDays)))
            .stream()
            .filter(a -> a.getFollowedAt() != null)
            .filter(a -> a.getLastInboundInteractionAt() == null
                || a.getLastInboundInteractionAt().isBefore(a.getFollowedAt()))
            .sorted(Comparator.comparing(XFollowAccount::getFollowedAt))
            .collect(Collectors.toCollection(ArrayList::new));

        try {
            refreshFollowerCounts(candidates, now);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return abort(dryRun, candidates.size(), 0, 0, "interrupted");
        }

        long doneToday = todayUnfollowCount(now);
        int remaining = (int) Math.max(0, dailyCap - doneToday);
        List<XFollowAccount> targets = candidates.stream()
            .filter(a -> a.getFollowerCount() != null && a.getFollowerCount() >= minFollowers
                && a.getFollowerCount() < maxFollowers)
            .limit(remaining)
            .toList();
        log.info("[x-unfollow] candidates={} targets={} remainingCap={} dryRun={}",
            candidates.size(), targets.size(), remaining, dryRun);

        if (dryRun) {
            String lines = targets.stream()
                .map(a -> "@" + a.getHandle() + " " + a.getFollowerCount() + "명 / "
                    + Duration.between(a.getFollowedAt(), now).toDays() + "일")
                .collect(Collectors.joining("\n"));
            log.info("[x-unfollow] DRY-RUN targets:\n{}", lines);
            notify(String.format(Locale.ROOT,
                "🧹 [Again-Spring] X 언팔 정리 (DRY-RUN)%n후보 %d · 대상 %d%n%s",
                candidates.size(), targets.size(), lines));
            return new CleanupResult(true, true, candidates.size(), targets.size(), 0, null);
        }

        int unfollowed = 0;
        String aborted = null;
        for (int i = 0; i < targets.size() && aborted == null; i += UNFOLLOW_BATCH) {
            List<XFollowAccount> batch = targets.subList(i, Math.min(targets.size(), i + UNFOLLOW_BATCH));
            if (i > 0) {
                try {
                    sleeper.sleep(30_000L + ThreadLocalRandom.current().nextLong(30_001L));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    aborted = "interrupted";
                    break;
                }
            }
            AsmClient.XUnfollowResult res;
            try {
                res = asmClient.unfollowX(batch.stream().map(XFollowAccount::getHandle).toList());
            } catch (Exception e) {
                log.warn("[x-unfollow] call failed, aborting: {}", e.getMessage());
                aborted = "unfollow call failed: " + e.getMessage();
                break;
            }
            Map<String, XFollowAccount> byHandle = batch.stream()
                .collect(Collectors.toMap(XFollowAccount::getHandle, a -> a, (a, b) -> a));
            for (String raw : res.unfollowed()) {
                XFollowAccount acc = byHandle.get(XOpsActionLedger.normHandle(raw));
                if (acc == null) {
                    continue;
                }
                record(acc, now);
                unfollowed++;
            }
            if (!res.failed().isEmpty()) {
                AsmClient.XUnfollowFailure f = res.failed().get(0);
                aborted = "unfollow failed: @" + f.handle() + " " + f.error();
                log.warn("[x-unfollow] {} failure(s), aborting: {}", res.failed().size(), aborted);
            }
        }
        notify(String.format(Locale.ROOT,
            "🧹 [Again-Spring] X 언팔 정리%n언팔 %d / 대상 %d (후보 %d)%s",
            unfollowed, targets.size(), candidates.size(),
            aborted != null ? "\n⚠️ 중단: " + aborted : ""));
        return new CleanupResult(true, false, candidates.size(), targets.size(), unfollowed, aborted);
    }

    private void record(XFollowAccount acc, Instant now) {
        acc.setStatus(XFollowAccount.Status.UNFOLLOWED);
        acc.setUnfollowedAt(now);
        acc.setUnfollowReason(REASON);
        accountRepository.save(acc);
        eventRepository.save(XFollowEvent.builder()
            .handle(acc.getHandle())
            .direction(XFollowEvent.Direction.OUT)
            .kind(XFollowEvent.Kind.UNFOLLOW)
            .occurredAt(now)
            .build());
    }

    private void refreshFollowerCounts(List<XFollowAccount> candidates, Instant now)
        throws InterruptedException {
        List<XFollowAccount> stale = candidates.stream()
            .filter(a -> a.getFollowerCount() == null || a.getFollowerCountCheckedAt() == null
                || a.getFollowerCountCheckedAt().isBefore(now.minus(COUNT_TTL)))
            .limit(MAX_LOOKUPS_PER_RUN)
            .toList();
        for (int i = 0; i < stale.size(); i += LOOKUP_BATCH) {
            List<XFollowAccount> batch = stale.subList(i, Math.min(stale.size(), i + LOOKUP_BATCH));
            if (i > 0) {
                sleeper.sleep(3_000L + ThreadLocalRandom.current().nextLong(5_001L));
            }
            AsmClient.XProfileFollowersResult res;
            try {
                res = asmClient.getXProfileFollowers(batch.stream().map(XFollowAccount::getHandle).toList());
            } catch (Exception e) {
                log.warn("[x-unfollow] follower lookup batch failed: {}", e.getMessage());
                continue;
            }
            Map<String, XFollowAccount> byHandle = batch.stream()
                .collect(Collectors.toMap(XFollowAccount::getHandle, a -> a, (a, b) -> a));
            for (AsmClient.XProfileFollowers r : res.results()) {
                XFollowAccount acc = byHandle.get(XOpsActionLedger.normHandle(r.handle()));
                if (acc == null || r.followers() == null) {
                    continue;
                }
                acc.setFollowerCount(r.followers());
                acc.setFollowerCountCheckedAt(now);
                accountRepository.save(acc);
            }
        }
    }

    private long todayUnfollowCount(Instant now) {
        LocalDate day = now.atZone(XDailyQuota.KST).toLocalDate();
        Instant from = day.atStartOfDay(XDailyQuota.KST).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(XDailyQuota.KST).toInstant();
        return eventRepository.countByDirectionAndKindAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            XFollowEvent.Direction.OUT, XFollowEvent.Kind.UNFOLLOW, from, to);
    }

    private CleanupResult abort(boolean dryRun, int candidates, int qualified, int unfollowed, String reason) {
        log.warn("[x-unfollow] aborted: {}", reason);
        notify("🧹 [Again-Spring] X 언팔 정리 중단\n사유: " + reason);
        return new CleanupResult(true, dryRun, candidates, qualified, unfollowed, reason);
    }

    private void notify(String text) {
        try {
            telegramNotifier.send(text);
        } catch (Exception e) {
            log.debug("[x-unfollow] telegram failed: {}", e.getMessage());
        }
    }

    private boolean readBool(String key, boolean def) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(v -> v == null ? def : ("true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim())))
            .orElse(def);
    }

    private int readInt(String key, int def, int min, int max) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                try {
                    int n = Integer.parseInt(raw.trim());
                    return n < min || n > max ? def : n;
                } catch (Exception e) {
                    return def;
                }
            })
            .orElse(def);
    }
}
