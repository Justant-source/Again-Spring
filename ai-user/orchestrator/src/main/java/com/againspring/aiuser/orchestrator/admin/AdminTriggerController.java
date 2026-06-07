package com.againspring.aiuser.orchestrator.admin;

import com.againspring.aiuser.orchestrator.engine.BehaviorEngine;
import com.againspring.aiuser.orchestrator.repository.AiUserRuntimeRepository;
import com.againspring.aiuser.orchestrator.scheduler.PairedPostScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 개발·테스트용 수동 트리거 엔드포인트.
 * Docker 내부 네트워크에서만 접근 가능 (외부 노출 없음).
 */
@Slf4j
@RestController
@RequestMapping("/admin/trigger")
@RequiredArgsConstructor
public class AdminTriggerController {

    private final BehaviorEngine behaviorEngine;
    private final PairedPostScheduler pairedPostScheduler;
    private final AiUserRuntimeRepository runtimeRepo;

    /** BehaviorEngine 즉시 tick — 좋아요/댓글/투표/게시 1회 실행 */
    @PostMapping("/tick")
    public ResponseEntity<Map<String, Object>> triggerTick() {
        log.info("[AdminTrigger] Manual tick requested");
        try {
            behaviorEngine.tick();
            return ResponseEntity.ok(Map.of("status", "ok", "action", "tick"));
        } catch (Exception e) {
            log.error("[AdminTrigger] tick failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /** PairedPostScheduler 즉시 실행 — 갈등 사연 페어 생성 */
    @PostMapping("/paired-posts")
    public ResponseEntity<Map<String, Object>> triggerPairedPosts() {
        log.info("[AdminTrigger] Manual paired-posts requested");
        try {
            pairedPostScheduler.triggerNow();
            return ResponseEntity.ok(Map.of("status", "ok", "action", "paired-posts"));
        } catch (Exception e) {
            log.error("[AdminTrigger] paired-posts failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /** actions_today 카운터 리셋 — daily cap 초과 시 당일 재활성화 */
    @PostMapping("/reset-counter")
    public ResponseEntity<Map<String, Object>> resetCounter() {
        log.info("[AdminTrigger] Manual counter reset requested");
        return runtimeRepo.findById(1).<ResponseEntity<Map<String, Object>>>map(rt -> {
            int prev = rt.getActionsToday();
            rt.setActionsToday(0);
            rt.setUpdatedAt(Instant.now());
            runtimeRepo.save(rt);
            log.info("[AdminTrigger] Counter reset: {} → 0", prev);
            return ResponseEntity.ok(Map.of("status", "ok", "prev", (Object) prev, "now", (Object) 0));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** daily_global_cap 변경 — 재배포 없이 일일 한도 조정 */
    @PostMapping("/update-cap")
    public ResponseEntity<Map<String, Object>> updateCap(@RequestParam int cap) {
        if (cap < 1 || cap > 10000) {
            return ResponseEntity.badRequest()
                .body(Map.of("status", "error", "message", "cap must be between 1 and 10000"));
        }
        log.info("[AdminTrigger] Update daily_global_cap requested: {}", cap);
        return runtimeRepo.findById(1).<ResponseEntity<Map<String, Object>>>map(rt -> {
            int prev = rt.getDailyGlobalCap();
            rt.setDailyGlobalCap(cap);
            rt.setUpdatedAt(Instant.now());
            runtimeRepo.save(rt);
            log.info("[AdminTrigger] daily_global_cap: {} → {}", prev, cap);
            return ResponseEntity.ok(Map.of("status", "ok", "prev", (Object) prev, "now", (Object) cap));
        }).orElse(ResponseEntity.notFound().build());
    }
}
