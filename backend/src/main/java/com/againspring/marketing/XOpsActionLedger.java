package com.againspring.marketing;

import com.againspring.domain.marketing.XOpsAction;
import com.againspring.repository.marketing.XOpsActionRepository;
import com.againspring.repository.marketing.XTargetAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Persist X ops attempts so inbound/outbound/ritual publishers do not double-reply.
 */
@Service
@RequiredArgsConstructor
public class XOpsActionLedger {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final XOpsActionRepository repository;
    private final XTargetAccountRepository targetAccountRepository;

    /** Posted count and last post time for one target author over a window. */
    public record AuthorPosted(int count, Instant lastAt) {}

    /** Lowercase, no leading '@'. Blank/null → null (author unknown). */
    public static String normHandle(String raw) {
        if (raw == null) {
            return null;
        }
        String h = raw.trim();
        while (h.startsWith("@")) {
            h = h.substring(1);
        }
        h = h.trim().toLowerCase(Locale.ROOT);
        return h.isEmpty() ? null : h;
    }

    /** Start of the KST calendar day containing {@code now}. */
    public static Instant startOfKstDay(Instant now) {
        return kstDayWindow(now)[0];
    }

    /** Outbound POSTED per author since {@code since}; handles must be normalised. */
    public Map<String, AuthorPosted> postedByAuthorSince(Collection<String> handles, Instant since) {
        Map<String, AuthorPosted> out = new HashMap<>();
        if (handles == null || handles.isEmpty()) {
            return out;
        }
        for (XOpsActionRepository.AuthorPosted row : repository.postedByAuthorSince(
                XOpsAction.Kind.OUTBOUND, XOpsAction.Status.POSTED, handles, since)) {
            out.put(row.getHandle(), new AuthorPosted((int) row.getCnt(), row.getLastAt()));
        }
        return out;
    }

    /** Bump the per-account "seen in a candidate tick" counter. */
    @Transactional
    public void recordCandidateSeen(String handle, Instant now) {
        String h = normHandle(handle);
        if (h != null) {
            targetAccountRepository.upsertSeen(h, now);
        }
    }

    @Transactional
    public XOpsAction recordOutboundPosted(String targetTweetId, String parentTweetId,
        String postedTweetId, String body, Instant now, String authorHandle) {
        String h = normHandle(authorHandle);
        XOpsAction row = persist(XOpsAction.Kind.OUTBOUND, targetTweetId, parentTweetId, null,
            postedTweetId, body, XOpsAction.Status.POSTED, null, now, null, h);
        if (h != null) {
            targetAccountRepository.upsertPosted(h, row.getCreatedAt());
        }
        return row;
    }

    @Transactional
    public XOpsAction recordOutboundSkipped(String targetTweetId, String skipReason, Instant now,
        String authorHandle) {
        return persist(XOpsAction.Kind.OUTBOUND, targetTweetId, null, null, null, null,
            XOpsAction.Status.SKIPPED, skipReason, now, null, normHandle(authorHandle));
    }

    @Transactional
    public XOpsAction recordOutboundFailed(String targetTweetId, String skipReason, Instant now,
        String authorHandle) {
        return persist(XOpsAction.Kind.OUTBOUND, targetTweetId, null, null, null, null,
            XOpsAction.Status.FAILED, skipReason, now, null, normHandle(authorHandle));
    }

    public boolean alreadyHandled(String targetTweetId) {
        if (targetTweetId == null || targetTweetId.isBlank()) {
            return false;
        }
        return repository.existsByTargetTweetId(targetTweetId);
    }

    /** KST calendar day of {@code now}. Count status=POSTED only. */
    public int countPostedToday(XOpsAction.Kind kind, Instant now) {
        Instant[] window = kstDayWindow(now);
        return (int) repository.countByKindAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            kind, XOpsAction.Status.POSTED, window[0], window[1]);
    }

    public int countPostedTodayForOurPost(String ourPostTweetId, Instant now) {
        Instant[] window = kstDayWindow(now);
        return (int) repository.countByOurPostTweetIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            ourPostTweetId, XOpsAction.Status.POSTED, window[0], window[1]);
    }

    @Transactional
    public XOpsAction recordPosted(XOpsAction.Kind kind, String targetTweetId, String parentTweetId,
        String ourPostTweetId, String postedTweetId, String body, Instant now) {
        return persist(kind, targetTweetId, parentTweetId, ourPostTweetId, postedTweetId, body,
            XOpsAction.Status.POSTED, null, now, null, null);
    }

    @Transactional
    public XOpsAction recordPosted(XOpsAction.Kind kind, String targetTweetId, String parentTweetId,
        String ourPostTweetId, String postedTweetId, String body, Instant now, Long refPostId) {
        return persist(kind, targetTweetId, parentTweetId, ourPostTweetId, postedTweetId, body,
            XOpsAction.Status.POSTED, null, now, refPostId, null);
    }

    @Transactional
    public XOpsAction recordSkipped(XOpsAction.Kind kind, String targetTweetId, String skipReason, Instant now) {
        return persist(kind, targetTweetId, null, null, null, null,
            XOpsAction.Status.SKIPPED, skipReason, now, null, null);
    }

    @Transactional
    public XOpsAction recordFailed(XOpsAction.Kind kind, String targetTweetId, String skipReason, Instant now) {
        return persist(kind, targetTweetId, null, null, null, null,
            XOpsAction.Status.FAILED, skipReason, now, null, null);
    }

    public boolean alreadyScooped(Long refPostId) {
        if (refPostId == null) {
            return false;
        }
        return repository.existsByRefPostId(refPostId);
    }

    private XOpsAction persist(XOpsAction.Kind kind, String targetTweetId, String parentTweetId,
        String ourPostTweetId, String postedTweetId, String body,
        XOpsAction.Status status, String skipReason, Instant now, Long refPostId,
        String targetAuthorHandle) {
        XOpsAction row = XOpsAction.builder()
            .kind(kind)
            .targetTweetId(targetTweetId)
            .parentTweetId(parentTweetId)
            .ourPostTweetId(ourPostTweetId)
            .postedTweetId(postedTweetId)
            .body(body)
            .status(status)
            .skipReason(trimSkipReason(skipReason))
            .refPostId(refPostId)
            .targetAuthorHandle(targetAuthorHandle)
            .createdAt(now != null ? now : Instant.now())
            .build();
        return repository.save(row);
    }

    static String trimSkipReason(String skipReason) {
        if (skipReason == null || skipReason.length() <= 32) {
            return skipReason;
        }
        return skipReason.substring(0, 32);
    }

    private static Instant[] kstDayWindow(Instant now) {
        LocalDate day = now.atZone(KST).toLocalDate();
        Instant start = day.atStartOfDay(KST).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(KST).toInstant();
        return new Instant[] {start, end};
    }
}
