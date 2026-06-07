package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.engine.planner.DailyPlanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyPlannerScheduler {

    private final DailyPlanner dailyPlanner;
    private final OrchestratorProperties props;

    @Scheduled(cron = "0 0 4 * * *")  // 04:00 KST (Asia/Seoul timezone)
    public void planDaily() {
        log.info("DailyPlannerScheduler.planDaily() triggered (enabled={})", props.isEnabled());
        try {
            dailyPlanner.planForToday();
        } catch (Exception e) {
            log.error("DailyPlanner.planForToday() threw exception: {}", e.getMessage(), e);
            // Do NOT rethrow
        }
    }
}
