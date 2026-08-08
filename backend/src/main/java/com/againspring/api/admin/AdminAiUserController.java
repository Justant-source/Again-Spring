package com.againspring.api.admin;

import com.againspring.domain.ai.AiUserGenerationConfig;
import com.againspring.repository.ai.AiUserGenerationConfigRepository;
import com.againspring.service.admin.AiUserMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 유저 생성 정책 관리 API (ADMIN 전용, §11 토큰 관제 콘솔).
 * /admin/ai-user 관리 화면의 백엔드.
 *
 * 싱글톤 테이블 ai_user_generation_config(id=1) 을 읽고 쓴다.
 * orchestrator DailyPlanner가 이 설정을 구독해 일일 쿼터를 결정한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai-user")
@RequiredArgsConstructor
@Tag(name = "Admin — AI User", description = "AI 유저 생성 정책·토큰 관제 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiUserController {

    private final AiUserGenerationConfigRepository configRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AiUserMonitorService aiUserMonitorService;

    @Value("${ai.user.orchestrator-url:http://againspring-ai-user-orchestrator:8096}")
    private String orchestratorUrl;

    // ── §11.5 토큰 추정 상수 ─────────────────────────────────────────────
    // 실측 기준 (ClaudeApiInvoker 로그 avg): input ~4600, output ~100
    private static final int POST_IN    = 4_800;  // post는 self-critique 포함 약간 높음
    private static final int POST_OUT   = 300;
    private static final int COMMENT_IN = 4_600;
    private static final int COMMENT_OUT = 100;
    private static final int REPLY_IN   = 4_300;  // reply는 컨텍스트 짧아 소폭 낮음
    private static final int REPLY_OUT  = 80;

    private static final long   MAX5X_DAILY  = 2_100_000L; // Max 5x = Pro(420K) × 5
    private static final long   MAX5X_WINDOW = 440_000L;   // Max 5x = Pro(88K) × 5
    private static final double PEAK_SHARE   = 0.208;      // 균등분포 기준 5h/24h
    private static final double HAIKU_IN_PER_M  = 1.0;     // $/Mtok
    private static final double HAIKU_OUT_PER_M = 5.0;     // $/Mtok
    private static final double CACHE_FACTOR    = 0.235;   // 캐싱 시 입력 ≈ 23.5%

    // ── 비율 추천 (§11.3) ─────────────────────────────────────────────────
    private static final double RATIO_COMMENT = 7.6;
    private static final double RATIO_REPLY   = 4.4;
    private static final double RATIO_VOTE    = 6.5;
    private static final double RATIO_LIKE    = 15.7;

    // =====================================================================
    // GET /api/admin/ai-user/generation-config
    // =====================================================================

    @GetMapping("/generation-config")
    @Operation(summary = "생성 정책 조회", description = "현재 AI 유저 생성 정책과 실시간 토큰 추정값을 반환한다.")
    public ResponseEntity<ConfigResponse> getConfig() {
        AiUserGenerationConfig cfg = loadOrInit();
        return ResponseEntity.ok(toResponse(cfg));
    }

    // =====================================================================
    // PUT /api/admin/ai-user/generation-config
    // =====================================================================

    @PutMapping("/generation-config")
    @Operation(summary = "생성 정책 저장", description = "일일 목표량·백엔드 라우팅을 저장한다. 값 범위 클램프 + 백엔드 배타 검증 포함.")
    public ResponseEntity<ConfigResponse> updateConfig(
            @RequestBody UpdateConfigRequest req,
            Authentication auth) {

        AiUserGenerationConfig cfg = loadOrInit();

        // ── 범위 클램프 ────────────────────────────────────────────────
        cfg.setTargetPosts(clamp(req.targetPosts, 0, 100));
        cfg.setTargetComments(clamp(req.targetComments, 0, 1200));
        cfg.setTargetReplies(clamp(req.targetReplies, 0, 900));
        cfg.setTargetVotes(clamp(req.targetVotes, 0, 10000));
        cfg.setTargetLikes(clamp(req.targetLikes, 0, 100000));
        cfg.setAutoComment(req.autoComment);
        cfg.setAutoReply(req.autoReply);

        // ── 계획형 실행기 설정 (PLAN 모드 일원화) ────────────────────────
        cfg.setProviderAiPostBundle(validatePlanProvider(req.providerAiPostBundle));
        cfg.setProviderHumanPostPlan(validatePlanProvider(req.providerHumanPostPlan));
        cfg.setProviderHumanInteraction(validatePlanProvider(req.providerHumanInteraction));
        cfg.setProviderVoteLike(validatePlanProvider(req.providerVoteLike));
        cfg.setScheduleExecutionPaused(req.scheduleExecutionPaused);
        cfg.setAiUserKillSwitch(req.aiUserKillSwitch);
        cfg.setCandidatePoolSize(clamp(req.candidatePoolSize, 8, 30));
        cfg.setHumanBatchMaxPosts(clamp(req.humanBatchMaxPosts, 1, 10));
        cfg.setHumanBatchMaxInteractions(clamp(req.humanBatchMaxInteractions, 1, 50));

        // ── 댓글 생성량 설정 (SSOT) ────────────────────────────────────
        cfg.setHrRespondersPerInteractionMax(clamp(req.hrRespondersPerInteractionMax, 0, 5));
        cfg.setHrDistinctPersonasMax(clamp(req.hrDistinctPersonasMax, 1, 10));
        cfg.setHrRepliesPerPersonaMax(clamp(req.hrRepliesPerPersonaMax, 1, 10));
        cfg.setHrCandidateRespondersMax(clamp(req.hrCandidateRespondersMax, 1, 8));
        cfg.setHrChunkSize(clamp(req.hrChunkSize, 1, 50));
        int delayMin = clamp(req.hrDelayMinutesMin, 1, 720);
        int delayMax = clamp(req.hrDelayMinutesMax, 1, 720);
        // min>max 역전 방지: 뒤집힌 입력은 정렬해서 저장한다.
        cfg.setHrDelayMinutesMin(Math.min(delayMin, delayMax));
        cfg.setHrDelayMinutesMax(Math.max(delayMin, delayMax));

        // ── 생성 런타임 (타임아웃·새벽 배치) ───────────────────────────
        cfg.setBundleTimeoutMs(clamp(req.bundleTimeoutMs > 0 ? req.bundleTimeoutMs : 600_000, 60_000, 900_000));
        double share = req.nightlyPairedShare;
        if (Double.isNaN(share) || share < 0) share = 0.20;
        cfg.setNightlyPairedShare(Math.min(1.0, share));
        int fromH = clamp(req.nightlySlotFromHour, 0, 23);
        int toH = clamp(req.nightlySlotToHour, 1, 24);
        if (toH <= fromH) toH = Math.min(24, fromH + 1);
        cfg.setNightlySlotFromHour(fromH);
        cfg.setNightlySlotToHour(toH);
        cfg.setNightlySlotMinSpacingMinutes(clamp(req.nightlySlotMinSpacingMinutes > 0
                ? req.nightlySlotMinSpacingMinutes : 45, 15, 180));

        // ── 메타 ──────────────────────────────────────────────────────
        String actor = (auth != null) ? auth.getName() : "unknown";
        cfg.setUpdatedBy(actor);
        cfg.setUpdatedAt(Instant.now());

        configRepository.save(cfg);
        log.info("AI 생성 정책 저장 by {}: posts={} comments={} replies={} votes={} likes={} timeoutMs={} pairedShare={} post_provider={} comment_provider={} interaction_provider={} vote_like_provider={}",
                actor, cfg.getTargetPosts(), cfg.getTargetComments(), cfg.getTargetReplies(), cfg.getTargetVotes(), cfg.getTargetLikes(),
                cfg.getBundleTimeoutMs(), cfg.getNightlyPairedShare(),
                cfg.getProviderAiPostBundle(), cfg.getProviderHumanPostPlan(), cfg.getProviderHumanInteraction(), cfg.getProviderVoteLike());

        return ResponseEntity.ok(toResponse(cfg));
    }

    // =====================================================================
    // =====================================================================
    // POST /api/admin/ai-user/cleanup/reduce-ㅠ — AI 댓글 ㅠ 과다 정규화
    // =====================================================================

    @PostMapping("/cleanup/reduce-ㅠ")
    @Operation(summary = "AI 댓글 ㅠ 연속 정규화",
               description = "AI 유저(synthetic=1)가 쓴 댓글의 ㅠ{2,} 를 단일 ㅠ 로 줄인다.")
    public ResponseEntity<Map<String, Object>> reduceEmojiㅠ() {
        int updated = jdbcTemplate.update(
            "UPDATE post_comments pc " +
            "JOIN users u ON pc.author_id = u.id " +
            "SET pc.body = REGEXP_REPLACE(pc.body, 'ㅠ{2,}', 'ㅠ') " +
            "WHERE u.synthetic = 1 " +
            "  AND pc.deleted_at IS NULL " +
            "  AND pc.body REGEXP 'ㅠ{2,}'"
        );
        log.info("[cleanup] reduced ㅠ in {} AI comments", updated);
        return ResponseEntity.ok(Map.of("updated", updated, "message", updated + "건 정규화 완료"));
    }

    // =====================================================================
    // POST /api/admin/ai-user/backfill-comment-likes — 오케스트레이터 프록시
    // =====================================================================

    @PostMapping("/backfill-comment-likes")
    @Operation(summary = "기존 댓글 좋아요 소급 적용",
               description = "오케스트레이터에 백필 요청을 전달한다. 비동기 실행 (202 Accepted).")
    public ResponseEntity<Map<String, Object>> backfillCommentLikes(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "8") int personasPerPost) {
        try {
            String url = orchestratorUrl + "/admin/trigger/backfill-comment-likes"
                + "?days=" + days + "&personasPerPost=" + personasPerPost;
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = RestClient.create().post().uri(url)
                    .retrieve().body(Map.class);
            log.info("[backfill-comment-likes] orchestrator response: {}", resp);
            return ResponseEntity.accepted().body(resp != null ? resp : Map.of("status", "queued"));
        } catch (Exception e) {
            log.error("[backfill-comment-likes] orchestrator call failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/admin/ai-user/kill — 비상 정지 (모든 backend=OFF)
    // =====================================================================

    @PostMapping("/kill")
    @Operation(summary = "비상 정지", description = "모든 생성 타입의 provider를 OFF로 설정한다. ai_user_runtime.enabled 와 별개의 소프트 스톱.")
    public ResponseEntity<KillResponse> killAll(Authentication auth) {
        AiUserGenerationConfig cfg = loadOrInit();
        cfg.setProviderAiPostBundle("OFF");
        cfg.setProviderHumanPostPlan("OFF");
        cfg.setProviderHumanInteraction("OFF");
        cfg.setProviderVoteLike("OFF");
        cfg.setAiUserKillSwitch(true);
        String actor = (auth != null) ? auth.getName() : "unknown";
        cfg.setUpdatedBy(actor);
        cfg.setUpdatedAt(Instant.now());
        configRepository.save(cfg);
        log.warn("AI 생성 비상 정지 by {}: 모든 provider → OFF", actor);
        return ResponseEntity.ok(new KillResponse("ok", "모든 생성 provider 오프로 설정됨", Instant.now().toString()));
    }

    // =====================================================================
    // GET /api/admin/ai-user/generation-status
    // =====================================================================

    @GetMapping("/generation-status")
    @Operation(summary = "오늘 AI 유저 행동 진행 현황", description = "KST 기준 오늘의 타입별 완료·목표·진행률 및 실패/차단 횟수를 반환한다.")
    public ResponseEntity<GenerationStatusResponse> getGenerationStatus() {
        LocalDate todayKst = LocalDate.now(ZoneId.of("Asia/Seoul"));
        java.sql.Timestamp dayStart = java.sql.Timestamp.from(todayKst.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());

        // Query 1: 오늘 synthetic=1 유저가 생성한 게시글
        Integer postsDone = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM posts p " +
            "JOIN users u ON p.author_id = u.id " +
            "WHERE u.synthetic = 1 AND p.deleted_at IS NULL AND p.created_at >= ?",
            Integer.class,
            dayStart
        );
        postsDone = postsDone != null ? postsDone : 0;

        // Query 2: action_log GROUP BY action_type, status
        List<Map<String, Object>> actionStats = jdbcTemplate.queryForList(
            "SELECT action_type, status, COUNT(*) as cnt " +
            "FROM persona_action_log " +
            "WHERE created_at >= ? " +
            "  AND action_type IN ('LIKE','VOTE','COMMENT','REPLY','COMMENT_LIKE') " +
            "GROUP BY action_type, status",
            dayStart
        );

        // Aggregation
        int commentsDone = 0, repliesDone = 0, votesDone = 0, likesDone = 0;
        int totalFailed = 0, totalBlocked = 0;

        for (Map<String, Object> row : actionStats) {
            String actionType = (String) row.get("action_type");
            String status = (String) row.get("status");
            int cnt = ((Number) row.get("cnt")).intValue();

            if ("POSTED".equals(status)) {
                if ("COMMENT".equals(actionType)) {
                    commentsDone += cnt;
                } else if ("REPLY".equals(actionType)) {
                    repliesDone += cnt;
                } else if ("VOTE".equals(actionType)) {
                    votesDone += cnt;
                } else if ("LIKE".equals(actionType) || "COMMENT_LIKE".equals(actionType)) {
                    likesDone += cnt;
                }
            } else if ("FAILED".equals(status)) {
                totalFailed += cnt;
            } else if ("BLOCKED".equals(status)) {
                totalBlocked += cnt;
            }
        }

        // Load targets from ai_user_generation_config
        AiUserGenerationConfig cfg = loadOrInit();
        int targetPosts = cfg.getTargetPosts();
        int targetComments = cfg.getTargetComments();
        int targetReplies = cfg.getTargetReplies();
        int targetVotes = cfg.getTargetVotes();
        int targetLikes = cfg.getTargetLikes();

        // Build response
        GenerationStatusResponse resp = new GenerationStatusResponse(
                todayKst.toString(),
                new GenerationStatusResponse.Targets(
                        new GenerationStatusResponse.Metric(postsDone, targetPosts, computePercent(postsDone, targetPosts)),
                        new GenerationStatusResponse.Metric(commentsDone, targetComments, computePercent(commentsDone, targetComments)),
                        new GenerationStatusResponse.Metric(repliesDone, targetReplies, computePercent(repliesDone, targetReplies)),
                        new GenerationStatusResponse.Metric(votesDone, targetVotes, computePercent(votesDone, targetVotes)),
                        new GenerationStatusResponse.Metric(likesDone, targetLikes, computePercent(likesDone, targetLikes))
                ),
                new GenerationStatusResponse.Failures(totalFailed, totalBlocked)
        );

        return ResponseEntity.ok(resp);
    }

    private static int computePercent(int done, int target) {
        if (target == 0) return 0;
        return Math.min(100, (int) Math.round(100.0 * done / target));
    }

    // =====================================================================
    // 내부 헬퍼
    // =====================================================================

    private AiUserGenerationConfig loadOrInit() {
        return configRepository.findById(1)
                .orElseGet(() -> configRepository.save(AiUserGenerationConfig.builder().build()));
    }

    private ConfigResponse toResponse(AiUserGenerationConfig cfg) {
        EstimateResult est = computeEstimate(cfg);
        return new ConfigResponse(
                cfg.getTargetPosts(), cfg.getTargetComments(), cfg.getTargetReplies(),
                cfg.getTargetVotes(), cfg.getTargetLikes(),
                cfg.isAutoComment(), cfg.isAutoReply(),
                cfg.getProviderAiPostBundle(),
                cfg.getProviderHumanPostPlan(), cfg.getProviderHumanInteraction(), cfg.getProviderVoteLike(),
                cfg.isScheduleExecutionPaused(), cfg.isAiUserKillSwitch(),
                cfg.getCandidatePoolSize(), cfg.getHumanBatchMaxPosts(), cfg.getHumanBatchMaxInteractions(),
                cfg.getHrRespondersPerInteractionMax(), cfg.getHrDistinctPersonasMax(),
                cfg.getHrRepliesPerPersonaMax(), cfg.getHrRepliesPerPostHumanMax(),
                cfg.getHrCandidateRespondersMax(), cfg.getHrChunkSize(),
                cfg.getHrDelayMinutesMin(), cfg.getHrDelayMinutesMax(),
                cfg.getBundleTimeoutMs(), cfg.getNightlyPairedShare(),
                cfg.getNightlySlotFromHour(), cfg.getNightlySlotToHour(),
                cfg.getNightlySlotMinSpacingMinutes(),
                cfg.getUpdatedBy(), cfg.getUpdatedAt() != null ? cfg.getUpdatedAt().toString() : null,
                RATIO_COMMENT, RATIO_REPLY, RATIO_VOTE, RATIO_LIKE,
                est
        );
    }

    /** §11.5 토큰·비용 추정 (PLAN 모드 일원화, provider 기준)
     *
     *  CLAUDE provider: CLI 경로, Max5x 정액제 토큰 계산
     *  CODEX provider: 호출수만 집계, $비용 추정 미정의
     *  OFF provider: 해당 액션 타입은 추정에서 제외
     *
     *  참고: 이것은 "추정치"이며, 정밀도보다 management 가시성을 우선한다.
     */
    private EstimateResult computeEstimate(AiUserGenerationConfig cfg) {
        long calls = 0;
        long cliIn = 0, cliOut = 0;
        long codexCalls = 0;

        // AI 글 생성 — 새벽 배치가 target_posts만큼 생성 (주간 provider OFF여도 새벽 창에서 실행)
        if (cfg.getTargetPosts() > 0) {
            calls += cfg.getTargetPosts();
            String postProvider = cfg.getProviderAiPostBundle();
            if ("OFF".equals(postProvider) || postProvider == null || postProvider.isBlank()) {
                // 낮 OFF + 새벽 배치 경로: CLAUDE 비용으로 추정
                cliIn += (long) cfg.getTargetPosts() * POST_IN;
                cliOut += (long) cfg.getTargetPosts() * POST_OUT;
            } else if ("CLAUDE".equals(postProvider)) {
                cliIn += (long) cfg.getTargetPosts() * POST_IN;
                cliOut += (long) cfg.getTargetPosts() * POST_OUT;
            } else if ("CODEX".equals(postProvider)) {
                codexCalls += cfg.getTargetPosts();
            }
        }

        // 사람 상호작용 — 댓글·답글 (providerHumanInteraction)
        if (!"OFF".equals(cfg.getProviderHumanInteraction())) {
            if (cfg.getTargetComments() > 0) {
                calls += cfg.getTargetComments();
                if ("CLAUDE".equals(cfg.getProviderHumanInteraction())) {
                    cliIn += (long) cfg.getTargetComments() * COMMENT_IN;
                    cliOut += (long) cfg.getTargetComments() * COMMENT_OUT;
                } else if ("CODEX".equals(cfg.getProviderHumanInteraction())) {
                    codexCalls += cfg.getTargetComments();
                }
            }
            if (cfg.getTargetReplies() > 0) {
                calls += cfg.getTargetReplies();
                if ("CLAUDE".equals(cfg.getProviderHumanInteraction())) {
                    cliIn += (long) cfg.getTargetReplies() * REPLY_IN;
                    cliOut += (long) cfg.getTargetReplies() * REPLY_OUT;
                } else if ("CODEX".equals(cfg.getProviderHumanInteraction())) {
                    codexCalls += cfg.getTargetReplies();
                }
            }
        }

        // AI 투표·좋아요 생성 (providerVoteLike) — 댓글 수준 토큰으로 간단히 추정
        if (!"OFF".equals(cfg.getProviderVoteLike())) {
            if (cfg.getTargetVotes() > 0) {
                calls += cfg.getTargetVotes();
                if ("CLAUDE".equals(cfg.getProviderVoteLike())) {
                    cliIn += (long) cfg.getTargetVotes() * COMMENT_IN;
                    cliOut += (long) cfg.getTargetVotes() * COMMENT_OUT;
                } else if ("CODEX".equals(cfg.getProviderVoteLike())) {
                    codexCalls += cfg.getTargetVotes();
                }
            }
            if (cfg.getTargetLikes() > 0) {
                calls += cfg.getTargetLikes();
                if ("CLAUDE".equals(cfg.getProviderVoteLike())) {
                    cliIn += (long) cfg.getTargetLikes() * COMMENT_IN;
                    cliOut += (long) cfg.getTargetLikes() * COMMENT_OUT;
                } else if ("CODEX".equals(cfg.getProviderVoteLike())) {
                    codexCalls += cfg.getTargetLikes();
                }
            }
        }

        long cliTotal    = cliIn + cliOut;
        double cliPct    = (double) cliTotal / MAX5X_DAILY * 100;
        double cliPeakPct = (double) cliTotal * PEAK_SHARE / MAX5X_WINDOW * 100;

        List<String> warnings = new ArrayList<>();
        if (calls == 0) {
            warnings.add("INFO:생성 없음 — 모든 provider가 OFF 또는 목표량 0");
        } else {
            if (cliPct > 100)    warnings.add("DANGER:CLAUDE 경로가 Max 5x 일일 한도 초과 (종일 throttle 위험)");
            else if (cliPct > 80) warnings.add("WARN:CLAUDE 경로가 Max 5x 한도 80% 초과 (개발 quota 공유 주의)");
            if (cliPeakPct > 100) warnings.add("WARN:저녁 피크 5h 윈도우 초과 (피크 시간대 throttle 위험)");
            if (codexCalls > 0)   warnings.add("INFO:CODEX " + codexCalls + "건 호출 예상 (비용 추정 미정의)");
        }

        return new EstimateResult(
                calls, cliTotal, cliPct, cliPeakPct,
                0.0, 0.0,  // CLAUDE는 Max5x 정액제, CODEX는 비용 미정의
                cliIn + cliOut, warnings
        );
    }

    private static String validatePlanProvider(String raw) {
        if ("CLAUDE".equalsIgnoreCase(raw)) return "CLAUDE";
        if ("CODEX".equalsIgnoreCase(raw)) return "CODEX";
        return "OFF";
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // =====================================================================
    // DTOs
    // =====================================================================

    @Getter @Setter
    public static class UpdateConfigRequest {
        private int targetPosts;
        private int targetComments;
        private int targetReplies;
        private int targetVotes;
        private int targetLikes;
        private boolean autoComment;
        private boolean autoReply;
        private String providerAiPostBundle;
        private String providerHumanPostPlan;
        private String providerHumanInteraction;
        private String providerVoteLike;
        private boolean scheduleExecutionPaused;
        private boolean aiUserKillSwitch;
        private int candidatePoolSize = 24;
        private int humanBatchMaxPosts = 10;
        private int humanBatchMaxInteractions = 50;
        // 댓글 생성량 설정 (SSOT)
        private int hrRespondersPerInteractionMax = 3;
        private int hrDistinctPersonasMax = 3;
        private int hrRepliesPerPersonaMax = 5;
        private int hrCandidateRespondersMax = 8;
        private int hrChunkSize = 20;
        private int hrDelayMinutesMin = 1;
        private int hrDelayMinutesMax = 30;
        private int bundleTimeoutMs = 600_000;
        private double nightlyPairedShare = 0.20;
        private int nightlySlotFromHour = 8;
        private int nightlySlotToHour = 22;
        private int nightlySlotMinSpacingMinutes = 45;
    }

    @Getter @AllArgsConstructor
    public static class ConfigResponse {
        private final int    targetPosts;
        private final int    targetComments;
        private final int    targetReplies;
        private final int    targetVotes;
        private final int    targetLikes;
        private final boolean autoComment;
        private final boolean autoReply;
        private final String providerAiPostBundle;
        private final String providerHumanPostPlan;
        private final String providerHumanInteraction;
        private final String providerVoteLike;
        private final boolean scheduleExecutionPaused;
        private final boolean aiUserKillSwitch;
        private final int candidatePoolSize;
        private final int humanBatchMaxPosts;
        private final int humanBatchMaxInteractions;
        private final int hrRespondersPerInteractionMax;
        private final int hrDistinctPersonasMax;
        private final int hrRepliesPerPersonaMax;
        /** 파생값 = distinct × perPersona. 저장하지 않는다. */
        private final int hrRepliesPerPostHumanMax;
        private final int hrCandidateRespondersMax;
        private final int hrChunkSize;
        private final int hrDelayMinutesMin;
        private final int hrDelayMinutesMax;
        private final int bundleTimeoutMs;
        private final double nightlyPairedShare;
        private final int nightlySlotFromHour;
        private final int nightlySlotToHour;
        private final int nightlySlotMinSpacingMinutes;
        private final String  updatedBy;
        private final String  updatedAt;
        private final double  ratioComment;
        private final double  ratioReply;
        private final double  ratioVote;
        private final double  ratioLike;
        private final EstimateResult estimate;
    }

    @Getter @AllArgsConstructor
    public static class EstimateResult {
        private final long   callsPerDay;
        private final long   cliTokensTotal;
        private final double cliPct;       // Max5x 일일 대비 %
        private final double cliPeakPct;   // 피크 5h 윈도우 대비 %
        private final double apiCostDay;   // API $/일
        private final double apiCostMonth; // API $/월
        private final long   apiTokensTotal;
        private final List<String> warnings; // "DANGER:...", "WARN:...", "INFO:..."
    }

    @Getter @AllArgsConstructor
    public static class KillResponse {
        private final String status;
        private final String message;
        private final String killedAt;
    }

    @Getter @AllArgsConstructor
    public static class GenerationStatusResponse {
        private final String todayKst;
        private final Targets targets;
        private final Failures failures;

        @Getter @AllArgsConstructor
        public static class Targets {
            private final Metric posts;
            private final Metric comments;
            private final Metric replies;
            private final Metric votes;
            private final Metric likes;
        }

        @Getter @AllArgsConstructor
        public static class Metric {
            private final int done;
            private final int target;
            private final int percent;
        }

        @Getter @AllArgsConstructor
        public static class Failures {
            private final int failed;
            private final int blocked;
        }
    }

    // =====================================================================
    // GET /api/admin/ai-user/action-feed
    // =====================================================================

    @GetMapping("/action-feed")
    @Operation(summary = "AI 유저 행동 피드", description = "persona_action_log 최근 기록 조회 (선택적 필터링)")
    public ResponseEntity<AiUserMonitorService.ActionFeedDto> getActionFeed(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String actionType) {
        AiUserMonitorService.ActionFeedDto feed = aiUserMonitorService.getActionFeed(limit, status, actionType);
        return ResponseEntity.ok(feed);
    }

    // =====================================================================
    // GET /api/admin/ai-user/persona-performance
    // =====================================================================

    @GetMapping("/persona-performance")
    @Operation(summary = "페르소나 성능 지표", description = "최근 24시간 또는 7일간 페르소나별 행동 완료율·실패율 집계")
    public ResponseEntity<List<AiUserMonitorService.PersonaPerformanceDto>> getPersonaPerformance(
            @RequestParam(defaultValue = "24h") String range) {
        List<AiUserMonitorService.PersonaPerformanceDto> performance = aiUserMonitorService.getPersonaPerformance(range);
        return ResponseEntity.ok(performance);
    }

    // =====================================================================
    // GET /api/admin/ai-user/hourly-distribution
    // =====================================================================

    @GetMapping("/hourly-distribution")
    @Operation(summary = "시간대별 행동 분포", description = "최근 N시간(기본 24)의 시간대별 게시 행동 분포 (0-23시)")
    public ResponseEntity<AiUserMonitorService.HourlyDistributionDto> getHourlyDistribution(
            @RequestParam(defaultValue = "24") int hours) {
        AiUserMonitorService.HourlyDistributionDto distribution = aiUserMonitorService.getHourlyDistribution(hours);
        return ResponseEntity.ok(distribution);
    }
}
