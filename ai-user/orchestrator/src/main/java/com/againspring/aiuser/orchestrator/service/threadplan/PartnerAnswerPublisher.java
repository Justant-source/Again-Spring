package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPartnerAnswer;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.service.GenerationConfigSupport;
import com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Fires delayed partner answers for AI paired posts at {@code scheduledPartnerAt}.
 * Call2 ({@code PAIRED_PHASE2}) generates partner body + phase2 comments using author
 * body and up to 5–8 published top-level comments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerAnswerPublisher {
    private static final String WORKER = "partner-answer-publisher";
    private static final int CALL2_CAST_MAX = 24;
    private static final int CALL2_COMMENT_CONTEXT = 8;

    private final PartnerAnswerLeaseService leases;
    private final PersonaRepository personas;
    private final LlmAiUserClient llmClient;
    private final BackendBotClient backend;
    private final ContentSafetyGuard safetyGuard;
    private final ThreadPlanGenerationService threadPlanGenerationService;
    private final OrchestratorProperties properties;
    private final AiUserGenerationConfigRepository generationConfigRepository;
    private final PlanPersonaMapper planPersonaMapper;
    private final JdbcTemplate jdbcTemplate;
    private final GenerationConfigSupport generationConfigSupport;
    private final LlmGenerationGateService llmGenerationGateService;

    public void publishDue() {
        if (!properties.isEnabled()) return;
        OrchestratorProperties.PairedPost config = properties.getPairedPost();
        if (config == null || !config.isEnabled() || !config.isPartnerPublisherEnabled()) return;

        boolean dbBlocked = generationConfigRepository.findById(1)
                .map(c -> c.isAiUserKillSwitch() || c.isScheduleExecutionPaused()).orElse(true);
        if (dbBlocked) {
            log.info("[PartnerAnswer] publishDue skip: ai_user_kill_switch or schedule_execution_paused");
            return;
        }

        int batchSize = Math.max(1, config.getPartnerPublishBatchSize());
        for (AiScheduledPartnerAnswer row : leases.claimDue(WORKER, batchSize, Duration.ofMinutes(5), Instant.now())) {
            publish(row);
        }
    }

    private void publish(AiScheduledPartnerAnswer row) {
        try {
            Persona partner = personas.findById(row.getPartnerPersonaId()).orElse(null);
            if (partner == null) {
                leases.releaseFailed(row.getId(), WORKER, "PARTNER_PERSONA_NOT_FOUND", false);
                return;
            }

            Optional<Call2Result> call2 = generateCall2(partner, row);
            if (call2.isEmpty()) {
                leases.releaseFailed(row.getId(), WORKER, "CALL2_GEN_FAILED", row.getAttemptCount() < 3);
                return;
            }
            Call2Result result = call2.get();
            ContentSafetyGuard.GuardResult guard =
                    safetyGuard.check(result.partnerBody(), ContentSafetyGuard.ContentType.POST);
            if (!guard.passed()) {
                leases.releaseFailed(row.getId(), WORKER, "CALL2_BLOCKED", false);
                return;
            }

            boolean ok = backend.submitPartnerAnswer(
                    row.getInviteToken(), null, result.partnerBody(), result.captureSplits());
            if (!ok) {
                leases.releaseFailed(row.getId(), WORKER, "PARTNER_SUBMIT_FAILED", row.getAttemptCount() < 3);
                return;
            }

            Instant partnerAt = Instant.now();
            int revision = lookupContentRevision(row.getPostId());
            String authorBody = row.getAuthorBody() != null ? row.getAuthorBody() : "";
            String title = row.getAuthorTitle() != null ? row.getAuthorTitle() : "갈등 사연";
            boolean planned = threadPlanGenerationService.attachPhase2FromCall2Response(
                    row.getPostId(), revision, title, authorBody, result.partnerBody(),
                    row.getCategory(), partnerAt, result.response());
            if (!planned) {
                log.warn("[PartnerAnswer] Phase2 attach not ready for post={} revision={}",
                        row.getPostId(), revision);
            }

            leases.complete(row.getId(), WORKER);
            log.info("[PartnerAnswer] ✅ post={} partner={} corrId={} phase2Items={}",
                    row.getPostId(),
                    partner.getId().length() >= 8 ? partner.getId().substring(0, 8) : partner.getId(),
                    row.getCorrelationId(),
                    result.itemCount());
        } catch (Exception e) {
            log.warn("[PartnerAnswer] publish failed id={}: {}", row.getId(), e.getMessage());
            leases.releaseFailed(row.getId(), WORKER, "PUBLISH_EXCEPTION", row.getAttemptCount() < 3);
        }
    }

    private Optional<Call2Result> generateCall2(Persona partner, AiScheduledPartnerAnswer row) {
        AiUserGenerationConfig config = generationConfigRepository.findById(1).orElse(null);
        String provider = config == null ? properties.getThreadPlan().getAiPostProvider()
                : config.getProviderAiPostBundle();
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) {
            provider = properties.getThreadPlan().getAiPostProvider();
        }
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) {
            log.info("[PartnerAnswer] Call2 skipped: provider OFF corrId={}", row.getCorrelationId());
            return Optional.empty();
        }
        String model = properties.getThreadPlan().getAiPostModel();
        String corr = row.getCorrelationId() == null ? "pair" : row.getCorrelationId();

        List<Map<String, Object>> publishedComments =
                threadPlanGenerationService.loadLatestPublishedTopLevelComments(
                        row.getPostId(), CALL2_COMMENT_CONTEXT);
        List<Map<String, Object>> commentCtx = new ArrayList<>(publishedComments.size());
        for (Map<String, Object> c : publishedComments) {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("body", c.get("body"));
            if (c.get("authorNickname") != null) rowMap.put("nickname", c.get("authorNickname"));
            if (c.get("createdAt") != null) rowMap.put("createdAt", c.get("createdAt"));
            commentCtx.add(rowMap);
        }

        List<Persona> pool = PlanPersonaMapper.capCastPool(personas.findByActiveTrue(), CALL2_CAST_MAX);
        List<Map<String, Object>> cast = planPersonaMapper.mapCast(pool);
        Map<String, Object> partnerMap = planPersonaMapper.mapAuthor(partner);
        List<Map<String, Object>> personasCast = new ArrayList<>();
        personasCast.add(partnerMap);
        for (Map<String, Object> p : cast) {
            if (partner.getId().equals(String.valueOf(p.getOrDefault("personaId", "")))) continue;
            personasCast.add(p);
            if (personasCast.size() >= CALL2_CAST_MAX) break;
        }

        Map<String, Object> authorPost = new LinkedHashMap<>();
        if (row.getAuthorPersonaId() != null && !row.getAuthorPersonaId().isBlank()) {
            authorPost.put("personaId", row.getAuthorPersonaId());
        }
        authorPost.put("title", row.getAuthorTitle() != null ? row.getAuthorTitle() : "");
        authorPost.put("body", row.getAuthorBody() != null ? row.getAuthorBody() : "");

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", provider);
        request.put("model", model);
        request.put("correlationId", corr + "-P2");
        request.put("timeoutMs", generationConfigSupport.bundleTimeoutMs());
        request.put("category", row.getCategory());
        request.put("authorPost", authorPost);
        request.put("partner", partnerMap);
        request.put("personas", personasCast);
        request.put("publishedTopLevelComments", commentCtx);
        request.put("includePartnerPost", true);
        request.put("maxTopLevel", 14);
        request.put("maxReplies", 10);
        request.put("minTopLevel", 1);
        request.put("minItems", 1);

        // LLM Generation Gate check: skip generation if held
        if (llmGenerationGateService.isHeld()) {
            log.info("[PartnerAnswer] Call2 generation held (LLM gate) corrId={}", corr);
            return Optional.empty();
        }

        Optional<Map<String, Object>> responseOpt = llmClient.generatePairedCall2(request);
        if (responseOpt.isEmpty()) return Optional.empty();
        Map<String, Object> response = new LinkedHashMap<>(responseOpt.get());
        if (!(response.get("items") instanceof List<?>) && response.get("comments") instanceof List<?> comments) {
            response.put("items", comments);
        }

        String partnerBody = extractPartnerBody(response);
        if (partnerBody == null || partnerBody.isBlank()) {
            log.warn("[PartnerAnswer] Call2 missing partner_post corrId={}", corr);
            return Optional.empty();
        }
        Set<String> storySides = new LinkedHashSet<>();
        if (row.getAuthorPersonaId() != null && !row.getAuthorPersonaId().isBlank()) {
            storySides.add(row.getAuthorPersonaId());
        }
        storySides.add(partner.getId());
        int stripped = StoryPersonaCommentFilter.stripFromResponse(response, storySides);
        if (stripped > 0) {
            log.info("[PartnerAnswer] Call2 stripped {} story-persona comment(s) corrId={}", stripped, corr);
        }
        int items = response.get("items") instanceof List<?> list ? list.size() : 0;
        return Optional.of(new Call2Result(partnerBody.strip(), extractPartnerSplits(response), response, items));
    }

    private static String extractPartnerBody(Map<String, Object> response) {
        Object raw = response.get("partner_post");
        if (raw == null) raw = response.get("partnerPost");
        if (raw instanceof Map<?, ?> post) {
            Object body = post.get("body");
            if (body != null) {
                String s = String.valueOf(body).trim();
                return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
            }
        }
        if (raw instanceof String s && !s.isBlank()) return s.trim();
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> extractPartnerSplits(Map<String, Object> response) {
        Object raw = response.get("partner_post");
        if (raw == null) raw = response.get("partnerPost");
        if (!(raw instanceof Map<?, ?> post)) return null;
        Object v = post.get("capture_split_after_lines");
        if (v == null) v = post.get("captureSplitAfterLines");
        if (!(v instanceof List<?> list) || list.isEmpty()) return null;
        List<Integer> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number n) out.add(n.intValue());
            else if (o instanceof String s) {
                try { out.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) { }
            }
        }
        return out.isEmpty() ? null : out;
    }

    private int lookupContentRevision(String postId) {
        try {
            Integer revision = jdbcTemplate.queryForObject(
                    "SELECT content_revision FROM posts WHERE id = ?", Integer.class, postId);
            return revision != null ? Math.max(1, revision) : 2;
        } catch (Exception e) {
            return 2;
        }
    }

    private record Call2Result(String partnerBody, List<Integer> captureSplits,
                               Map<String, Object> response, int itemCount) { }
}
