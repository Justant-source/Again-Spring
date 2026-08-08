package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.engine.planner.DailyPlanner;
import com.againspring.aiuser.orchestrator.service.planner.DailyPlannerRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyPlannerScheduler {

    private final DailyPlanner dailyPlanner;
    private final DailyPlannerRetryService retryService;
    private final OrchestratorProperties props;

    @Scheduled(cron = "0 0 4 * * *")  // 04:00 KST (Asia/Seoul timezone)
    public void planDaily() {
        log.info("DailyPlannerScheduler.planDaily() triggered (enabled={})", props.isEnabled());
        if (!props.isEnabled()) {
            log.info("DailyPlannerScheduler.planDaily() skipped: AI_USER_ENABLED=false");
            return;
        }
        try {
            dailyPlanner.planForToday();
        } catch (Exception e) {
            log.error("DailyPlanner.planForToday() threw exception: {}", e.getMessage(), e);
            // DB에 실패 기록 (재시도 스케줄러가 30분 뒤에 처리)
            retryService.recordInitialFailure(e);
        }
    }
}
