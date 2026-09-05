package com.againspring.aiuser.orchestrator.admin;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.LlmGenerationGate;
import com.againspring.aiuser.orchestrator.engine.PlannedAction;
import com.againspring.aiuser.orchestrator.persona.PersonaProfileRegenerator;
import com.againspring.aiuser.orchestrator.persona.PersonaRelationshipFiller;
import com.againspring.aiuser.orchestrator.engine.ViewDispatcher;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserRuntimeRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.scheduler.PairedPostScheduler;
import com.againspring.aiuser.orchestrator.service.engagement.PlanEngagementDispatcher;
import com.againspring.aiuser.orchestrator.service.gate.EffectiveGatesService;
import com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService;
import com.againspring.aiuser.orchestrator.service.threadplan.ActivityCurve;
import com.againspring.aiuser.orchestrator.service.threadplan.HumanReplyTtlCleanupService;
import com.againspring.aiuser.orchestrator.service.threadplan.LlmCallBudget;
import com.againspring.aiuser.orchestrator.service.threadplan.NightlyScheduledFillService;
import com.againspring.aiuser.orchestrator.service.threadplan.ScheduledPostPublisher;
import com.againspring.aiuser.orchestrator.service.threadplan.ThreadPlanGenerationService;
import com.againspring.aiuser.orchestrator.task.ActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    private final PairedPostScheduler pairedPostScheduler;
    private final AiUserRuntimeRepository runtimeRepo;
    private final PersonaRepository personaRepo;
    private final ActionExecutor actionExecutor;
    private final JdbcTemplate jdbcTemplate;
    private final OrchestratorProperties properties;
    private final NightlyScheduledFillService nightlyScheduledFillService;
    private final AiScheduledPostRepository scheduledPostRepository;
    private final PlanEngagementDispatcher engagementDispatcher;
    private final ViewDispatcher viewDispatcher;
    private final HumanReplyTtlCleanupService humanReplyTtlCleanupService;
    private final ThreadPlanGenerationService threadPlanGenerationService;
    private final LlmGenerationGateService llmGenerationGateService;
    private final EffectiveGatesService effectiveGatesService;
    private final ScheduledPostPublisher scheduledPostPublisher;
    private final PersonaProfileRegenerator personaProfileRegenerator;
    private final PersonaRelationshipFiller personaRelationshipFiller;

    /**
     * KST 시간대 곡선으로 발행 슬롯 N개를 샘플링만 해서 보여준다(부작용 없음).
     * 재배치/야간배치 계획 수립용 — 실제 코드가 쓰는 것과 동일한 ActivityCurve를 그대로 호출한다.
     */
    @GetMapping("/sample-slots")
    public ResponseEntity<Map<String, Object>> sampleSlots(
            @RequestParam(defaultValue = "8") int count,
            @RequestParam(defaultValue = "8") int fromHour,
            @RequestParam(defaultValue = "22") int toHour,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "45") long minSpacingMinutes) {
        ZoneId kst = ActivityCurve.KST;
        LocalDate day = date != null ? LocalDate.parse(date) : LocalDate.now(kst);
        Instant from = day.atStartOfDay(kst).plusHours(fromHour).toInstant();
        Instant to = day.atStartOfDay(kst).plusHours(toHour).toInstant();
        try {
            List<Instant> slots = ActivityCurve.sampleFutureInstants(from, to, count,
                    properties.getThreadPlan().getKstHourlyHumanWeights(),
                    Duration.ofMinutes(minSpacingMinutes), new Random());
            List<String> kstStrings = slots.stream().map(i -> i.atZone(kst).toString()).toList();
            return ResponseEntity.ok(Map.of("slotsKst", kstStrings, "slotsUtc", slots));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Manual/nightly solo fill: retry until {@code count} rows are saved or LLM cap {@code 3*count}.
     * Empty claims retry other plaza/source/persona and do not count against the cap.
     */
    @PostMapping("/generate-scheduled-posts")
    public ResponseEntity<Map<String, Object>> generateScheduledPosts(
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(defaultValue = "8") int fromHour,
            @RequestParam(defaultValue = "22") int toHour,
            @RequestParam(defaultValue = "45") long minSpacingMinutes,
            @RequestParam(defaultValue = "false") boolean skipSourceClaim) {
        int n = Math.max(1, Math.min(count, 100));
        NightlyScheduledFillService.FillResult result = nightlyScheduledFillService.fillSolo(
                n, fromHour, toHour, minSpacingMinutes,
                LlmCallBudget.ofMultiplier(n, NightlyScheduledFillService.LLM_CAP_MULTIPLIER), skipSourceClaim);
        if (result.error() != null && result.error().startsWith("슬롯 샘플링 실패")) {
            return ResponseEntity.badRequest().body(Map.of("error", result.error()));
        }
        log.info("[generate-scheduled-posts] saved={}/{} attempted={} llm={}/{} failures={}",
                result.saved(), n, result.attempted(), result.llmUsed(), result.llmMax(), result.failures().size());
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("attempted", result.attempted());
        body.put("saved", result.saved());
        body.put("scheduledIds", result.scheduledIds());
        body.put("llmUsed", result.llmUsed());
        body.put("llmMax", result.llmMax());
        body.put("failures", result.failureReasons());
        body.put("message", result.saved() + "개 예약글 저장(목표 " + n + ", LLM " + result.llmUsed() + "/" + result.llmMax() + ").");
        return ResponseEntity.ok(body);
    }

    /**
     * Combined nightly fill: paired first, leftover as solo, LLM cap {@code 3*target_posts}.
     * Telegram only when saved &lt; N.
     */
    @PostMapping("/fill-nightly-scheduled-posts")
    public ResponseEntity<Map<String, Object>> fillNightlyScheduledPosts(
            @RequestParam(defaultValue = "8") int fromHour,
            @RequestParam(defaultValue = "22") int toHour,
            @RequestParam(defaultValue = "45") long minSpacingMinutes) {
        NightlyScheduledFillService.FillResult result = nightlyScheduledFillService.fillNightly(
                fromHour, toHour, minSpacingMinutes, true);
        if (result.error() != null && result.error().startsWith("슬롯 샘플링 실패")) {
            return ResponseEntity.badRequest().body(Map.of("error", result.error()));
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("target", result.target());
        body.put("saved", result.saved());
        body.put("attempted", result.attempted());
        body.put("pairedSaved", result.pairedSaved());
        body.put("soloSaved", result.soloSaved());
        body.put("scheduledIds", result.scheduledIds());
        body.put("llmUsed", result.llmUsed());
        body.put("llmMax", result.llmMax());
        body.put("failures", result.failureReasons());
        body.put("message", "nightly fill saved " + result.saved() + "/" + result.target());
        return ResponseEntity.ok(body);
    }

    /** PairedPostScheduler 즉시 실행 — 양면 사연(작성자+상대방) 생성. count>0이면 최대 N쌍. */
    @PostMapping("/paired-posts")
    public ResponseEntity<Map<String, Object>> triggerPairedPosts(
            @RequestParam(required = false) Integer count) {
        log.info("[AdminTrigger] Manual paired-posts requested count={}", count);
        try {
            int attempted = (count != null && count > 0)
                ? pairedPostScheduler.triggerNow(count)
                : pairedPostScheduler.triggerNow();
            return ResponseEntity.ok(Map.of(
                "status", "ok",
                "action", "paired-posts",
                "attempted", attempted,
                "requested", count != null ? count : 0));
        } catch (Exception e) {
            log.error("[AdminTrigger] paired-posts failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * 이미 공개된 양면 사연에 댓글 PLAN이 비어 있을 때 강제 생성.
     * provider DB gate가 OFF여도 yml fallback으로 생성한다.
     */
    @PostMapping("/ensure-paired-comment-plan")
    public ResponseEntity<Map<String, Object>> ensurePairedCommentPlan(@RequestParam String postId) {
        log.info("[AdminTrigger] ensure-paired-comment-plan postId={}", postId);
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT title, body_published, partner_body_published, category, content_revision " +
                "FROM posts WHERE id = ? AND partner_answered_at IS NOT NULL",
                postId);
            String title = stringVal(row.get("title"));
            String authorBody = stringVal(row.get("body_published"));
            String partnerBody = stringVal(row.get("partner_body_published"));
            String category = stringVal(row.get("category"));
            int revision = ((Number) row.get("content_revision")).intValue();
            boolean ok = threadPlanGenerationService.ensureCommentPlanForPairedPost(
                postId, revision, title, authorBody, partnerBody, category);
            return ResponseEntity.ok(Map.of(
                "status", ok ? "ok" : "incomplete",
                "action", "ensure-paired-comment-plan",
                "postId", postId,
                "revision", revision));
        } catch (Exception e) {
            log.error("[AdminTrigger] ensure-paired-comment-plan failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    /**
     * 조회수만 즉시 보정 (ViewDispatcher.dispatchViews() 직접 호출, LEGACY tick 무관하게 단독 실행).
     */
    @PostMapping("/dispatch-views")
    public ResponseEntity<Map<String, Object>> dispatchViews() {
        int updated = viewDispatcher.dispatchViews();
        log.info("[AdminTrigger] dispatch-views: {} posts updated", updated);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    /**
     * 댓글/대댓글 좋아요·글 좋아요·투표·조회수 리콘실 — PlanEngagementDispatcher.reconcile()의
     * 유일한 진입점(2026-07-31~ 투표/글좋아요 흡수, VoteLikeBatchService 삭제).
     * dryRun=true: 실제 반영 없이 부족분(deficit)만 동기 계산해서 즉시 반환(공식 검증용).
     * dryRun=false: 비동기 실행, 즉시 202 반환(실제 좋아요/투표 호출은 수 분 걸릴 수 있음).
     */
    @PostMapping("/reconcile-engagement")
    public ResponseEntity<Map<String, Object>> reconcileEngagement(
            @RequestParam(defaultValue = "3") int days,
            @RequestParam(defaultValue = "40") int maxPosts,
            @RequestParam(defaultValue = "300") int maxLikeCalls,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        if (dryRun) {
            var result = engagementDispatcher.reconcile(days, maxPosts, maxLikeCalls, true);
            return ResponseEntity.ok(Map.of(
                    "dryRun", true,
                    "postsScanned", result.postsScanned(),
                    "deficits", result.deficits()));
        }
        log.info("[AdminTrigger] reconcile-engagement dispatch requested days={} maxPosts={} maxLikeCalls={}",
                days, maxPosts, maxLikeCalls);
        runEngagementReconcileAsync(days, maxPosts, maxLikeCalls);
        return ResponseEntity.accepted().body(Map.of(
                "dryRun", false,
                "message", "백그라운드로 조회수/댓글·대댓글 좋아요 리콘실을 시작했습니다."));
    }

    @Async
    void runEngagementReconcileAsync(int days, int maxPosts, int maxLikeCalls) {
        var result = engagementDispatcher.reconcile(days, maxPosts, maxLikeCalls, false);
        log.info("[AdminTrigger] reconcile-engagement done: posts={} views={} commentLikes={} replyLikes={} votes={} postLikes={}",
                result.postsScanned(), result.viewsUpdated(), result.commentLikesApplied(), result.replyLikesApplied(),
                result.votesApplied(), result.postLikesApplied());
    }

    /**
     * Human-reply backlog TTL cleanup (Wave1-I / §2.9).
     * Default flag is OFF — pass force=true for a one-shot admin run against the bound DB
     * (does not auto-wipe on startup). Cancels inbox rows older than inbox_ttl_days with
     * EXPIRED_TTL and expires stuck REQUESTED plans the same way.
     */
    @PostMapping("/human-reply-ttl-cleanup")
    public ResponseEntity<Map<String, Object>> humanReplyTtlCleanup(
            @RequestParam(defaultValue = "false") boolean force) {
        var result = humanReplyTtlCleanupService.run(Instant.now(), force);
        return ResponseEntity.ok(Map.of(
                "ran", result.ran(),
                "force", force,
                "reclaimedProcessing", result.reclaimedProcessing(),
                "inboxCancelled", result.inboxCancelled(),
                "plansExpired", result.plansExpired(),
                "flagEnabled", properties.getHumanReply().isTtlCleanupEnabled()));
    }

    /**
     * LLM 생성 게이트 HOLD (LLM 장애 시 GENERATION만 차단 — PUBLISHING은 계속).
     * @param reason 홀딩 사유 (optional)
     */
    @PostMapping("/llm-generation-hold")
    public ResponseEntity<Map<String, Object>> llmGenerationHold(
            @RequestParam(required = false) String reason) {
        String finalReason = reason != null && !reason.isBlank() ? reason : "Manual admin hold";
        llmGenerationGateService.hold(finalReason);
        LlmGenerationGate gate = llmGenerationGateService.getCurrentState();
        log.info("[AdminTrigger] llm-generation-hold: reason={}", finalReason);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("status", "ok");
        body.put("action", "llm-generation-hold");
        body.put("state", gate.getState());
        body.put("lastHeldAt", gate.getLastHeldAt());
        body.put("reason", gate.getReason());
        return ResponseEntity.ok(body);
    }

    /**
     * LLM 생성 게이트 RESUME (정상화).
     */
    @PostMapping("/llm-generation-resume")
    public ResponseEntity<Map<String, Object>> llmGenerationResume() {
        llmGenerationGateService.resume();
        LlmGenerationGate gate = llmGenerationGateService.getCurrentState();
        log.info("[AdminTrigger] llm-generation-resume");
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("status", "ok");
        body.put("action", "llm-generation-resume");
        body.put("state", gate.getState());
        body.put("updatedAt", gate.getUpdatedAt());
        body.put("message", "LLM generation gate resumed");
        return ResponseEntity.ok(body);
    }

    /**
     * LLM 생성 게이트 상태 조회 (모니터링용).
     */
    @GetMapping("/llm-generation-status")
    public ResponseEntity<Map<String, Object>> llmGenerationStatus() {
        LlmGenerationGate gate = llmGenerationGateService.getCurrentState();
        boolean isHeld = llmGenerationGateService.isHeld();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("state", gate.getState());
        body.put("isHeld", isHeld);
        body.put("lastHeldAt", gate.getLastHeldAt());
        body.put("reason", gate.getReason());
        body.put("updatedAt", gate.getUpdatedAt());
        return ResponseEntity.ok(body);
    }

    /**
     * 예약글 단건 즉시 게시 — <strong>dev canary 전용</strong>.
     * force=true는 슬롯 시각(scheduledPublishAt 미도래)과 QuietHours 밴을 모두 무시하고 즉시 게시한다.
     * prod에서 force=true로 호출하면 새벽(KST 02:00–06:00) 게시가 발생할 수 있으니 쓰지 마라.
     */
    @PostMapping("/publish-scheduled-post")
    public ResponseEntity<Map<String, Object>> publishScheduledPost(
            @RequestParam String id,
            @RequestParam(defaultValue = "false") boolean force) {
        return scheduledPostPublisher.publishNow(id, force)
                .<ResponseEntity<Map<String, Object>>>map(postId -> ResponseEntity.ok(
                        Map.of("scheduledId", id, "postId", postId)))
                .orElseGet(() -> ResponseEntity.status(409)
                        .body(Map.of("error", "NOT_DUE_OR_NOT_FOUND", "scheduledId", id)));
    }

    /** 모든 생성/발행 게이트의 해석 결과 (backend /api/admin/ai-user/effective-gates 가 프록시). */
    @GetMapping("/effective-gates")
    public ResponseEntity<Map<String, Object>> effectiveGates() {
        return ResponseEntity.ok(effectiveGatesService.resolve());
    }

    /**
     * WP1 — 페르소나 신원 축 재생성 (00-shared.md 계약 1~4, 01-wp1-persona-data.md §3).
     * dryRun=true면 QuotaPlanner 분포만 반환하고 LLM 호출이 전혀 없다(게이트 a 검증용).
     * only는 콤마구분 personaId 목록 — 지정 시 QuotaPlanner는 전체 활성 인원 기준으로 계산하되
     * 실제 LLM 호출·DB 갱신은 그 id들에만 수행한다. force=true면 style_axes가 이미 있어도 재생성한다.
     * only 없이(=force=false) 재호출하면 style_axes가 이미 채워진 페르소나는 건너뛰므로 이 트리거
     * 자체가 중단·재개 단위다. maxConsecutiveFailures(기본 5)회 연속 실패 시 또는 LLM 계정
     * 한도·인증·거절 시그니처(LlmErrorSignatures) 감지 시 남은 대상을 시도하지 않고 조기 종료하며,
     * 응답의 haltedReason에 원인을, remaining에 아직 style_axes가 없는 페르소나 수를 담는다.
     */
    @PostMapping("/regenerate-persona-profiles")
    public ResponseEntity<Map<String, Object>> regeneratePersonaProfiles(
            @RequestParam long seed,
            @RequestParam(defaultValue = "10") int batch,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(required = false) String only,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "5") int maxConsecutiveFailures) {
        List<String> onlyIds = (only == null || only.isBlank())
                ? null
                : java.util.Arrays.stream(only.split(",")).map(String::trim)
                    .filter(s -> !s.isBlank()).toList();
        try {
            Map<String, Object> result = dryRun
                    ? personaProfileRegenerator.dryRun(seed)
                    : personaProfileRegenerator.regenerate(seed, batch, onlyIds, force, maxConsecutiveFailures);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[AdminTrigger] regenerate-persona-profiles failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * WP1 — 150명 전원 관계 ≥1 보장 (01-wp1-persona-data.md §6). 기존 관계는 유지·존중한다.
     */
    @PostMapping("/fill-persona-relationships")
    public ResponseEntity<Map<String, Object>> fillPersonaRelationships(@RequestParam long seed) {
        try {
            var result = personaRelationshipFiller.fillAndPersist(seed);
            return ResponseEntity.ok(Map.of(
                    "totalActive", result.totalActive(),
                    "coveredBefore", result.coveredBefore(),
                    "coveredAfter", result.coveredAfter(),
                    "created", result.created(),
                    "stillUncovered", result.stillUncovered()));
        } catch (Exception e) {
            log.error("[AdminTrigger] fill-persona-relationships failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
