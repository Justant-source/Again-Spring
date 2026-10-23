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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Replies to comments on our X posts: 3–25 min jitter, 30 min window, daily/per-post caps.
 * Shares the daily budget with outbound ({@link XDailyQuota}): inbound + outbound ≤ today's target.
 * One reply per call with a 1–7 min pause; the inbox is scraped every 4–9 min.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XInboundService {

    static final int INBOX_SINCE_MINUTES = 24 * 60;
    static final int REPLY_WINDOW_MINUTES = 20 * 60;

    private static final Pattern URLS = Pattern.compile(
        "(?i)(https?://\\S+|www\\.\\S+|t\\.co/\\S+)");
    private static final Pattern FOLLOW_BAIT = Pattern.compile(
        "(?i)(맞팔|선팔|맞구독|follow\\s*for\\s*follow|follow\\s*4\\s*follow|follow4follow|\\bf4f\\b)");
    private static final Pattern ABUSIVE = Pattern.compile(
        "씨발|시발|좆|개새끼|지랄|꺼져|죽어|병신");

    private final MarketingXOpsSettingsService settingsService;
    private final AsmClient asmClient;
    private final XCommentComposer composer;
    private final XOpsActionLedger ledger;
    private final XCommentTraceRecorder commentTrace;
    private final TelegramNotifier telegramNotifier;
    private final XDailyQuota dailyQuota;

    private List<AsmClient.XInboxItem> inboxPool = new ArrayList<>();
    private Instant nextFetchAt;
    private Instant nextReplyAt;

    @Value("${llm.enabled:true}")
    private boolean llmEnabled;

    public void run(Instant now) {
        MarketingXOpsSettingsService.XOpsSettings settings = settingsService.get();
        if (!settings.inboundEnabled()) {
            return;
        }
        if (!llmEnabled) {
            return;
        }

        if (XHumanPacer.weight(now) <= 0) {
            return; // asleep
        }
        if (nextReplyAt != null && now.isBefore(nextReplyAt)) {
            return;
        }
        int total = ledger.countPostedToday(XOpsAction.Kind.INBOUND, now)
            + ledger.countPostedToday(XOpsAction.Kind.OUTBOUND, now);
        int cap = Math.min(dailyQuota.targetFor(now), settings.outboundDailyCap());
        if (total >= cap) {
            return;
        }

        // The inbox scrape is a full Playwright pass — poll every 4–9 min, not every minute.
        if (nextFetchAt == null || !now.isBefore(nextFetchAt)) {
            try {
                List<AsmClient.XInboxItem> fresh = asmClient.listXInbox(INBOX_SINCE_MINUTES);
                inboxPool = fresh == null ? new ArrayList<>() : new ArrayList<>(fresh);
                nextFetchAt = now.plus(Duration.ofSeconds(
                    240 + ThreadLocalRandom.current().nextInt(0, 301)));
            } catch (Exception e) {
                log.warn("[x-inbound] inbox fetch failed: {}", e.getMessage());
                nextFetchAt = now.plus(Duration.ofMinutes(3));
                return;
            }
        }
        if (inboxPool.isEmpty()) {
            return;
        }

        for (AsmClient.XInboxItem item : inboxPool) {
            if (item == null || item.tweetId() == null || item.tweetId().isBlank()) {
                continue;
            }
            if (ledger.alreadyHandled(item.tweetId())) {
                continue;
            }
            if (looksLikeUnwantedInbound(item.text())) {
                ledger.recordSkipped(XOpsAction.Kind.INBOUND, item.tweetId(), "SAFETY", now);
                continue;
            }
            if (!inJitterWindow(item, now)) {
                continue;
            }
            if (ledger.countPostedToday(XOpsAction.Kind.INBOUND, now) >= settings.inboundDailyCap()) {
                return;
            }
            String ourPost = item.ourPostTweetId();
            if (ourPost != null && !ourPost.isBlank()
                && ledger.countPostedTodayForOurPost(ourPost, now) >= settings.inboundPerPostCap()) {
                ledger.recordSkipped(XOpsAction.Kind.INBOUND, item.tweetId(), "CAP", now);
                continue;
            }

            String context = parentContext(item);
            XCommentComposer.Draft draft = composer.composeReply(item.text(), context);
            if (draft == null || draft.skip() || draft.body() == null || draft.body().isBlank()) {
                String reason = draft != null && draft.skipReason() != null
                    ? draft.skipReason() : "NO_VOICE";
                remember(ledger.recordSkipped(XOpsAction.Kind.INBOUND, item.tweetId(), reason, now),
                    draft, item, context);
                nextReplyAt = now.plusSeconds(20 + ThreadLocalRandom.current().nextInt(0, 41));
                return;
            }

            try {
                AsmClient.XPublishResult result = publishWithRetry(
                    draft.body(), item.tweetId());
                if (result != null && result.ok()) {
                    remember(ledger.recordPosted(
                        XOpsAction.Kind.INBOUND,
                        item.tweetId(),
                        item.parentTweetId(),
                        item.ourPostTweetId(),
                        result.tweetId(),
                        draft.body(),
                        now), draft, item, context);
                    telegramNotifier.send(XOpsTelegramAlerts.posted(
                        "Justant-Bot 대댓글", result, item.tweetId(), draft.body()));
                } else {
                    remember(ledger.recordFailed(
                        XOpsAction.Kind.INBOUND, item.tweetId(), "PUBLISH_FAILED", now),
                        draft, item, context);
                }
            } catch (Exception e) {
                log.warn("[x-inbound] publish failed tweetId={}: {}", item.tweetId(), e.getMessage());
                remember(ledger.recordFailed(
                    XOpsAction.Kind.INBOUND, item.tweetId(), "ASM_ERROR", now),
                    draft, item, context);
            }
            // One reply per call, then a human-ish pause (1–7 min) before the next one.
            nextReplyAt = now.plusSeconds(60 + ThreadLocalRandom.current().nextInt(0, 361));
            return;
        }
    }

    static int jitterMinutes(String tweetId) {
        return 3 + Math.floorMod(stableHash(tweetId), 23);
    }

    static int stableHash(String tweetId) {
        if (tweetId == null) {
            return 0;
        }
        int h = 0;
        for (int i = 0; i < tweetId.length(); i++) {
            h = 31 * h + tweetId.charAt(i);
        }
        return h;
    }

    static boolean looksLikeUnwantedInbound(String text) {
        if (text == null) {
            return true;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return true;
        }
        String stripped = URLS.matcher(t).replaceAll("").trim();
        if (stripped.isEmpty()) {
            return true;
        }
        if (FOLLOW_BAIT.matcher(t).find()) {
            return true;
        }
        return ABUSIVE.matcher(t).find();
    }

    private static boolean inJitterWindow(AsmClient.XInboxItem item, Instant now) {
        if (item.createdAt() == null) {
            return false;
        }
        Instant earliest = item.createdAt().plus(Duration.ofMinutes(jitterMinutes(item.tweetId())));
        Instant latest = item.createdAt().plus(Duration.ofMinutes(REPLY_WINDOW_MINUTES));
        return !now.isBefore(earliest) && !now.isAfter(latest);
    }

    private void remember(
            XOpsAction action, XCommentComposer.Draft draft, AsmClient.XInboxItem item, String context) {
        commentTrace.record(
            XOpsAction.Kind.INBOUND, action, draft, item.text(), context, false);
    }

    private static String parentContext(AsmClient.XInboxItem item) {
        if (item.ourPostTweetId() != null && !item.ourPostTweetId().isBlank()) {
            return item.ourPostTweetId();
        }
        return item.parentTweetId();
    }

    /** Pause before the single publish retry (X sometimes refuses the first compose). */
    long retryPauseMillis = 25_000L;

    private AsmClient.XPublishResult publishWithRetry(String body, String replyTo) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                AsmClient.XPublishResult r = asmClient.publishX(body, replyTo, null, null);
                if (r != null && r.ok()) {
                    return r;
                }
                last = null;
                if (attempt == 2) {
                    return r;
                }
            } catch (RuntimeException e) {
                last = e;
                if (attempt == 2) {
                    throw e;
                }
            }
            log.warn("[x-publish] attempt {} failed for {}, retrying once", attempt, replyTo);
            try {
                Thread.sleep(retryPauseMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                if (last != null) {
                    throw last;
                }
                return null;
            }
        }
        return null;
    }
}
