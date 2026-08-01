package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanStatus;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Backlog TTL for human-interaction inbox and stuck REQUESTED plans (§2.9 / Wave1-I).
 * Destructive transitions stay behind {@code ai-user.human-reply.ttl-cleanup-enabled}
 * (default false). Admin may pass {@code force=true} for a one-shot run without flipping the flag.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HumanReplyTtlCleanupService {
    private final OrchestratorProperties props;
    private final HumanInteractionInboxService inboxService;
    private final AiThreadPlanRepository planRepository;

    @Transactional
    public CleanupResult run(Instant now, boolean force) {
        OrchestratorProperties.HumanReply cfg = props.getHumanReply();
        if (!force && !cfg.isTtlCleanupEnabled()) {
            log.debug("human-reply TTL cleanup skipped (flag off)");
            return CleanupResult.skipped();
        }
        int reclaimed = inboxService.reclaimStuckProcessing();
        Instant inboxCutoff = now.minus(Math.max(1, cfg.getInboxTtlDays()), ChronoUnit.DAYS);
        int inboxCancelled = inboxService.cancelExpiredByObservedAt(inboxCutoff);
        Instant planCutoff = now.minus(Math.max(1, cfg.getPlanTtlDays()), ChronoUnit.DAYS);
        int plansExpired = planRepository.expireRequestedOlderThan(
                ThreadPlanStatus.REQUESTED,
                ThreadPlanStatus.EXPIRED,
                HumanInteractionInboxService.REASON_EXPIRED_TTL,
                planCutoff);
        log.info("human-reply TTL cleanup: reclaimed={}, inboxCancelled={}, plansExpired={}, force={}",
                reclaimed, inboxCancelled, plansExpired, force);
        return new CleanupResult(true, reclaimed, inboxCancelled, plansExpired);
    }

    public record CleanupResult(boolean ran, int reclaimedProcessing, int inboxCancelled, int plansExpired) {
        static CleanupResult skipped() { return new CleanupResult(false, 0, 0, 0); }
    }
}
