package com.againspring.aiuser.orchestrator.admin;

import com.againspring.aiuser.orchestrator.engine.BehaviorEngine;
import com.againspring.aiuser.orchestrator.engine.PlannedAction;
import com.againspring.aiuser.orchestrator.repository.AiUserRuntimeRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.scheduler.PairedPostScheduler;
import com.againspring.aiuser.orchestrator.task.ActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final PersonaRepository personaRepo;
    private final ActionExecutor actionExecutor;
    private final JdbcTemplate jdbcTemplate;

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

    /**
     * 기존 게시글 댓글 좋아요 소급 적용 (비동기).
     * days: 최근 N일치 게시글 대상. personasPerPost: 게시글당 랜덤 샘플 페르소나 수.
     */
    @PostMapping("/backfill-comment-likes")
    public ResponseEntity<Map<String, Object>> backfillCommentLikes(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "8") int personasPerPost) {

        List<String> postIds = jdbcTemplate.queryForList(
            "SELECT id FROM posts WHERE deleted_at IS NULL AND created_at >= NOW() - INTERVAL ? DAY",
            String.class, days);

        if (postIds.isEmpty()) {
            return ResponseEntity.ok(Map.of("queued", 0, "message", "대상 게시글 없음"));
        }

        var personas = personaRepo.findByActiveTrue();
        if (personas.isEmpty()) {
            return ResponseEntity.ok(Map.of("queued", 0, "message", "활성 페르소나 없음"));
        }

        long queued = (long) postIds.size() * Math.min(personasPerPost, personas.size());
        log.info("[backfill-comment-likes] posts={} personas={} personasPerPost={} queued={}",
            postIds.size(), personas.size(), personasPerPost, queued);

        runBackfillAsync(postIds, personas, personasPerPost);
        return ResponseEntity.accepted().body(Map.of(
            "queued", queued,
            "posts", postIds.size(),
            "personasPerPost", personasPerPost,
            "message", "백그라운드 좋아요 백필을 시작했습니다. 완료까지 수 분이 소요될 수 있습니다."
        ));
    }

    @Async
    void runBackfillAsync(List<String> postIds, List<com.againspring.aiuser.orchestrator.domain.Persona> personas, int personasPerPost) {
        AtomicInteger processed = new AtomicInteger(0);
        for (String postId : postIds) {
            var shuffled = new java.util.ArrayList<>(personas);
            Collections.shuffle(shuffled);
            int take = Math.min(personasPerPost, shuffled.size());
            for (int i = 0; i < take; i++) {
                var persona = shuffled.get(i);
                try {
                    com.againspring.aiuser.orchestrator.client.dto.PostDto stub =
                        new com.againspring.aiuser.orchestrator.client.dto.PostDto();
                    stub.setId(postId);
                    actionExecutor.execute(persona, PlannedAction.commentLike(stub));
                } catch (Exception e) {
                    log.warn("[backfill-comment-likes] persona={} post={} error={}", persona.getId(), postId, e.getMessage());
                }
            }
            int done = processed.incrementAndGet();
            if (done % 50 == 0) {
                log.info("[backfill-comment-likes] progress {}/{}", done, postIds.size());
            }
        }
        log.info("[backfill-comment-likes] done: {} posts processed", processed.get());
    }

    /**
     * AI 신규 글 즉시 생성 (동기). count개를 HEAVY 페르소나(부족하면 활성 전체)로 생성한다.
     * tick의 POST 분기는 HEAVY 티어 + 희박 확률 + 1일1글이라 정확한 개수를 보장 못 하므로
     * executePost(=ActionExecutor.execute + PlannedAction.newPost)를 직접 호출한다.
     * 본문은 LLM 생성 + ContentSafetyGuard를 그대로 거친다(손수 작성 아님).
     */
    @PostMapping("/generate-posts")
    public ResponseEntity<Map<String, Object>> generatePosts(
            @RequestParam(defaultValue = "2") int count,
            @RequestParam(required = false) String voice) {

        int n = Math.max(1, Math.min(count, 10)); // 안전 상한
        var active = new java.util.ArrayList<>(personaRepo.findByActiveTrue());
        if (active.isEmpty()) {
            return ResponseEntity.ok(Map.of("attempted", 0, "message", "활성 페르소나 없음"));
        }
        // voice 필터: 특정 커뮤니티 타겟 생성 (예: ?voice=NATEPAN)
        if (voice != null && !voice.isBlank()) {
            String voiceUpper = voice.toUpperCase();
            active.removeIf(p -> {
                String vt = p.getVoiceProfile() != null
                    ? extractVoiceType(p.getVoiceProfile()) : null;
                return !voiceUpper.equals(vt);
            });
            if (active.isEmpty()) {
                return ResponseEntity.ok(Map.of("attempted", 0, "message", "해당 voice 활성 페르소나 없음: " + voice));
            }
        }
        var heavy = active.stream()
            .filter(p -> "HEAVY".equals(p.getTier()))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        List<com.againspring.aiuser.orchestrator.domain.Persona> pool =
            (heavy.size() >= n) ? heavy : active;
        Collections.shuffle(pool);

        int attempted = 0;
        List<String> personaIds = new java.util.ArrayList<>();
        for (int i = 0; i < n && i < pool.size(); i++) {
            var persona = pool.get(i);
            try {
                actionExecutor.execute(persona, PlannedAction.newPost());
                personaIds.add(persona.getId());
                attempted++;
            } catch (Exception e) {
                log.warn("[generate-posts] persona={} error={}", persona.getId(), e.getMessage());
            }
        }
        log.info("[generate-posts] {} post(s) attempted (count={})", attempted, n);
        return ResponseEntity.ok(Map.of(
            "attempted", attempted,
            "personaIds", personaIds,
            "message", attempted + "개 글 생성 시도 완료(LLM+세이프가드 통과분만 게시됨)."
        ));
    }

    private static String extractVoiceType(java.util.Map<String, Object> profile) {
        Object v = profile.get("voice_type");
        return v != null ? v.toString() : null;
    }

    /** AI 댓글 ㅠ{2,} → ㅠ 정규화 (synthetic=1 유저만) */
    @PostMapping("/cleanup-ㅠ")
    public ResponseEntity<Map<String, Object>> cleanupDoubleㅠ() {
        int updated = jdbcTemplate.update(
            "UPDATE post_comments pc " +
            "JOIN users u ON pc.author_id = u.id " +
            "SET pc.body = REGEXP_REPLACE(pc.body, 'ㅠ{2,}', 'ㅠ') " +
            "WHERE u.synthetic = 1 " +
            "  AND pc.deleted_at IS NULL " +
            "  AND pc.body REGEXP 'ㅠ{2,}'"
        );
        log.info("[cleanup-ㅠ] normalized double-ㅠ in {} AI comments", updated);
        return ResponseEntity.ok(Map.of("updated", updated));
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
