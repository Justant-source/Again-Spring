package com.againspring.marketing;

import com.againspring.domain.marketing.XOpsAction;
import com.againspring.notification.TelegramNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * One outbound reply per daytime 30-minute tick on followed accounts' recent
 * original posts. First reply hits the root; later replies thread under our previous reply.
 * Skips continue to the next candidate; at most
 * {@code marketing.x.outbound_per_tick} successful publishes per tick
 * (admin / {@code system_setting}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XOutboundService {

    private final MarketingXOpsSettingsService settingsService;
    private final AsmClient asmClient;
    private final XCommentComposer composer;
    private final OutboundDraftGuard outboundDraftGuard;
    private final XOpsActionLedger ledger;
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

        int perTick = Math.max(1, settings.outboundPerTick());
        int postedThisTick = 0;
        for (AsmClient.XOutboundCandidate c : candidates) {
            if (postedThisTick >= perTick) {
                return;
            }
            if (ledger.countPostedToday(XOpsAction.Kind.OUTBOUND, now) >= settings.outboundDailyCap()) {
                return;
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

            if (c.hasVideo()) {
                ledger.recordSkipped(XOpsAction.Kind.OUTBOUND, replyTo, "VIDEO", now);
                continue;
            }
            if (c.hasPhoto() && (c.photoJpegBase64() == null || c.photoJpegBase64().isBlank())) {
                ledger.recordSkipped(XOpsAction.Kind.OUTBOUND, replyTo, "VISION_FAIL", now);
                continue;
            }

            String jpeg = c.hasPhoto() ? c.photoJpegBase64() : null;
            XCommentComposer.Draft draft = composer.composeOutbound(c.text(), c.peerReplies(), jpeg);
            if (draft == null || draft.skip() || draft.body() == null || draft.body().isBlank()) {
                String reason = draft != null && draft.skipReason() != null
                    ? draft.skipReason() : "UNSURE";
                ledger.recordSkipped(XOpsAction.Kind.OUTBOUND, replyTo, reason, now);
                continue;
            }

            String guardHit = outboundDraftGuard.firstViolation(draft.body(), c.text(), c.peerReplies())
                .orElse(null);
            if (guardHit != null) {
                ledger.recordSkipped(XOpsAction.Kind.OUTBOUND, replyTo, guardHit, now);
                continue;
            }

            try {
                AsmClient.XPublishResult result = asmClient.publishX(
                    draft.body(), replyTo, null, null);
                if (result != null && result.ok()) {
                    ledger.recordPosted(
                        XOpsAction.Kind.OUTBOUND,
                        replyTo,
                        c.tweetId(),
                        null,
                        result.tweetId(),
                        draft.body(),
                        now);
                    telegramNotifier.send(XOpsTelegramAlerts.posted(
                        "Justant-Bot 선댓글", result, replyTo, draft.body()));
                    postedThisTick++;
                    continue;
                }
                ledger.recordFailed(XOpsAction.Kind.OUTBOUND, replyTo, "PUBLISH_FAILED", now);
            } catch (Exception e) {
                log.warn("[x-outbound] publish failed tweetId={}: {}", replyTo, e.getMessage());
                ledger.recordFailed(XOpsAction.Kind.OUTBOUND, replyTo, "ASM_ERROR", now);
            }
        }
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
