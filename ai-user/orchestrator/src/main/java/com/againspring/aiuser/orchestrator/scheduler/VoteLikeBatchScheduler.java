package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.service.threadplan.VoteLikeBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * VOTE/LIKE 배치 스케줄러.
 *
 * 주기: 15분 (REPLY 30분보다 더 자주, 충분한 대상 게시글이 있어야 하므로)
 * 역할: VoteLikeBatchService.run() 호출 — PLAN 체계 내 VOTE/LIKE 생성 및 즉시 실행
 */
@Component
@RequiredArgsConstructor
public class VoteLikeBatchScheduler {

    private final VoteLikeBatchService service;

    @Scheduled(cron = "${ai-user.thread-plan.vote-like-cron:0 */15 * * * *}")
    public void run() {
        service.run();
    }
}
