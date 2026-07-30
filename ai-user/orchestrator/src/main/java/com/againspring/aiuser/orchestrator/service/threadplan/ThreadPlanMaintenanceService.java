package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanStatus;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;

/** Safe maintenance only: expires plans and accrues exposure. It never publishes an action. */
@Service
@RequiredArgsConstructor
public class ThreadPlanMaintenanceService {
    private static final EnumSet<ThreadPlanStatus> LIVE = EnumSet.of(
            ThreadPlanStatus.REQUESTED, ThreadPlanStatus.GENERATING, ThreadPlanStatus.READY,
            ThreadPlanStatus.ACTIVE, ThreadPlanStatus.PAUSED);
    private final AiThreadPlanRepository planRepository;
    private final ThreadPlanItemLeaseService itemLeaseService;
    private final HumanInteractionInboxService inboxService;

    @Transactional
    public MaintenanceResult maintain(Instant now, Map<Integer, Double> kstHourlyHumanWeights) {
        int expiredPlans = 0;
        for (AiThreadPlan plan : planRepository.findByStatusInAndAbsoluteExpiresAtBefore(LIVE, now)) {
            plan.setStatus(ThreadPlanStatus.EXPIRED);
            expiredPlans++;
        }
        // Only live plans accrue exposure. The provider of these weights is deliberately deferred.
        for (AiThreadPlan plan : planRepository.findByStatusIn(LIVE)) {
            if (plan.getPublishedAt() == null) continue;
            Instant previous = plan.getExposureCalculatedAt() == null ? plan.getPublishedAt() : plan.getExposureCalculatedAt();
            if (now.isAfter(previous)) {
                plan.setEffectiveExposureSeconds(plan.getEffectiveExposureSeconds()
                        + EffectiveExposureCalculator.weightedSeconds(previous, now, kstHourlyHumanWeights));
                plan.setExposureCalculatedAt(now);
            }
        }
        int recoveredItemLeases = itemLeaseService.recoverExpiredLeases(now);
        int recoveredOrExpiredInbox = inboxService.recoverAndExpire(now);
        return new MaintenanceResult(expiredPlans, recoveredItemLeases, recoveredOrExpiredInbox);
    }

    public record MaintenanceResult(int expiredPlans, int recoveredItemLeases, int recoveredOrExpiredInbox) { }
}
