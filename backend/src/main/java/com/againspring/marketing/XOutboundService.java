package com.againspring.marketing;

import com.againspring.domain.marketing.XOpsAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * One outbound reply per tick on hot mutual posts. First reply hits the root;
 * later replies thread under our previous reply. Does not force-fill the daily cap.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XOutboundService {

    private final MarketingXOpsSettingsService settingsService;
    private final AsmClient asmClient;
    private final XCommentComposer composer;
    private final XOpsActionLedger ledger;

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
        try {
            candidates = asmClient.listXOutboundCandidates(
                settings.hotMinReplies(), settings.hotMaxAgeHours());
        } catch (Exception e) {
            log.warn("[x-outbound] candidate fetch failed: {}", e.getMessage());
            return;
        }
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        for (AsmClient.XOutboundCandidate c : candidates) {
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

            XCommentComposer.Draft draft = composer.composeReply(c.text(), c.tweetId());
            if (draft == null || draft.skip() || draft.body() == null || draft.body().isBlank()) {
                String reason = draft != null && draft.skipReason() != null
                    ? draft.skipReason() : "NO_VOICE";
                ledger.recordSkipped(XOpsAction.Kind.OUTBOUND, replyTo, reason, now);
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
                } else {
                    ledger.recordFailed(XOpsAction.Kind.OUTBOUND, replyTo, "PUBLISH_FAILED", now);
                }
            } catch (Exception e) {
                log.warn("[x-outbound] publish failed tweetId={}: {}", replyTo, e.getMessage());
                ledger.recordFailed(XOpsAction.Kind.OUTBOUND, replyTo, "ASM_ERROR", now);
            }
            return;
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
