package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XFollowAccount;
import com.againspring.domain.marketing.XFollowEvent;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XFollowAccountRepository;
import com.againspring.repository.marketing.XFollowEventRepository;
import com.againspring.repository.marketing.XOpsActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Mirrors the accounts we follow into {@code x_follow_account} / {@code x_follow_event}:
 * follow time (ledger, interpolated from list order, or first-seen), reciprocity, inbound
 * engagement. Never marks accounts UNFOLLOWED — the cleanup service owns that state.
 *
 * <p>Gate: {@code marketing.x.follow_tracking_enabled} (default <b>true</b>).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XFollowTrackerService {

    public static final String KEY_ENABLED = "marketing.x.follow_tracking_enabled";
    static final Duration OUTBOUND_LOOKBACK = Duration.ofDays(14);

    /**
     * {@code ok=false} for failed / disabled / skipped(lock busy) runs — nothing was written
     * and callers (cleanup) must not rely on fresh data.
     */
    public record SyncResult(boolean ok, boolean snapshotComplete, int seen, int newAccounts,
                             int newMutual, int inboundEvents) {
        public static SyncResult failed() {
            return new SyncResult(false, false, 0, 0, 0, 0);
        }
    }

    private record Anchor(int order, Instant at) {}

    private final SystemSettingRepository systemSettingRepository;
    private final AsmClient asmClient;
    private final XFollowAccountRepository accountRepository;
    private final XFollowEventRepository eventRepository;
    private final XOpsActionRepository opsActionRepository;
    private final TransactionTemplate transactionTemplate;

    private final ReentrantLock lock = new ReentrantLock();

    public SyncResult sync(Instant now) {
        if (!readEnabled()) {
            log.info("[x-follow-tracker] disabled");
            return SyncResult.failed();
        }
        if (!lock.tryLock()) {
            log.info("[x-follow-tracker] sync already running, skipped");
            return SyncResult.failed();
        }
        try {
            AsmClient.XFollowingSnapshot snapshot;
            try {
                snapshot = asmClient.getXFollowingSnapshot();
            } catch (Exception e) {
                log.warn("[x-follow-tracker] following snapshot failed: {}", e.getMessage());
                return SyncResult.failed();
            }
            AsmClient.XEngagers engagers = null;
            try {
                engagers = asmClient.getXEngagers();
            } catch (Exception e) {
                log.warn("[x-follow-tracker] engagers fetch failed (skipping step): {}", e.getMessage());
            }
            final AsmClient.XEngagers eng = engagers;
            SyncResult result = transactionTemplate.execute(tx -> persist(snapshot, eng, now));
            log.info("[x-follow-tracker] {}", result);
            return result == null ? SyncResult.failed() : result;
        } catch (Exception e) {
            log.warn("[x-follow-tracker] sync failed: {}", e.getMessage(), e);
            return SyncResult.failed();
        } finally {
            lock.unlock();
        }
    }

    private SyncResult persist(AsmClient.XFollowingSnapshot snapshot, AsmClient.XEngagers engagers, Instant now) {
        Map<String, XFollowAccount> accounts = new HashMap<>();
        for (XFollowAccount a : accountRepository.findAll()) {
            accounts.put(a.getHandle(), a);
        }

        // ledger FOLLOW rows: handle -> earliest createdAt
        Map<String, Instant> ledgerFollow = new HashMap<>();
        for (XOpsAction a : opsActionRepository.findByStatusAndKindInAndCreatedAtGreaterThanEqual(
            XOpsAction.Status.POSTED, List.of(XOpsAction.Kind.FOLLOW), Instant.EPOCH)) {
            String h = followHandle(a.getTargetTweetId());
            if (h != null && a.getCreatedAt() != null) {
                ledgerFollow.merge(h, a.getCreatedAt(), (x, y) -> x.isBefore(y) ? x : y);
            }
        }

        // anchors: ledger-known accounts present in the snapshot, newest first
        List<Anchor> anchors = new ArrayList<>();
        Set<String> seenHandles = new HashSet<>();
        for (AsmClient.XFollowingEntry e : snapshot.following()) {
            String h = normalize(e.handle());
            if (h == null || !seenHandles.add(h)) {
                continue;
            }
            Instant at = ledgerFollow.get(h);
            if (at != null) {
                anchors.add(new Anchor(e.order(), at));
            }
        }
        anchors.sort(Comparator.comparingInt(Anchor::order));

        int seen = 0;
        int newAccounts = 0;
        int newMutual = 0;
        Set<String> processed = new HashSet<>();
        for (AsmClient.XFollowingEntry e : snapshot.following()) {
            String h = normalize(e.handle());
            if (h == null || !processed.add(h)) {
                continue;
            }
            seen++;
            XFollowAccount acct = accounts.get(h);
            boolean isNew = acct == null;
            if (isNew) {
                acct = XFollowAccount.builder().handle(h).status(XFollowAccount.Status.FOLLOWING)
                    .firstSeenAt(now).lastSeenFollowingAt(now).build();
                newAccounts++;
            } else {
                acct.setLastSeenFollowingAt(now);
                if (acct.getStatus() == XFollowAccount.Status.UNFOLLOWED) {
                    acct.setStatus(XFollowAccount.Status.FOLLOWING);
                    acct.setUnfollowedAt(null);
                    acct.setUnfollowReason(null);
                    acct.setFollowedAt(now);
                    acct.setFollowedAtSource(XFollowAccount.FollowedAtSource.FIRST_SEEN);
                    recordEvent(h, XFollowEvent.Direction.OUT, XFollowEvent.Kind.FOLLOW, null, now);
                }
            }
            acct.setVerified(e.verified());

            if (acct.getFollowedAt() == null) {
                Instant ledgerAt = ledgerFollow.get(h);
                if (ledgerAt != null) {
                    acct.setFollowedAt(ledgerAt);
                    acct.setFollowedAtSource(XFollowAccount.FollowedAtSource.LEDGER);
                } else {
                    estimate(acct, e.order(), anchors, now);
                }
                recordEvent(h, XFollowEvent.Direction.OUT, XFollowEvent.Kind.FOLLOW, null, acct.getFollowedAt());
            }

            boolean nowFollowsUs = e.followsYou() || ledgerFollow.containsKey(h);
            if (nowFollowsUs && !acct.isFollowsUs()) {
                acct.setFollowsUs(true);
                acct.setFollowsUsSince(now);
                recordEvent(h, XFollowEvent.Direction.IN, XFollowEvent.Kind.FOLLOW_BACK, null, now);
                newMutual++;
            } else if (!nowFollowsUs && acct.isFollowsUs()) {
                acct.setFollowsUs(false);
                acct.setFollowsUsSince(null);
            }
            accounts.put(h, accountRepository.save(acct));
        }

        if (snapshot.complete()) {
            long notSeen = accounts.values().stream()
                .filter(a -> a.getStatus() == XFollowAccount.Status.FOLLOWING
                    && a.getLastSeenFollowingAt() != null && a.getLastSeenFollowingAt().isBefore(now))
                .count();
            if (notSeen > 0) {
                log.info("[x-follow-tracker] {} FOLLOWING accounts absent from complete snapshot (state untouched)",
                    notSeen);
            }
        } else {
            log.info("[x-follow-tracker] snapshot incomplete: absence ignored");
        }

        int inbound = 0;
        if (engagers != null) {
            for (AsmClient.XEngagerEvent ev : engagers.events()) {
                String h = normalize(ev.handle());
                XFollowAccount acct = h == null ? null : accounts.get(h);
                XFollowEvent.Kind kind = engagerKind(ev.kind());
                if (acct == null || kind == null) {
                    continue;
                }
                Instant at = ev.at() == null ? now : ev.at();
                if (recordEvent(h, XFollowEvent.Direction.IN, kind, null, at)) {
                    inbound++;
                }
                Instant prev = acct.getLastInboundInteractionAt();
                if (prev == null || at.isAfter(prev)) {
                    acct.setLastInboundInteractionAt(at);
                    accountRepository.save(acct);
                }
            }
        }

        Instant since = now.minus(OUTBOUND_LOOKBACK);
        for (XOpsAction a : opsActionRepository.findByStatusAndKindInAndCreatedAtGreaterThanEqual(
            XOpsAction.Status.POSTED, List.of(XOpsAction.Kind.OUTBOUND), since)) {
            String h = normalize(a.getTargetAuthorHandle());
            if (h != null && accounts.containsKey(h) && a.getCreatedAt() != null) {
                recordEvent(h, XFollowEvent.Direction.OUT, XFollowEvent.Kind.REPLY,
                    a.getTargetTweetId(), a.getCreatedAt());
            }
        }

        return new SyncResult(true, snapshot.complete(), seen, newAccounts, newMutual, inbound);
    }

    /** order 0 = newest follow; anchors sorted by order ascending (newest first). */
    private static void estimate(XFollowAccount acct, int order, List<Anchor> anchors, Instant now) {
        if (anchors.isEmpty() || order < anchors.get(0).order()) {
            acct.setFollowedAt(now);
            acct.setFollowedAtSource(XFollowAccount.FollowedAtSource.FIRST_SEEN);
            return;
        }
        Anchor newer = anchors.get(0);
        for (Anchor a : anchors) {
            if (a.order() < order) {
                newer = a; // closest newer anchor
            } else {
                break;
            }
        }
        acct.setFollowedAt(newer.at());
        acct.setFollowedAtSource(XFollowAccount.FollowedAtSource.ESTIMATED);
    }

    /** @return true when a new row was written. */
    private boolean recordEvent(String handle, XFollowEvent.Direction dir, XFollowEvent.Kind kind,
                                String tweetId, Instant at) {
        if (eventRepository.existsByHandleAndDirectionAndKindAndOccurredAt(handle, dir, kind, at)) {
            return false;
        }
        eventRepository.save(XFollowEvent.builder().handle(handle).direction(dir).kind(kind)
            .tweetId(tweetId).occurredAt(at).build());
        return true;
    }

    private static XFollowEvent.Kind engagerKind(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "LIKE" -> XFollowEvent.Kind.LIKE;
            case "REPOST" -> XFollowEvent.Kind.REPOST;
            case "REPLY" -> XFollowEvent.Kind.REPLY;
            default -> null;
        };
    }

    private static String followHandle(String targetTweetId) {
        if (targetTweetId == null || !targetTweetId.startsWith("follow:")) {
            return null;
        }
        return normalize(targetTweetId.substring("follow:".length()));
    }

    static String normalize(String handle) {
        if (handle == null) {
            return null;
        }
        String h = handle.trim();
        while (h.startsWith("@")) {
            h = h.substring(1);
        }
        h = h.toLowerCase(Locale.ROOT);
        return h.isEmpty() ? null : h;
    }

    private boolean readEnabled() {
        return systemSettingRepository.findById(KEY_ENABLED)
            .map(SystemSetting::getSettingValue)
            .map(v -> v == null || "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim()))
            .orElse(true);
    }
}
