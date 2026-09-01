package com.againspring.marketing;

import com.againspring.domain.marketing.XOpsAction;
import com.againspring.repository.marketing.XOpsActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Persist X ops attempts so inbound/outbound/ritual publishers do not double-reply.
 */
@Service
@RequiredArgsConstructor
public class XOpsActionLedger {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final XOpsActionRepository repository;

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
            XOpsAction.Status.POSTED, null, now);
    }

    @Transactional
    public XOpsAction recordSkipped(XOpsAction.Kind kind, String targetTweetId, String skipReason, Instant now) {
        return persist(kind, targetTweetId, null, null, null, null,
            XOpsAction.Status.SKIPPED, skipReason, now);
    }

    @Transactional
    public XOpsAction recordFailed(XOpsAction.Kind kind, String targetTweetId, String skipReason, Instant now) {
        return persist(kind, targetTweetId, null, null, null, null,
            XOpsAction.Status.FAILED, skipReason, now);
    }

    private XOpsAction persist(XOpsAction.Kind kind, String targetTweetId, String parentTweetId,
        String ourPostTweetId, String postedTweetId, String body,
        XOpsAction.Status status, String skipReason, Instant now) {
        XOpsAction row = XOpsAction.builder()
            .kind(kind)
            .targetTweetId(targetTweetId)
            .parentTweetId(parentTweetId)
            .ourPostTweetId(ourPostTweetId)
            .postedTweetId(postedTweetId)
            .body(body)
            .status(status)
            .skipReason(trimSkipReason(skipReason))
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
