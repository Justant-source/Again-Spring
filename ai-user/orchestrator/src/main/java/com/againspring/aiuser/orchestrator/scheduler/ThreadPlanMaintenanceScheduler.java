package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.service.threadplan.ThreadPlanMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Foundation maintenance scheduler. It is intentionally unable to invoke an LLM
 * or call BackendBotClient; a publisher will be wired in a later change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThreadPlanMaintenanceScheduler {
    private final ThreadPlanMaintenanceService maintenanceService;
    private final OrchestratorProperties properties;

    @Scheduled(cron = "${ai-user.thread-plan.maintenance-cron:0 */5 * * * *}")
    public void maintain() {
        if (!properties.isEnabled() || !properties.getThreadPlan().isMaintenanceEnabled()) return;
        try {
            maintenanceService.maintain(Instant.now(), properties.getThreadPlan().getKstHourlyHumanWeights());
        } catch (Exception e) {
            log.error("Thread-plan maintenance failed: {}", e.getMessage(), e);
        }
    }
}
