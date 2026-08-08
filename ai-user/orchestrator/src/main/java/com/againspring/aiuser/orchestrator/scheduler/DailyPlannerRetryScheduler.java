package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.service.planner.DailyPlannerRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyPlannerRetryScheduler {

    private final DailyPlannerRetryService retryService;
    private final OrchestratorProperties props;

    /**
     * 원본 planDaily() 실패 30분 후 자동 재시도.
     * 04:00에 실패 → 04:30에 이 메서드 실행
     * cron: "0 30 4 * * *"  (KST 04:30)
     */
    @Scheduled(cron = "0 30 4 * * *")  // 04:30 KST (Asia/Seoul timezone)
    public void retryFailedPlanDaily() {
        log.info("DailyPlannerRetryScheduler.retryFailedPlanDaily() triggered (enabled={})", props.isEnabled());
        if (!props.isEnabled()) {
            log.info("DailyPlannerRetryScheduler.retryFailedPlanDaily() skipped: AI_USER_ENABLED=false");
            return;
        }
        try {
            retryService.checkAndRetryFailedYesterday();
        } catch (Exception e) {
            log.error("DailyPlannerRetryService.checkAndRetryFailedYesterday() threw exception: {}",
                e.getMessage(), e);
            // 재시도 스케줄러도 예외를 삼킴 (무한 루프 방지)
        }
    }
}
