package com.againspring.marketing;

import com.againspring.domain.marketing.XOpsAction;
import com.againspring.notification.TelegramNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One outbound reply per daytime 30-minute tick on followed accounts' recent
 * original posts. First reply hits the root; later replies thread under our previous reply.
 * Candidates are reordered so the least-recently-covered accounts go first, and an
 * account gets at most {@code marketing.x.outbound_per_account_daily_cap} replies per
 * KST day (and one per tick). Skips continue to the next candidate; at most
 * {@code marketing.x.outbound_per_tick} successful publishes per tick
 * (admin / {@code system_setting}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XOutboundService {

    private static final Duration RECENT_WINDOW = Duration.ofDays(7);

    private final MarketingXOpsSettingsService settingsService;
    private final AsmClient asmClient;
    private final XCommentComposer composer;
    private final OutboundDraftGuard outboundDraftGuard;
    private final XOpsActionLedger ledger;
    private final XCommentTraceRecorder commentTrace;
    private final TelegramNotifier telegramNotifier;

    @Value("${llm.enabled:true}")
    private boolean llmEnabled;

    public void run(Instant now) {
        MarketingXOpsSettingsService.XOpsSettings settings = settingsService.get();
        if (!settings.outboundEnabled() || !llmEnabled) {
            return;
        }
        if (ledger.countPostedToday(XOpsAction.Kind.OUTBOUND, now) >= settings.outboundDailyCap()) {
            return;
        }

        List<AsmClient.XOutboundCandidate> candidates;
        long fetchStarted = System.nanoTime();
        try {
            candidates = asmClient.listXOutboundCandidates(
                settings.hotMinReplies(), settings.hotMaxAgeHours());
        } catch (Exception e) {
            log.warn("[x-outbound] candidate fetch failed: {}", e.getMessage());
            return;
        }
        int n = candidates == null ? 0 : candidates.size();
        log.info("[x-outbound] fetched {} candidates in {}ms",
            n, (System.nanoTime() - fetchStarted) / 1_000_000L);
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        recordSeen(candidates, now);
        int accountCap = Math.max(1, settings.outboundPerAccountDailyCap());
        List<AsmClient.XOutboundCandidate> ordered = diversify(candidates, now);
        Map<String, XOpsActionLedger.AuthorPosted> today = ledger.postedByAuthorSince(
            handlesOf(candidates), XOpsActionLedger.startOfKstDay(now));
        Set<String> pickedThisTick = new HashSet<>();

        int perTick = Math.max(1, settings.outboundPerTick());
        int postedThisTick = 0;
        int accountCapSkipped = 0;
        for (AsmClient.XOutboundCandidate c : ordered) {
            if (postedThisTick >= perTick) {
                break;
            }
            if (ledger.countPostedToday(XOpsAction.Kind.OUTBOUND, now) >= settings.outboundDailyCap()) {
                break;
            }
            if (c == null || c.tweetId() == null || c.tweetId().isBlank()) {
                continue;
            }
            if (c.replyCount() < settings.hotMinReplies() || c.ageHours() > settings.hotMaxAgeHours()) {
                continue;
            }

            String replyTo = replyTarget(c);
            if (replyTo == null) {
                continue;
            }
            if (ledger.alreadyHandled(replyTo)) {
                continue;
            }

            String author = XOpsActionLedger.normHandle(c.authorHandle());
            if (author != null) {
                XOpsActionLedger.AuthorPosted done = today.get(author);
                int postedToday = done == null ? 0 : done.count();
                if (pickedThisTick.contains(author) || postedToday >= accountCap) {
                    accountCapSkipped++;
                    continue;
                }
            }

            if (c.hasVideo()) {
                ledger.recordOutboundSkipped(replyTo, "VIDEO", now, author);
                continue;
            }
            if (c.hasPhoto() && (c.photoJpegBase64() == null || c.photoJpegBase64().isBlank())) {
                ledger.recordOutboundSkipped(replyTo, "VISION_FAIL", now, author);
                continue;
            }

            String jpeg = c.hasPhoto() ? c.photoJpegBase64() : null;
            XCommentComposer.Draft draft = composer.composeOutbound(c.text(), c.peerReplies(), jpeg);
            if (draft == null || draft.skip() || draft.body() == null || draft.body().isBlank()) {
                String reason = draft != null && draft.skipReason() != null
                    ? draft.skipReason() : "UNSURE";
                remember(ledger.recordOutboundSkipped(replyTo, reason, now, author), draft, c);
                continue;
            }

            String guardHit = outboundDraftGuard.firstViolation(draft.body(), c.text(), c.peerReplies())
                .orElse(null);
            if (guardHit != null) {
                remember(ledger.recordOutboundSkipped(replyTo, guardHit, now, author), draft, c);
                continue;
            }

            try {
                AsmClient.XPublishResult result = asmClient.publishX(
                    draft.body(), replyTo, null, null);
                if (result != null && result.ok()) {
                    remember(ledger.recordOutboundPosted(
                        replyTo,
                        c.tweetId(),
                        result.tweetId(),
                        draft.body(),
                        now,
                        author), draft, c);
                    telegramNotifier.send(XOpsTelegramAlerts.posted(
                        "Justant-Bot 선댓글", result, replyTo, draft.body()));
                    postedThisTick++;
                    if (author != null) {
                        pickedThisTick.add(author);
                    }
                    continue;
                }
                remember(ledger.recordOutboundFailed(replyTo, "PUBLISH_FAILED", now, author), draft, c);
            } catch (Exception e) {
                log.warn("[x-outbound] publish failed tweetId={}: {}", replyTo, e.getMessage());
                remember(ledger.recordOutboundFailed(replyTo, "ASM_ERROR", now, author), draft, c);
            }
        }
        log.info("[x-outbound] posted={} accountCapSkipped={} accountCap={}",
            postedThisTick, accountCapSkipped, accountCap);
    }

    private void recordSeen(List<AsmClient.XOutboundCandidate> candidates, Instant now) {
        try {
            for (String h : handlesOf(candidates)) {
                ledger.recordCandidateSeen(h, now);
            }
        } catch (Exception e) {
            log.warn("[x-outbound] candidate-seen upsert failed: {}", e.getMessage());
        }
    }

    private static Set<String> handlesOf(List<AsmClient.XOutboundCandidate> candidates) {
        Set<String> out = new LinkedHashSet<>();
        for (AsmClient.XOutboundCandidate c : candidates) {
            String h = c == null ? null : XOpsActionLedger.normHandle(c.authorHandle());
            if (h != null) {
                out.add(h);
            }
        }
        return out;
    }

    /**
     * Least-recently-covered accounts first: fewest 7-day replies, then the
     * longest since our last reply, then the original order (stable).
     */
    List<AsmClient.XOutboundCandidate> diversify(List<AsmClient.XOutboundCandidate> candidates, Instant now) {
        Map<String, XOpsActionLedger.AuthorPosted> week = ledger.postedByAuthorSince(
            handlesOf(candidates), now.minus(RECENT_WINDOW));
        List<AsmClient.XOutboundCandidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
            .comparingInt((AsmClient.XOutboundCandidate c) -> weekCount(week, c))
            .thenComparing(c -> lastPosted(week, c)));
        return ordered;
    }

    private static int weekCount(Map<String, XOpsActionLedger.AuthorPosted> week, AsmClient.XOutboundCandidate c) {
        XOpsActionLedger.AuthorPosted p = c == null ? null : week.get(XOpsActionLedger.normHandle(c.authorHandle()));
        return p == null ? 0 : p.count();
    }

    private static Instant lastPosted(Map<String, XOpsActionLedger.AuthorPosted> week, AsmClient.XOutboundCandidate c) {
        XOpsActionLedger.AuthorPosted p = c == null ? null : week.get(XOpsActionLedger.normHandle(c.authorHandle()));
        return p == null || p.lastAt() == null ? Instant.EPOCH : p.lastAt();
    }

    private void remember(XOpsAction action, XCommentComposer.Draft draft, AsmClient.XOutboundCandidate c) {
        commentTrace.record(
            XOpsAction.Kind.OUTBOUND,
            action,
            draft,
            c.text(),
            XCommentComposer.joinPeers(c.peerReplies()),
            c.hasPhoto());
    }

    static String replyTarget(AsmClient.XOutboundCandidate c) {
        if (!c.alreadyRepliedByUs()) {
            return c.tweetId();
        }
        if (c.ourReplyTweetId() != null && !c.ourReplyTweetId().isBlank()) {
            return c.ourReplyTweetId();
        }
        return null;
    }
}
