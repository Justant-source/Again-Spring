package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.CommentThreadDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemStatus;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemType;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanStatus;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanItemRepository;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.service.GenerationConfigSupport;
import com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService;
import com.againspring.aiuser.orchestrator.util.LiteralNewlineNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Generates a whole candidate tree before any item becomes publishable.  This is deliberately
 * separate from the publisher: an HTTP failure or malformed result never turns into community text.
 *
 * <p>Paired posts use a two-phase comment lifecycle (scheduler hooks):
 * <ul>
 *   <li>{@link #ensureAuthorPhase1CommentPlan} — author PUBLIC at T0, author-body only,
 *       {@code scheduledAt} strictly before partnerAt (T0+Δ)</li>
 *   <li>{@link #ensureCommentPlanForPairedPost} / {@link #attachPhase2FromCall2Response} —
 *       partner arrival: unpublished items cancelled via revision bump; phase2 grounded on both
 *       bodies; already-POSTED phase1 comments kept</li>
 *   <li>{@link #loadLatestPublishedTopLevelComments} — Call2 context (5–8 latest top-level)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadPlanGenerationService {
    /** Phase1 top-level volume (grilled: few, 2–4). */
    public static final int PHASE1_MAX_TOP_LEVEL = 4;
    public static final int PHASE1_MAX_REPLIES = 0;
    public static final int PHASE1_READY_MIN_TOP_LEVEL = 1;
    public static final int PHASE1_READY_MIN_ITEMS = 2;
    /** Call2 context window for published top-level comments. */
    public static final int CALL2_COMMENT_CONTEXT_DEFAULT = 8;
    public static final int CALL2_COMMENT_CONTEXT_MAX = 8;

    private final AiThreadPlanRepository planRepository;
    private final AiThreadPlanItemRepository itemRepository;
    private final PersonaRepository personaRepository;
    private final ThreadPlanService planService;
    private final LlmAiUserClient llmClient;
    private final ThreadQualityGate qualityGate;
    private final OrchestratorProperties properties;
    private final AiUserGenerationConfigRepository configRepository;
    private final CandidateScheduleSupport candidateScheduleSupport;
    private final PlanPersonaMapper planPersonaMapper;
    private final InterestedPersonaSeeder interestedPersonaSeeder;
    private final BackendBotClient backendBotClient;
    private final GenerationConfigSupport generationConfigSupport;
    private final LlmGenerationGateService llmGenerationGateService;
    private final JdbcTemplate jdbcTemplate;
    private final com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache promptTemplateCache;

    @Transactional
    public void generateRequestedPlans() {
        if (!planModeEnabled()) return;
        planRepository.lockByStatus(ThreadPlanStatus.REQUESTED, PageRequest.of(0, 5)).stream()
                .map(AiThreadPlan::getId).toList()
                .forEach(this::generateOne);
    }

    /**
     * Phase1 (scheduler hook at author PUBLIC / T0): author-body-only comment plan.
     * All item {@code scheduledAt} values are clamped strictly before {@code partnerAt} (T0+Δ).
     * Volume is small (≤ {@link #PHASE1_MAX_TOP_LEVEL} top-level).
     *
     * @param partnerAt partner arrival instant; must be after {@code publishedAt}
     * @return true when the plan is READY/ACTIVE (or already was)
     */
    @Transactional
    public boolean ensureAuthorPhase1CommentPlan(String postId, int revision, String title,
                                                  String authorBody, String category,
                                                  Instant publishedAt, Instant partnerAt) {
        if (!planModeEnabled() || postId == null || postId.isBlank()) {
            return false;
        }
        Instant t0 = publishedAt != null ? publishedAt : Instant.now();
        if (partnerAt == null || !partnerAt.isAfter(t0)) {
            log.warn("Phase1 skipped for post {}: partnerAt must be after publishedAt", postId);
            return false;
        }
        String sourceBody = combinePairedSourceBody(authorBody, null);
        AiThreadPlan plan = planService.requestPlan(
                postId, Math.max(1, revision), "AI_POST", t0, title, sourceBody, category);
        reGroundPlanSource(plan, title, sourceBody, category);
        if (plan.getStatus() == ThreadPlanStatus.READY || plan.getStatus() == ThreadPlanStatus.ACTIVE) {
            return true;
        }
        if (plan.getStatus() != ThreadPlanStatus.REQUESTED) {
            log.warn("Phase1 plan {} for post {} is {} — skip", plan.getId(), postId, plan.getStatus());
            return false;
        }
        generatePhase(plan.getId(), PHASE1_MAX_TOP_LEVEL, PHASE1_MAX_REPLIES,
                PHASE1_READY_MIN_TOP_LEVEL, PHASE1_READY_MIN_ITEMS, t0, partnerAt, false);
        return planReadyOrActive(plan.getId(), postId, "Phase1");
    }

    /**
     * Phase2 (partner arrival): both-body grounding. {@link ThreadPlanService#requestPlan} with a
     * bumped revision cancels unfinished items from older revisions; already-POSTED phase1 comments
     * stay. Falls back to yml providers when DB gates are OFF.
     *
     * @return true when the plan is READY/ACTIVE (or already was)
     */
    @Transactional
    public boolean ensureCommentPlanForPairedPost(String postId, int revision, String title,
                                                   String authorBody, String partnerBody, String category) {
        return ensureCommentPlanForPairedPost(postId, revision, title, authorBody, partnerBody,
                category, Instant.now());
    }

    /**
     * Phase2 with explicit partner-publish clock for schedule clamping (items on/after partnerAt).
     */
    @Transactional
    public boolean ensureCommentPlanForPairedPost(String postId, int revision, String title,
                                                   String authorBody, String partnerBody, String category,
                                                   Instant partnerPublishedAt) {
        if (!planModeEnabled() || postId == null || postId.isBlank()) {
            return false;
        }
        Instant origin = partnerPublishedAt != null ? partnerPublishedAt : Instant.now();
        String sourceBody = combinePairedSourceBody(authorBody, partnerBody);
        AiThreadPlan plan = planService.requestPlan(
                postId, Math.max(1, revision), "AI_POST", origin, title, sourceBody, category);
        // Outbox may have created this revision first with author-only body / HUMAN_POST.
        reGroundPlanSource(plan, title, sourceBody, category);
        if (plan.getStatus() == ThreadPlanStatus.READY || plan.getStatus() == ThreadPlanStatus.ACTIVE) {
            return true;
        }
        if (plan.getStatus() != ThreadPlanStatus.REQUESTED) {
            log.warn("Paired comment plan {} for post {} is {} — skip force generate",
                    plan.getId(), postId, plan.getStatus());
            return false;
        }
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        int pool = config == null ? 24 : Math.max(8, Math.min(30, config.getCandidatePoolSize()));
        int roots = Math.min(14, pool);
        generatePhase(plan.getId(), roots, pool - roots,
                properties.getThreadPlan().getReadyMinTopLevel(),
                properties.getThreadPlan().getReadyMinItems(),
                origin, null, true);
        return planReadyOrActive(plan.getId(), postId, "Phase2");
    }

    /**
     * Partner-arrival path when Call2 already returned structured comment candidates
     * (same response as partner body). Cancels unpublished via revision bump, then persists
     * phase2 items with {@code scheduledAt} on/after {@code partnerPublishedAt}.
     */
    @Transactional
    public boolean attachPhase2FromCall2Response(String postId, int revision, String title,
                                                  String authorBody, String partnerBody, String category,
                                                  Instant partnerPublishedAt,
                                                  Map<String, Object> call2Response) {
        if (!planModeEnabled() || postId == null || postId.isBlank() || call2Response == null) {
            return false;
        }
        Instant origin = partnerPublishedAt != null ? partnerPublishedAt : Instant.now();
        String sourceBody = combinePairedSourceBody(authorBody, partnerBody);
        AiThreadPlan plan = planService.requestPlan(
                postId, Math.max(1, revision), "AI_POST", origin, title, sourceBody, category);
        reGroundPlanSource(plan, title, sourceBody, category);
        if (plan.getStatus() == ThreadPlanStatus.READY || plan.getStatus() == ThreadPlanStatus.ACTIVE) {
            return true;
        }
        if (plan.getStatus() != ThreadPlanStatus.REQUESTED
                && plan.getStatus() != ThreadPlanStatus.GENERATING) {
            log.warn("Phase2 Call2 attach skipped plan {} status={}", plan.getId(), plan.getStatus());
            return false;
        }
        if (plan.getStatus() == ThreadPlanStatus.REQUESTED) {
            planService.markGenerating(plan.getId(),
                    properties.getThreadPlan().getAiPostProvider(),
                    properties.getThreadPlan().getAiPostModel());
        }
        Map<String, Object> response = new LinkedHashMap<>(call2Response);
        // Call2 may nest comments under "items" or "comments" — prefer items.
        if (!(response.get("items") instanceof List<?>) && response.get("comments") instanceof List<?> comments) {
            response.put("items", comments);
        }
        candidateScheduleSupport.clampScheduledAtsOnOrAfter(response, origin);
        try {
            persistAndFinalize(plan.getId(), response, null);
        } catch (IllegalArgumentException e) {
            log.warn("Phase2 Call2 attach rejected for plan {}: {}", plan.getId(), e.getMessage());
            planService.markFailed(plan.getId(), "INVALID_STRUCTURED_OUTPUT");
            return false;
        }
        return planReadyOrActive(plan.getId(), postId, "Phase2-Call2");
    }

    /**
     * Call2 context helper: up to {@code limit} (clamped 1..{@link #CALL2_COMMENT_CONTEXT_MAX})
     * latest published top-level comments. Fewer than requested is OK (including 0).
     */
    public List<Map<String, Object>> loadLatestPublishedTopLevelComments(String postId, int limit) {
        if (postId == null || postId.isBlank()) return List.of();
        int size = limit <= 0 ? CALL2_COMMENT_CONTEXT_DEFAULT
                : Math.min(CALL2_COMMENT_CONTEXT_MAX, Math.max(1, limit));
        List<CommentThreadDto> raw = backendBotClient.getComments(postId, 0, size);
        if (raw == null || raw.isEmpty()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(Math.min(size, raw.size()));
        for (CommentThreadDto c : raw) {
            if (c == null) continue;
            // API returns top-level threads; nested replies live under replies — skip empty bodies.
            String body = c.getBody();
            if (body == null || body.isBlank()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            if (c.getId() != null) row.put("id", c.getId());
            row.put("body", body.strip());
            if (c.getAuthorNickname() != null) row.put("authorNickname", c.getAuthorNickname());
            if (c.getAuthorId() != null) row.put("authorId", c.getAuthorId());
            out.add(row);
            if (out.size() >= size) break;
        }
        return out;
    }

    /** Author + partner bodies for comment-plan grounding (paired posts only). */
    public static String combinePairedSourceBody(String authorBody, String partnerBody) {
        String author = authorBody == null ? "" : authorBody.strip();
        String partner = partnerBody == null ? "" : partnerBody.strip();
        if (partner.isEmpty()) return author;
        if (author.isEmpty()) return partner;
        return "[작성자]\n" + author + "\n\n[상대방]\n" + partner;
    }

    private void reGroundPlanSource(AiThreadPlan plan, String title, String sourceBody, String category) {
        if (sourceBody == null || sourceBody.isBlank()) return;
        plan.setSourceTitle(title);
        plan.setSourceBody(sourceBody);
        plan.setSourceCategory(category);
        if (!"AI_POST".equals(plan.getSourceType())) {
            plan.setSourceType("AI_POST");
        }
        planRepository.save(plan);
    }

    private boolean planReadyOrActive(String planId, String postId, String label) {
        AiThreadPlan after = planRepository.findById(planId).orElse(null);
        if (after == null) return false;
        boolean ok = after.getStatus() == ThreadPlanStatus.READY || after.getStatus() == ThreadPlanStatus.ACTIVE;
        if (!ok) {
            log.warn("{} comment plan {} for post {} ended as {} failure={}",
                    label, after.getId(), postId, after.getStatus(), after.getFailureCode());
        }
        return ok;
    }

    /** One retry only, with exactly the same provider/model snapshot. */
    public void generateOne(String planId) {
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        int pool = config == null ? 24 : Math.max(8, Math.min(30, config.getCandidatePoolSize()));
        int roots = Math.min(14, pool);
        generatePhase(planId, roots, pool - roots,
                properties.getThreadPlan().getReadyMinTopLevel(),
                properties.getThreadPlan().getReadyMinItems(),
                null, null, false);
    }

    /**
     * Shared generation path for default, phase1, and phase2 plans.
     *
     * @param partnerAtExclusive when non-null, clamp all scheduledAt strictly before this (phase1)
     * @param clampOnOrAfterOrigin when true and origin non-null, clamp scheduledAt on/after origin (phase2)
     */
    private void generatePhase(String planId,
                               int maxTopLevel, int maxReplies,
                               int readyMinTopLevel, int readyMinItems,
                               Instant scheduleOrigin,
                               Instant partnerAtExclusive,
                               boolean clampOnOrAfterOrigin) {
        AiThreadPlan plan = planRepository.findById(planId).orElse(null);
        if (plan == null || plan.getStatus() != ThreadPlanStatus.REQUESTED
                || Instant.now().isAfter(plan.getAbsoluteExpiresAt())) {
            return;
        }
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        String provider = resolveProvider(plan.getSourceType(), config);
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) return;
        String model = "AI_POST".equals(plan.getSourceType()) ? properties.getThreadPlan().getAiPostModel()
                : properties.getThreadPlan().getHumanPlanModel();
        planService.markGenerating(planId, provider, model);

        // LLM Generation Gate check: skip generation if held
        if (llmGenerationGateService.isHeld()) {
            log.info("[ThreadPlanGeneration] generation held (LLM gate) planId={}", planId);
            planService.markFailed(planId, "GENERATION_HELD_BY_GATE");
            return;
        }

        PlanRequestBuilt built = planRequest(plan, provider, model, maxTopLevel, maxReplies);
        Optional<Map<String, Object>> response = llmClient.generateThreadPlan(built.request());
        if (response.isEmpty()) response = llmClient.generateThreadPlan(built.request());
        if (response.isEmpty()) {
            planService.markFailed(planId, "GENERATION_FAILED");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>(response.get());
        Instant origin = scheduleOrigin != null ? scheduleOrigin : plan.getPublishedAt();
        if (partnerAtExclusive != null) {
            candidateScheduleSupport.clampScheduledAtsBefore(body, origin, partnerAtExclusive);
        } else if (clampOnOrAfterOrigin && origin != null) {
            candidateScheduleSupport.clampScheduledAtsOnOrAfter(body, origin);
        }
        try {
            persistAndFinalize(planId, body, built.castIds(), readyMinTopLevel, readyMinItems);
        } catch (IllegalArgumentException e) {
            log.warn("Plan {} rejected: {}", planId, e.getMessage());
            planService.markFailed(planId, "INVALID_STRUCTURED_OUTPUT");
        }
    }

    /** DB provider가 SSOT. OFF면 OFF다(관리자 결정을 yml이 뒤집지 않는다). row 자체가 없을 때만 yml 기본값. */
    private String resolveProvider(String sourceType, AiUserGenerationConfig config) {
        boolean aiPost = "AI_POST".equals(sourceType);
        if (config == null) {
            return aiPost ? properties.getThreadPlan().getAiPostProvider() : properties.getThreadPlan().getHumanPlanProvider();
        }
        String fromDb = aiPost ? config.getProviderAiPostBundle() : config.getProviderHumanPostPlan();
        return (fromDb == null || fromDb.isBlank()) ? "OFF" : fromDb;
    }

    String resolveProviderForTest(String sourceType, AiUserGenerationConfig config) { return resolveProvider(sourceType, config); }

    private PlanRequestBuilt planRequest(AiThreadPlan plan, String provider, String model, int maxTopLevel, int maxReplies) {
        // Full active pool for rotation (no fixed limit(24)) — but a single request's cast is
        // capped+shuffled so the prompt stays under Claude's 200K-token budget (2026-08-01 outage:
        // sending all 150 personas' voice_profile ≈ 306K tokens, 173/173 REQUESTED plans FAILED).
        List<Persona> active = personaRepository.findByActiveTrue();
        List<Persona> cast = PlanPersonaMapper.capCastPool(active, properties.getThreadPlan().getPlanPersonaCastMax());
        List<Map<String, Object>> personas = planPersonaMapper.mapCast(cast);
        Set<String> castIds = planPersonaMapper.castIds(personas);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("kind", "AI_POST".equals(plan.getSourceType()) ? "AI_POST" : "HUMAN_POST");
        r.put("provider", provider); if (model != null && !model.isBlank()) r.put("model", model);
        r.put("promptOverrides", promptTemplateCache.overrides());
        r.put("correlationId", "thread-plan-" + plan.getId()); r.put("postId", plan.getPostId());
        r.put("timeoutMs", generationConfigSupport.bundleTimeoutMs());
        r.put("postRevision", (long) plan.getPostRevision()); r.put("existingTitle", nullToEmpty(plan.getSourceTitle()));
        r.put("existingBody", nullToEmpty(plan.getSourceBody())); r.put("category", nullToEmpty(plan.getSourceCategory()));
        r.put("personas", personas);
        r.put("maxTopLevel", Math.max(0, maxTopLevel));
        r.put("maxReplies", Math.max(0, maxReplies));
        // Floor=1 so LLM parsePlan accepts sparse plans; quality gate (WP4) drops later.
        r.put("minTopLevel", 1); r.put("minItems", 1);
        return new PlanRequestBuilt(r, castIds);
    }

    private record PlanRequestBuilt(Map<String, Object> request, Set<String> castIds) { }

    /**
     * Quality-gate → persist kept → READY+ACTIVE.
     * <p>If operational READY mins fail: regenerate comment candidates via LLM once
     * ({@code HUMAN_POST}). If still below mins (or regen fails), persist whatever kept
     * items remain and activate as a thin READY — never discard a usable partial thread.
     */
    @Transactional
    public ThreadQualityGate.QualityResult persistAndFinalize(String planId, Map<String, Object> response) {
        return persistAndFinalize(planId, response, null);
    }

    @Transactional
    public ThreadQualityGate.QualityResult persistAndFinalize(String planId, Map<String, Object> response,
                                                               Set<String> allowedPersonaIds) {
        OrchestratorProperties.ThreadPlan cfg = properties.getThreadPlan();
        return persistAndFinalize(planId, response, allowedPersonaIds,
                cfg.getReadyMinTopLevel(), cfg.getReadyMinItems());
    }

    @Transactional
    public ThreadQualityGate.QualityResult persistAndFinalize(String planId, Map<String, Object> response,
                                                               Set<String> allowedPersonaIds,
                                                               int readyMinTopLevel, int readyMinItems) {
        ThreadQualityGate.QualityResult first =
                evaluateResponse(planId, response, allowedPersonaIds, readyMinTopLevel, readyMinItems);
        ThreadQualityGate.QualityResult chosen = first;

        if (!first.passedOperationalMin()) {
            log.warn("Plan {} below READY mins (kept={}, dropped={}): {} — attempting 1 quality regen",
                    planId, first.keptItems().size(), first.dropped(), first.reasons());
            Optional<Map<String, Object>> retryBody = regenerateCommentsForQuality(planId, allowedPersonaIds);
            if (retryBody.isPresent()) {
                try {
                    ThreadQualityGate.QualityResult second = evaluateResponse(
                            planId, retryBody.get(), allowedPersonaIds, readyMinTopLevel, readyMinItems);
                    if (second.passedOperationalMin()) {
                        persistKeptItems(planId, second);
                        activateReady(planId, second);
                        return second;
                    }
                    // Prefer the retry's kept set when non-empty; else fall back to first.
                    chosen = second.keptItems().isEmpty() && !first.keptItems().isEmpty() ? first : second;
                    log.warn("Plan {} quality regen still below READY mins (kept={}, dropped={}): {} — thin READY",
                            planId, chosen.keptItems().size(), chosen.dropped(), chosen.reasons());
                } catch (IllegalArgumentException invalid) {
                    log.warn("Plan {} quality regen rejected: {} — thin READY from first pass",
                            planId, invalid.getMessage());
                }
            } else {
                log.warn("Plan {} quality regen unavailable — thin READY from first pass (kept={})",
                        planId, first.keptItems().size());
            }
            persistKeptItems(planId, chosen);
            activateReady(planId, chosen);
            return chosen;
        }

        persistKeptItems(planId, first);
        activateReady(planId, first);
        return first;
    }

    private void activateReady(String planId, ThreadQualityGate.QualityResult quality) {
        planService.markReady(planId);
        planService.activate(planId);
        seedInterestedPersonasBestEffort(planId, quality);
    }

    /**
     * Comment-only LLM regen after a READY-min miss. Always {@code HUMAN_POST}.
     * Returns empty when gate is held, provider OFF, or source body missing.
     */
    Optional<Map<String, Object>> regenerateCommentsForQuality(String planId, Set<String> allowedPersonaIds) {
        AiThreadPlan plan = planRepository.findById(planId).orElse(null);
        if (plan == null) return Optional.empty();
        String title = nullToEmpty(plan.getSourceTitle());
        String body = nullToEmpty(plan.getSourceBody());
        if (title.isBlank() || body.isBlank()) {
            log.warn("Plan {} quality regen skipped: missing source title/body", planId);
            return Optional.empty();
        }
        if (llmGenerationGateService != null && llmGenerationGateService.isHeld()) {
            log.info("Plan {} quality regen skipped: LLM gate held", planId);
            return Optional.empty();
        }
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        String provider = resolveProvider("HUMAN_POST", config);
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) {
            log.warn("Plan {} quality regen skipped: HUMAN_POST provider OFF", planId);
            return Optional.empty();
        }
        String model = properties.getThreadPlan().getHumanPlanModel();
        int pool = config == null ? 24 : Math.max(8, Math.min(30, config.getCandidatePoolSize()));
        int roots = Math.min(14, pool);
        int replies = Math.max(0, pool - roots);

        Set<String> excluded = loadStorySidePersonaIds(plan.getPostId());
        List<Persona> poolPersonas;
        if (allowedPersonaIds != null && !allowedPersonaIds.isEmpty()) {
            poolPersonas = new ArrayList<>();
            for (String id : allowedPersonaIds) {
                if (id == null || id.isBlank() || excluded.contains(id)) continue;
                personaRepository.findById(id).ifPresent(p -> {
                    if (p.isActive()) poolPersonas.add(p);
                });
            }
        } else {
            poolPersonas = new ArrayList<>();
            for (Persona p : PlanPersonaMapper.capCastPool(
                    personaRepository.findByActiveTrue(), properties.getThreadPlan().getPlanPersonaCastMax())) {
                if (p != null && !excluded.contains(p.getId())) poolPersonas.add(p);
            }
        }
        if (poolPersonas.isEmpty()) {
            log.warn("Plan {} quality regen skipped: empty comment cast", planId);
            return Optional.empty();
        }
        List<Map<String, Object>> personas = planPersonaMapper.mapCast(poolPersonas);
        Set<String> castIds = planPersonaMapper.castIds(personas);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("kind", "HUMAN_POST");
        req.put("provider", provider);
        if (model != null && !model.isBlank()) req.put("model", model);
        req.put("promptOverrides", promptTemplateCache.overrides());
        req.put("correlationId", "thread-plan-quality-regen-" + plan.getId());
        req.put("postId", plan.getPostId());
        req.put("timeoutMs", generationConfigSupport.bundleTimeoutMs());
        req.put("postRevision", (long) plan.getPostRevision());
        req.put("existingTitle", title);
        req.put("existingBody", body);
        req.put("category", nullToEmpty(plan.getSourceCategory()));
        req.put("personas", personas);
        req.put("maxTopLevel", roots);
        req.put("maxReplies", replies);
        req.put("minTopLevel", 1);
        req.put("minItems", 1);
        return invokeCommentRegen(plan, req, excluded);
    }

    private Optional<Map<String, Object>> invokeCommentRegen(AiThreadPlan plan, Map<String, Object> req,
                                                             Set<String> excludedStory) {
        plan.setGenerationAttempts(plan.getGenerationAttempts() + 1);
        planRepository.save(plan);
        Optional<Map<String, Object>> response = llmClient.generateThreadPlan(req);
        if (response.isEmpty()) {
            response = llmClient.generateThreadPlan(req);
        }
        if (response.isEmpty()) {
            log.warn("Plan {} quality regen LLM empty", plan.getId());
            return Optional.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>(response.get());
        if (!(body.get("items") instanceof List<?>) && body.get("comments") instanceof List<?> comments) {
            body.put("items", comments);
        }
        if (excludedStory != null && !excludedStory.isEmpty()) {
            int stripped = StoryPersonaCommentFilter.stripFromResponse(body, excludedStory);
            if (stripped > 0) {
                log.info("Plan {} quality regen stripped {} story-persona comment(s)", plan.getId(), stripped);
            }
        }
        Instant origin = plan.getPublishedAt() != null ? plan.getPublishedAt() : Instant.now();
        candidateScheduleSupport.rescheduleFromPublishAt(body, origin);
        return Optional.of(body);
    }

    /** Best-effort PLAN_CAST seed into ai_post_interested_personas; never fails READY. */
    private void seedInterestedPersonasBestEffort(String planId, ThreadQualityGate.QualityResult quality) {
        try {
            AiThreadPlan plan = planRepository.findById(planId).orElse(null);
            if (plan == null || plan.getPostId() == null || plan.getPostId().isBlank()) return;
            Set<String> cast = new LinkedHashSet<>();
            for (Map<String, Object> row : quality.keptItems()) {
                String personaId = text(row.get("personaId"));
                if (!personaId.isBlank()) cast.add(personaId);
            }
            interestedPersonaSeeder.seedFromPlanCast(plan.getPostId(), cast);
        } catch (Exception e) {
            log.warn("interested persona seed failed for plan {}: {}", planId, e.getMessage());
        }
    }

    /** Persist without an explicit cast set — uses currently active persona ids as the cast. */
    @Transactional
    ThreadQualityGate.QualityResult persistResponse(String planId, Map<String, Object> response) {
        return persistResponse(planId, response, null);
    }

    /**
     * Runs {@link ThreadQualityGate}, then persists kept items when operational mins pass.
     * Callers that need READY/FAILED/thin-READY should use {@link #persistAndFinalize}.
     *
     * @param allowedPersonaIds {@code null} → active personas; empty set → skip cast check
     */
    @Transactional
    ThreadQualityGate.QualityResult persistResponse(String planId, Map<String, Object> response,
                                                     Set<String> allowedPersonaIds) {
        OrchestratorProperties.ThreadPlan cfg = properties.getThreadPlan();
        return persistResponse(planId, response, allowedPersonaIds,
                cfg.getReadyMinTopLevel(), cfg.getReadyMinItems());
    }

    @Transactional
    ThreadQualityGate.QualityResult persistResponse(String planId, Map<String, Object> response,
                                                     Set<String> allowedPersonaIds,
                                                     int readyMinTopLevel, int readyMinItems) {
        ThreadQualityGate.QualityResult quality =
                evaluateResponse(planId, response, allowedPersonaIds, readyMinTopLevel, readyMinItems);
        if (!quality.passedOperationalMin()) {
            return quality;
        }
        persistKeptItems(planId, quality);
        return quality;
    }

    ThreadQualityGate.QualityResult evaluateResponse(String planId, Map<String, Object> response,
                                                     Set<String> allowedPersonaIds,
                                                     int readyMinTopLevel, int readyMinItems) {
        AiThreadPlan plan = planRepository.findById(planId).orElseThrow();
        Object rawItems = response.get("items");
        if (!(rawItems instanceof List<?> rows) || rows.isEmpty() || rows.size() > 24) {
            throw new IllegalArgumentException("invalid item count");
        }
        Set<String> cast = allowedPersonaIds != null
                ? allowedPersonaIds
                : personaRepository.findByActiveTrue().stream()
                        .map(Persona::getId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> excludedStory = loadStorySidePersonaIds(plan.getPostId());
        OrchestratorProperties.ThreadPlan cfg = properties.getThreadPlan();
        return qualityGate.evaluate(
                rows,
                cast,
                personaRepository::existsById,
                readyMinTopLevel,
                readyMinItems,
                cfg.getStanceShareMax(),
                excludedStory);
    }

    void persistKeptItems(String planId, ThreadQualityGate.QualityResult quality) {
        if (quality == null || quality.keptItems() == null || quality.keptItems().isEmpty()) {
            return;
        }
        AiThreadPlan plan = planRepository.findById(planId).orElseThrow();
        Map<String, String> refs = new HashMap<>();
        int sequence = 0;
        for (Map<String, Object> row : quality.keptItems()) {
            String ref = text(row.get("ref"));
            String parentRef = text(row.get("parentRef"));
            String body = text(row.get("body"));
            String personaId = text(row.get("personaId"));
            String parentItemId = parentRef.isBlank() ? null : refs.get(parentRef);
            ThreadPlanItemType type = parentItemId == null ? ThreadPlanItemType.COMMENT : ThreadPlanItemType.REPLY;
            Instant stored = candidateScheduleSupport.parseScheduledAt(row.get("scheduledAt"));
            Instant scheduled = stored != null
                    ? stored
                    : candidateScheduleSupport.schedule(plan.getPublishedAt(), sequence, type == ThreadPlanItemType.REPLY);
            sequence++;
            AiThreadPlanItem.AiThreadPlanItemBuilder itemBuilder = AiThreadPlanItem.builder().planId(planId).itemType(type)
                    .status(ThreadPlanItemStatus.SCHEDULED).sequenceNo(sequence).parentItemId(parentItemId)
                    .personaId(personaId).targetPostId(plan.getPostId()).body(body).scheduledAt(scheduled)
                    .notBefore(scheduled).idempotencyKey(planId + ":" + ref);
            applyOptionalItemFields(itemBuilder, row);
            AiThreadPlanItem item = itemBuilder.build();
            itemRepository.save(item);
            refs.put(ref, item.getId());
        }
    }

    /**
     * Post author + partner must never be scheduled as bystander comment personas.
     * Missing/unknown post → empty set (gate skips exclusion).
     */
    Set<String> loadStorySidePersonaIds(String postId) {
        if (postId == null || postId.isBlank() || jdbcTemplate == null) return Set.of();
        try {
            Set<String> ids = jdbcTemplate.query(
                    "SELECT author_id, partner_user_id FROM posts WHERE id = ? AND deleted_at IS NULL",
                    rs -> {
                        Set<String> out = new LinkedHashSet<>();
                        if (rs.next()) {
                            String author = rs.getString(1);
                            String partner = rs.getString(2);
                            if (author != null && !author.isBlank()) out.add(author);
                            if (partner != null && !partner.isBlank()) out.add(partner);
                        }
                        return out;
                    },
                    postId);
            return ids == null ? Set.of() : ids;
        } catch (Exception e) {
            log.warn("story-side persona lookup failed post={}: {}", postId, e.getMessage());
            return Set.of();
        }
    }

    /** Soft-set stance / sourceExampleId if W1-H entity fields exist; otherwise no-op. */
    private static void applyOptionalItemFields(AiThreadPlanItem.AiThreadPlanItemBuilder itemBuilder, Map<?, ?> row) {
        Object stance = row.get("stance");
        if (stance != null && !String.valueOf(stance).isBlank()) {
            try {
                AiThreadPlanItem.AiThreadPlanItemBuilder.class.getMethod("stance", String.class)
                        .invoke(itemBuilder, String.valueOf(stance).trim());
            } catch (ReflectiveOperationException ignored) { /* W1-H pending */ }
        }
        Object sourceExampleId = row.get("sourceExampleId");
        if (sourceExampleId instanceof Number n) {
            try {
                AiThreadPlanItem.AiThreadPlanItemBuilder.class.getMethod("sourceExampleId", Long.class)
                        .invoke(itemBuilder, n.longValue());
            } catch (ReflectiveOperationException ignored) { /* W1-H pending */ }
        }
    }

    /** Package-visible for unit tests; delegates to {@link CandidateScheduleSupport}. */
    Instant schedule(Instant publishedAt, int index, boolean reply) {
        return candidateScheduleSupport.schedule(publishedAt, index, reply);
    }
    private static String text(Object value) {
        if (value == null) return "";
        return LiteralNewlineNormalizer.normalize(String.valueOf(value)).trim();
    }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private boolean planModeEnabled() { return properties.isEnabled() && properties.getThreadPlan().isEnabled()
            && configRepository.findById(1).map(c -> !c.isAiUserKillSwitch()).orElse(false); }
}
