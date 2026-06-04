package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.engine.BehaviorEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 유저 행동 마스터 cron 스케줄러.
 *
 * cron 주기: ${AI_USER_TICK_CRON} (기본 10분)
 * 실제 행동 여부는 BehaviorEngine이 kill-switch + 볼륨 쿼터로 결정.
 * 이 클래스는 순수하게 tick()을 트리거하는 역할만 담당.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestratorScheduler {

    private final BehaviorEngine behaviorEngine;
    private final OrchestratorProperties props;

    /**
     * Master tick — triggered by cron.
     * BehaviorEngine checks kill-switch internally; no need to check here.
     */
    @Scheduled(cron = "${ai-user.tick-cron:0 */10 * * * *}")
    public void tick() {
        log.debug("OrchestratorScheduler.tick() triggered (enabled={})", props.isEnabled());
        try {
            behaviorEngine.tick();
        } catch (Exception e) {
            log.error("BehaviorEngine.tick() threw unexpected exception: {}", e.getMessage(), e);
            // Do NOT rethrow — scheduled tasks that throw kill the scheduler thread
        }
    }
}
