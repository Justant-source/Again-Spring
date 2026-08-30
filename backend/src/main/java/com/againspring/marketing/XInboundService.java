package com.againspring.marketing;

import com.againspring.domain.marketing.XOpsAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Replies to comments on our X posts: 3–25 min jitter, 30 min window, daily/per-post caps.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XInboundService {

    static final int INBOX_SINCE_MINUTES = 90;
    static final int REPLY_WINDOW_MINUTES = 30;
    static final int MAX_BATCH = 3;

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

        List<AsmClient.XInboxItem> inbox;
        try {
            inbox = asmClient.listXInbox(INBOX_SINCE_MINUTES);
        } catch (Exception e) {
            log.warn("[x-inbound] inbox fetch failed: {}", e.getMessage());
            return;
        }
        if (inbox == null || inbox.isEmpty()) {
            return;
        }

        int batch = 0;
        for (AsmClient.XInboxItem item : inbox) {
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

            XCommentComposer.Draft draft = composer.composeReply(
                item.text(), parentContext(item));
            if (draft == null || draft.skip() || draft.body() == null || draft.body().isBlank()) {
                String reason = draft != null && draft.skipReason() != null
                    ? draft.skipReason() : "NO_VOICE";
                ledger.recordSkipped(XOpsAction.Kind.INBOUND, item.tweetId(), reason, now);
                batch++;
                if (batch >= MAX_BATCH) {
                    return;
                }
                continue;
            }

            try {
                AsmClient.XPublishResult result = asmClient.publishX(
                    draft.body(), item.tweetId(), null, null);
                if (result != null && result.ok()) {
                    ledger.recordPosted(
                        XOpsAction.Kind.INBOUND,
                        item.tweetId(),
                        item.parentTweetId(),
                        item.ourPostTweetId(),
                        result.tweetId(),
                        draft.body(),
                        now);
                } else {
                    ledger.recordFailed(XOpsAction.Kind.INBOUND, item.tweetId(), "PUBLISH_FAILED", now);
                }
            } catch (Exception e) {
                log.warn("[x-inbound] publish failed tweetId={}: {}", item.tweetId(), e.getMessage());
                ledger.recordFailed(XOpsAction.Kind.INBOUND, item.tweetId(), "ASM_ERROR", now);
            }
            batch++;
            if (batch >= MAX_BATCH) {
                return;
            }
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

    private static String parentContext(AsmClient.XInboxItem item) {
        if (item.ourPostTweetId() != null && !item.ourPostTweetId().isBlank()) {
            return item.ourPostTweetId();
        }
        return item.parentTweetId();
    }
}
