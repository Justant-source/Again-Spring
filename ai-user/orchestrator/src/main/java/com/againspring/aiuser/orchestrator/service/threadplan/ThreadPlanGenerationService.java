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
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
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
    private final ContentSafetyGuard safetyGuard;
    private final OrchestratorProperties properties;
    private final AiUserGenerationConfigRepository configRepository;
    private final CandidateScheduleSupport candidateScheduleSupport;

    @Transactional
    public void generateRequestedPlans() {
        if (!planModeEnabled()) return;
        planRepository.lockByStatus(ThreadPlanStatus.REQUESTED, PageRequest.of(0, 5)).stream()
                .map(AiThreadPlan::getId).toList()
                .forEach(this::generateOne);
    }

    /** One retry only, with exactly the same provider/model snapshot. */
    public void generateOne(String planId) {
        AiThreadPlan plan = planRepository.findById(planId).orElse(null);
        if (plan == null || plan.getStatus() != ThreadPlanStatus.REQUESTED || Instant.now().isAfter(plan.getAbsoluteExpiresAt())) return;
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        String provider = config == null ? ("AI_POST".equals(plan.getSourceType()) ? properties.getThreadPlan().getAiPostProvider() : properties.getThreadPlan().getHumanPlanProvider())
                : ("AI_POST".equals(plan.getSourceType()) ? config.getProviderAiPostBundle() : config.getProviderHumanPostPlan());
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) return;
        String model = "AI_POST".equals(plan.getSourceType()) ? properties.getThreadPlan().getAiPostModel()
                : properties.getThreadPlan().getHumanPlanModel();
        planService.markGenerating(planId, provider, model);
        int pool = config == null ? 24 : Math.max(1, Math.min(24, config.getCandidatePoolSize()));
        Map<String, Object> request = planRequest(plan, provider, model, pool);
        Optional<Map<String, Object>> response = llmClient.generateThreadPlan(request);
        if (response.isEmpty()) response = llmClient.generateThreadPlan(request); // same provider, bounded retry
        if (response.isEmpty()) {
            planService.markFailed(planId, "GENERATION_FAILED");
            return;
        }
        try {
            persistResponse(planId, response.get());
            planService.markReady(planId);
            planService.activate(planId);
        } catch (IllegalArgumentException e) {
            log.warn("Plan {} rejected: {}", planId, e.getMessage());
            planService.markFailed(planId, "INVALID_STRUCTURED_OUTPUT");
        }
    }

    private Map<String, Object> planRequest(AiThreadPlan plan, String provider, String model, int pool) {
        List<Map<String, Object>> personas = personaRepository.findByActiveTrue().stream().limit(24).<Map<String, Object>>map(p -> Map.of(
                "personaId", p.getId(), "nickname", p.getId(),
                "voiceProfile", String.valueOf(p.getVoiceProfile()), "formality", "neutral")).toList();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("kind", "AI_POST".equals(plan.getSourceType()) ? "AI_POST" : "HUMAN_POST");
        r.put("provider", provider); if (model != null && !model.isBlank()) r.put("model", model);
        r.put("correlationId", "thread-plan-" + plan.getId()); r.put("postId", plan.getPostId());
        r.put("timeoutMs", properties.getThreadPlan().getBundleTimeoutMs());
        r.put("postRevision", (long) plan.getPostRevision()); r.put("existingTitle", nullToEmpty(plan.getSourceTitle()));
        r.put("existingBody", nullToEmpty(plan.getSourceBody())); r.put("category", nullToEmpty(plan.getSourceCategory()));
        int roots = Math.min(14, pool); r.put("personas", personas); r.put("maxTopLevel", roots); r.put("maxReplies", pool - roots);
        return r;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    void persistResponse(String planId, Map<String, Object> response) {
        AiThreadPlan plan = planRepository.findById(planId).orElseThrow();
        Object rawItems = response.get("items");
        if (!(rawItems instanceof List<?> rows) || rows.isEmpty() || rows.size() > 24) throw new IllegalArgumentException("invalid item count");
        Map<String, String> refs = new HashMap<>();
        int sequence = 0;
        for (Object raw : rows) {
            if (!(raw instanceof Map<?, ?> row)) throw new IllegalArgumentException("invalid item");
            String ref = text(row.get("ref")); String parentRef = text(row.get("parentRef"));
            String body = text(row.get("body")); String personaId = text(row.get("personaId"));
            if (ref.isBlank() || body.isBlank() || personaId.isBlank() || body.length() > 2000 || refs.containsKey(ref)) throw new IllegalArgumentException("invalid candidate");
            if (!personaRepository.existsById(personaId) || !safetyGuard.check(body, ContentSafetyGuard.ContentType.COMMENT).passed()) throw new IllegalArgumentException("unsafe candidate");
            String parentItemId = parentRef.isBlank() ? null : refs.get(parentRef);
            if (!parentRef.isBlank() && parentItemId == null) throw new IllegalArgumentException("unknown parent");
            ThreadPlanItemType type = parentItemId == null ? ThreadPlanItemType.COMMENT : ThreadPlanItemType.REPLY;
            Instant stored = candidateScheduleSupport.parseScheduledAt(row.get("scheduledAt"));
            Instant scheduled = stored != null
                    ? stored
                    : candidateScheduleSupport.schedule(plan.getPublishedAt(), sequence, type == ThreadPlanItemType.REPLY);
            sequence++;
            AiThreadPlanItem item = AiThreadPlanItem.builder().planId(planId).itemType(type)
                    .status(ThreadPlanItemStatus.SCHEDULED).sequenceNo(sequence).parentItemId(parentItemId)
                    .personaId(personaId).targetPostId(plan.getPostId()).body(body).scheduledAt(scheduled)
                    .notBefore(scheduled).idempotencyKey(planId + ":" + ref).build();
            itemRepository.save(item); refs.put(ref, item.getId());
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
