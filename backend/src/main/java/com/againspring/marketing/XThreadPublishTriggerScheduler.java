package com.againspring.marketing;

import com.againspring.marketing.holding.MarketingHoldingCommitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Unattended T+24h marketing auto-publish trigger.
 *
 * <p>Gates: {@code asm.x-thread-publish-trigger-enabled}, ASM enabled,
 * and a valid {@code asm.auto-publish-since}. Commit / distribution rule C
 * lives in {@link MarketingHoldingCommitService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XThreadPublishTriggerScheduler {

    private final MarketingHoldingCommitService holdingCommitService;
    private final AsmProperties asmProperties;

    /**
     * Opt-in gate, separate from {@code asmProperties.isEnabled()}.
     *
     * ASM is a single instance shared by dev and prod (one WSL box, one X/IG account —
     * see docs/shared/marketing/10-context.md). This scheduler is fully unattended; if it's
     * on, it publishes to the real accounts. Defaults to false so a plain dev redeploy
     * can never auto-publish. Set true only where auto-publish is intentional.
     */
    @Value("${asm.x-thread-publish-trigger-enabled:false}")
    private boolean triggerEnabled;

    /**
     * Poll for holdings past the 24h mark and run the commit pipeline.
     * Interval default: 10 minutes.
     */
    @Scheduled(fixedDelayString = "${asm.x-thread-poll-interval-ms:600000}")
    public void pollAndPublishToXThread() {
        if (!triggerEnabled) {
            log.debug("Marketing auto-publish trigger is disabled (asm.x-thread-publish-trigger-enabled=false), skipping");
            return;
        }
        if (!asmProperties.isEnabled()) {
            log.debug("ASM is disabled, skipping marketing auto-publish trigger");
            return;
        }

        Instant since = parseAutoPublishSince(asmProperties.getAutoPublishSince());
        if (since == null) {
            log.warn("Marketing auto-publish trigger is on but asm.auto-publish-since is unset/invalid — fail-closed, skipping");
            return;
        }

        try {
            holdingCommitService.runCommitTick(since);
        } catch (Exception e) {
            log.error("Error in marketing auto-publish trigger scheduler", e);
        }
    }

    /** Blank/null/unparseable → null (caller fail-closes). Accepts ISO-8601 Instant. */
    static Instant parseAutoPublishSince(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
