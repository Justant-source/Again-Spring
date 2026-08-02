package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadPlanGenerationService {
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

    @Transactional
    public void generateRequestedPlans() {
        if (!planModeEnabled()) return;
        planRepository.lockByStatus(ThreadPlanStatus.REQUESTED, PageRequest.of(0, 5)).stream()
                .map(AiThreadPlan::getId).toList()
                .forEach(this::generateOne);
    }

    /**
     * Solo AI posts pre-bake comment candidates in {@code generateAndHold}. Paired posts go live
     * via the invite flow and previously only left a REQUESTED plan that stalls while DB
     * {@code provider_*} is OFF (daytime + nightly EXIT trap). Generate comments immediately
     * after the pair is public, falling back to yml providers when the DB gate is OFF.
     *
     * @return true when the plan is READY/ACTIVE (or already was)
     */
    @Transactional
    public boolean ensureCommentPlanForPairedPost(String postId, int revision, String title,
                                                   String authorBody, String partnerBody, String category) {
        if (!planModeEnabled() || postId == null || postId.isBlank()) {
            return false;
        }
        Instant now = Instant.now();
        String sourceBody = combinePairedSourceBody(authorBody, partnerBody);
        AiThreadPlan plan = planService.requestPlan(
                postId, Math.max(1, revision), "AI_POST", now, title, sourceBody, category);
        // Outbox may have created this revision first with author-only body / HUMAN_POST.
        // Re-ground to the paired snapshot before generating.
        if (sourceBody != null && !sourceBody.isBlank()) {
            plan.setSourceTitle(title);
            plan.setSourceBody(sourceBody);
            plan.setSourceCategory(category);
            if (!"AI_POST".equals(plan.getSourceType())) {
                plan.setSourceType("AI_POST");
            }
            planRepository.save(plan);
        }
        if (plan.getStatus() == ThreadPlanStatus.READY || plan.getStatus() == ThreadPlanStatus.ACTIVE) {
            return true;
        }
        if (plan.getStatus() != ThreadPlanStatus.REQUESTED) {
            log.warn("Paired comment plan {} for post {} is {} — skip force generate",
                    plan.getId(), postId, plan.getStatus());
            return false;
        }
        generateOne(plan.getId(), true);
        AiThreadPlan after = planRepository.findById(plan.getId()).orElse(plan);
        boolean ok = after.getStatus() == ThreadPlanStatus.READY || after.getStatus() == ThreadPlanStatus.ACTIVE;
        if (!ok) {
            log.warn("Paired comment plan {} for post {} ended as {} failure={}",
                    after.getId(), postId, after.getStatus(), after.getFailureCode());
        }
        return ok;
    }

    /** Author + partner bodies for comment-plan grounding (paired posts only). */
    public static String combinePairedSourceBody(String authorBody, String partnerBody) {
        String author = authorBody == null ? "" : authorBody.strip();
        String partner = partnerBody == null ? "" : partnerBody.strip();
        if (partner.isEmpty()) return author;
        if (author.isEmpty()) return partner;
        return "[작성자]\n" + author + "\n\n[상대방]\n" + partner;
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
        AiThreadPlan plan = planRepository.findById(planId).orElse(null);
        if (plan == null || plan.getStatus() != ThreadPlanStatus.REQUESTED || Instant.now().isAfter(plan.getAbsoluteExpiresAt())) return;
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        String provider = resolveProvider(plan.getSourceType(), config, fallbackToYmlWhenOff);
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) return;
        String model = "AI_POST".equals(plan.getSourceType()) ? properties.getThreadPlan().getAiPostModel()
                : properties.getThreadPlan().getHumanPlanModel();
        planService.markGenerating(planId, provider, model);
        int pool = config == null ? 24 : Math.max(1, Math.min(24, config.getCandidatePoolSize()));
        PlanRequestBuilt built = planRequest(plan, provider, model, pool);
        Optional<Map<String, Object>> response = llmClient.generateThreadPlan(built.request());
        if (response.isEmpty()) response = llmClient.generateThreadPlan(built.request()); // same provider, bounded retry
        if (response.isEmpty()) {
            planService.markFailed(planId, "GENERATION_FAILED");
            return;
        }
        try {
            persistAndFinalize(planId, response.get(), built.castIds());
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

    private PlanRequestBuilt planRequest(AiThreadPlan plan, String provider, String model, int pool) {
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
        r.put("timeoutMs", properties.getThreadPlan().getBundleTimeoutMs());
        r.put("postRevision", (long) plan.getPostRevision()); r.put("existingTitle", nullToEmpty(plan.getSourceTitle()));
        r.put("existingBody", nullToEmpty(plan.getSourceBody())); r.put("category", nullToEmpty(plan.getSourceCategory()));
        int roots = Math.min(14, pool); r.put("personas", personas); r.put("maxTopLevel", roots); r.put("maxReplies", pool - roots);
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
        ThreadQualityGate.QualityResult quality = persistResponse(planId, response, allowedPersonaIds);
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
                cfg.getReadyMinTopLevel(),
                cfg.getReadyMinItems(),
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
