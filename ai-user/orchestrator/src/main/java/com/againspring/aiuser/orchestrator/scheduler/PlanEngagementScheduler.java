package com.againspring.aiuser.orchestrator.scheduler;
import com.againspring.aiuser.orchestrator.service.engagement.PlanEngagementDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A throwing @Scheduled method kills the scheduler thread — never let reconcileDue() propagate. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanEngagementScheduler {
    private final PlanEngagementDispatcher dispatcher;

    @Scheduled(cron = "${ai-user.thread-plan.engagement.engagement-cron:0 */5 * * * *}")
    public void run() {
        try {
            dispatcher.reconcileDue();
        } catch (Exception e) {
            log.error("PlanEngagementDispatcher.reconcileDue() threw unexpected exception: {}", e.getMessage(), e);
        }
    }
}
