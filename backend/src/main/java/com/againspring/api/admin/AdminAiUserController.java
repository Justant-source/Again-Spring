package com.againspring.api.admin;

import com.againspring.domain.ai.AiUserGenerationConfig;
import com.againspring.repository.ai.AiUserGenerationConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    // ── §11.5 토큰 추정 상수 ─────────────────────────────────────────────
    private static final int POST_IN    = 5_900;
    private static final int POST_OUT   = 300;
    private static final int COMMENT_IN = 5_200;
    private static final int COMMENT_OUT = 70;
    private static final int REPLY_IN   = 3_800;
    private static final int REPLY_OUT  = 25;

    private static final long   MAX5X_DAILY  = 420_000L;   // 88K/5h × (24/5)
    private static final long   MAX5X_WINDOW = 88_000L;
    private static final double PEAK_SHARE   = 0.35;
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

        // ── 백엔드 유효성 + CLI/API 배타 규칙 (서버 강제) ───────────────
        cfg.setBackendPost(validateBackend(req.backendPost));
        cfg.setBackendComment(validateBackend(req.backendComment));
        cfg.setBackendReply(validateBackend(req.backendReply));

        cfg.setPromptCaching(req.promptCaching);
        cfg.setDailyTokenBudget(req.dailyTokenBudget);

        // ── 메타 ──────────────────────────────────────────────────────
        String actor = (auth != null) ? auth.getName() : "unknown";
        cfg.setUpdatedBy(actor);
        cfg.setUpdatedAt(Instant.now());

        configRepository.save(cfg);
        log.info("AI 생성 정책 저장 by {}: posts={} comments={} replies={} post_backend={} comment_backend={} reply_backend={}",
                actor, cfg.getTargetPosts(), cfg.getTargetComments(), cfg.getTargetReplies(),
                cfg.getBackendPost(), cfg.getBackendComment(), cfg.getBackendReply());

        return ResponseEntity.ok(toResponse(cfg));
    }

    // =====================================================================
    // POST /api/admin/ai-user/kill — 비상 정지 (모든 backend=OFF)
    // =====================================================================

    @PostMapping("/kill")
    @Operation(summary = "비상 정지", description = "모든 콘텐츠 타입의 백엔드를 OFF로 설정한다. ai_user_runtime.enabled 와 별개의 소프트 스톱.")
    public ResponseEntity<KillResponse> killAll(Authentication auth) {
        AiUserGenerationConfig cfg = loadOrInit();
        cfg.setBackendPost("OFF");
        cfg.setBackendComment("OFF");
        cfg.setBackendReply("OFF");
        String actor = (auth != null) ? auth.getName() : "unknown";
        cfg.setUpdatedBy(actor);
        cfg.setUpdatedAt(Instant.now());
        configRepository.save(cfg);
        log.warn("AI 생성 비상 정지 by {}: 모든 backend → OFF", actor);
        return ResponseEntity.ok(new KillResponse("ok", "POST/COMMENT/REPLY 모두 OFF로 설정됨", Instant.now().toString()));
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
                cfg.getBackendPost(), cfg.getBackendComment(), cfg.getBackendReply(),
                cfg.isPromptCaching(), cfg.getDailyTokenBudget(),
                cfg.getUpdatedBy(), cfg.getUpdatedAt() != null ? cfg.getUpdatedAt().toString() : null,
                RATIO_COMMENT, RATIO_REPLY, RATIO_VOTE, RATIO_LIKE,
                est
        );
    }

    /** §11.5 토큰·비용 추정 */
    private EstimateResult computeEstimate(AiUserGenerationConfig cfg) {
        long calls = 0;
        long cliIn = 0, cliOut = 0, apiIn = 0, apiOut = 0;

        if (!"OFF".equals(cfg.getBackendPost()) && cfg.getTargetPosts() > 0) {
            calls += cfg.getTargetPosts();
            if ("CLI".equals(cfg.getBackendPost())) {
                cliIn += (long) cfg.getTargetPosts() * POST_IN;
                cliOut += (long) cfg.getTargetPosts() * POST_OUT;
            } else {
                apiIn += (long) cfg.getTargetPosts() * POST_IN;
                apiOut += (long) cfg.getTargetPosts() * POST_OUT;
            }
        }
        if (!"OFF".equals(cfg.getBackendComment()) && cfg.getTargetComments() > 0) {
            calls += cfg.getTargetComments();
            if ("CLI".equals(cfg.getBackendComment())) {
                cliIn += (long) cfg.getTargetComments() * COMMENT_IN;
                cliOut += (long) cfg.getTargetComments() * COMMENT_OUT;
            } else {
                apiIn += (long) cfg.getTargetComments() * COMMENT_IN;
                apiOut += (long) cfg.getTargetComments() * COMMENT_OUT;
            }
        }
        if (!"OFF".equals(cfg.getBackendReply()) && cfg.getTargetReplies() > 0) {
            calls += cfg.getTargetReplies();
            if ("CLI".equals(cfg.getBackendReply())) {
                cliIn += (long) cfg.getTargetReplies() * REPLY_IN;
                cliOut += (long) cfg.getTargetReplies() * REPLY_OUT;
            } else {
                apiIn += (long) cfg.getTargetReplies() * REPLY_IN;
                apiOut += (long) cfg.getTargetReplies() * REPLY_OUT;
            }
        }

        long cliTotal    = cliIn + cliOut;
        double cliPct    = (double) cliTotal / MAX5X_DAILY * 100;
        double cliPeakPct = (double) cliTotal * PEAK_SHARE / MAX5X_WINDOW * 100;

        double apiInEff  = cfg.isPromptCaching() ? apiIn * CACHE_FACTOR : apiIn;
        double apiCostDay = (apiInEff / 1_000_000.0) * HAIKU_IN_PER_M
                          + (apiOut   / 1_000_000.0) * HAIKU_OUT_PER_M;
        double apiCostMonth = apiCostDay * 30;

        List<String> warnings = new ArrayList<>();
        if (calls == 0) {
            warnings.add("INFO:생성 없음 — 모든 타입이 OFF 또는 목표량 0");
        } else {
            if (cliPct > 100)    warnings.add("DANGER:CLI 경로가 Max 5x 일일 한도 초과 (종일 throttle 위험)");
            else if (cliPct > 80) warnings.add("WARN:CLI 경로가 Max 5x 한도 80% 초과 (개발 quota 공유 주의)");
            if (cliPeakPct > 100) warnings.add("WARN:저녁 피크 5h 윈도우 초과 (피크 시간대 throttle 위험)");
        }

        return new EstimateResult(
                calls, cliTotal, cliPct, cliPeakPct,
                apiCostDay, apiCostMonth,
                apiIn + apiOut, warnings
        );
    }

    private static String validateBackend(String raw) {
        if ("CLI".equalsIgnoreCase(raw)) return "CLI";
        if ("API".equalsIgnoreCase(raw)) return "API";
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
        private String backendPost;
        private String backendComment;
        private String backendReply;
        private boolean promptCaching;
        private Long dailyTokenBudget;
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
        private final String  backendPost;
        private final String  backendComment;
        private final String  backendReply;
        private final boolean promptCaching;
        private final Long    dailyTokenBudget;
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
}
