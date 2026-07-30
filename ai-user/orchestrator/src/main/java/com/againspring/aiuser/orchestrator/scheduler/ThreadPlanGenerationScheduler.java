package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.service.threadplan.ThreadPlanGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j @Component @RequiredArgsConstructor
public class ThreadPlanGenerationScheduler {
    private final ThreadPlanGenerationService generationService;
    private final OrchestratorProperties properties;
    @Scheduled(cron = "${ai-user.thread-plan.generation-cron:15 * * * * *}")
    public void generate() {
        try { generationService.generateRequestedPlans(); }
        catch (Exception e) { log.error("Thread plan generation tick failed", e); }
    }
}
