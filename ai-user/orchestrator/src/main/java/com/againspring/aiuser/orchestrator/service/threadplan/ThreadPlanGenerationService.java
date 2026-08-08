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
import com.againspring.aiuser.orchestrator.util.LiteralNewlineNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
        generatePhase(plan.getId(), true, PHASE1_MAX_TOP_LEVEL, PHASE1_MAX_REPLIES,
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
        generatePhase(plan.getId(), true, roots, pool - roots,
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
        generateOne(planId, false);
    }

    /**
     * @param fallbackToYmlWhenOff when true, use application.yml providers if DB config is OFF
     *        (paired AI posts must ship with scheduled comments even outside the nightly window)
     */
    public void generateOne(String planId, boolean fallbackToYmlWhenOff) {
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        int pool = config == null ? 24 : Math.max(8, Math.min(30, config.getCandidatePoolSize()));
        int roots = Math.min(14, pool);
        generatePhase(planId, fallbackToYmlWhenOff, roots, pool - roots,
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
    private void generatePhase(String planId, boolean fallbackToYmlWhenOff,
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
        String provider = resolveProvider(plan.getSourceType(), config, fallbackToYmlWhenOff);
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) return;
        String model = "AI_POST".equals(plan.getSourceType()) ? properties.getThreadPlan().getAiPostModel()
                : properties.getThreadPlan().getHumanPlanModel();
        planService.markGenerating(planId, provider, model);
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

    private String resolveProvider(String sourceType, AiUserGenerationConfig config, boolean fallbackToYmlWhenOff) {
        boolean aiPost = "AI_POST".equals(sourceType);
        String yml = aiPost ? properties.getThreadPlan().getAiPostProvider()
                : properties.getThreadPlan().getHumanPlanProvider();
        if (config == null) {
            return yml;
        }
        String fromDb = aiPost ? config.getProviderAiPostBundle() : config.getProviderHumanPostPlan();
        if (fromDb != null && !fromDb.isBlank() && !"OFF".equalsIgnoreCase(fromDb)) {
            return fromDb;
        }
        if (fallbackToYmlWhenOff) {
            return yml;
        }
        return fromDb;
    }

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
     * Single insertion point for hold replay and live publish: quality-gate → persist kept
     * → READY+ACTIVE or FAILED({@link ThreadQualityGate#FAILURE_QUALITY_BELOW_MIN}).
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
        ThreadQualityGate.QualityResult quality =
                persistResponse(planId, response, allowedPersonaIds, readyMinTopLevel, readyMinItems);
        if (quality.passedOperationalMin()) {
            planService.markReady(planId);
            planService.activate(planId);
            seedInterestedPersonasBestEffort(planId, quality);
        } else {
            log.warn("Plan {} below READY mins (kept={}, dropped={}): {}",
                    planId, quality.keptItems().size(), quality.dropped(), quality.reasons());
            planService.markFailed(planId, ThreadQualityGate.FAILURE_QUALITY_BELOW_MIN);
        }
        return quality;
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
     * Runs {@link ThreadQualityGate}, then persists only kept items when operational mins pass.
     * Does not invent filler. Callers that need READY/FAILED should use {@link #persistAndFinalize}.
     *
     * @param allowedPersonaIds {@code null} → active personas; empty set → skip cast check
     */
    @Transactional
    @SuppressWarnings("unchecked")
    ThreadQualityGate.QualityResult persistResponse(String planId, Map<String, Object> response,
                                                     Set<String> allowedPersonaIds) {
        OrchestratorProperties.ThreadPlan cfg = properties.getThreadPlan();
        return persistResponse(planId, response, allowedPersonaIds,
                cfg.getReadyMinTopLevel(), cfg.getReadyMinItems());
    }

    @Transactional
    @SuppressWarnings("unchecked")
    ThreadQualityGate.QualityResult persistResponse(String planId, Map<String, Object> response,
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
        OrchestratorProperties.ThreadPlan cfg = properties.getThreadPlan();
        ThreadQualityGate.QualityResult quality = qualityGate.evaluate(
                rows,
                cast,
                personaRepository::existsById,
                readyMinTopLevel,
                readyMinItems,
                cfg.getStanceShareMax());

        if (!quality.passedOperationalMin()) {
            // Do not store a thin tree that will never go READY — avoids orphan SCHEDULED rows.
            return quality;
        }

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
        return quality;
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
