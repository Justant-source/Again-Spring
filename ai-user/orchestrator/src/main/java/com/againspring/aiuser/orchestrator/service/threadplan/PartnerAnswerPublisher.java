package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPartnerAnswer;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.safety.SourceOverlapGuard;
import com.againspring.aiuser.orchestrator.service.GenerationConfigSupport;
import com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService;
import com.againspring.aiuser.orchestrator.service.persona.PersonaLottery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    /**
     * persona-diversity-v4 WP2 재배선 — {@code PairedPostScheduler.PAIRED_SKELETON_KEY}와 동일한
     * 문자열이어야 한다(패키지가 달라 상수 참조 대신 리터럴을 맞춘다). Call1 hold 시점에
     * {@code ai_scheduled_posts.candidates_json}에 실린 계약7 골격을, 다른 lease/row로 나중에
     * 도는 Call2가 {@code scheduled_post_id}로 원본 hold row를 다시 읽어 꺼내 쓴다.
     */
    private static final String PAIRED_SKELETON_KEY = "_pairedSkeleton";
    private static final ObjectMapper SKELETON_JSON = new ObjectMapper();

    private final PartnerAnswerLeaseService leases;
    private final PersonaRepository personas;
    private final LlmAiUserClient llmClient;
    private final BackendBotClient backend;
    private final ContentSafetyGuard safetyGuard;
    private final SourceOverlapGuard sourceOverlapGuard;
    private final AiLearningClient aiLearningClient;
    private final ThreadPlanGenerationService threadPlanGenerationService;
    private final OrchestratorProperties properties;
    private final AiUserGenerationConfigRepository generationConfigRepository;
    private final PlanPersonaMapper planPersonaMapper;
    private final JdbcTemplate jdbcTemplate;
    private final GenerationConfigSupport generationConfigSupport;
    private final LlmGenerationGateService llmGenerationGateService;
    private final com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache promptTemplateCache;
    private final PersonaLottery personaLottery;

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

            // persona-diversity-v4 WP2 배선 — 상대방(B) 재구성 결과도 A측(PairedPostScheduler)과
            // 동일하게 원문 12-gram 대조를 거친다. Call2는 Call1과 다른 lease/row라 claim 시점의
            // ResolvedSource가 메모리에 없으므로, sourceExampleId로 example_bank 원문을 다시 조회해
            // (AiLearningClient#getExampleById) 이 스코프에서만 대조하고 버린다(로그·DB 미기록).
            if (result.sourceExampleId() != null) {
                Optional<String> sourceBody = fetchSourceBodyForOverlapCheck(result.sourceExampleId());
                if (sourceBody.isPresent()) {
                    SourceOverlapGuard.GuardResult overlap =
                            sourceOverlapGuard.check(result.partnerBody(), sourceBody.get());
                    if (!overlap.passed()) {
                        log.error("[PartnerAnswer] {} overlapRatio={} corrId={}",
                                overlap.reason(), overlap.overlapRatio(), row.getCorrelationId());
                        leases.releaseFailed(row.getId(), WORKER, "CALL2_SOURCE_OVERLAP", false);
                        return;
                    }
                }
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

        // WP3 계약 6: capCastPool(랜덤 셔플) 대신 가중 비복원 추첨으로 통일. 글 작성자·파트너
        // 둘 다 방청객(bystander) 캐스트에서 제외한다.
        Set<String> exclude = row.getAuthorPersonaId() != null && !row.getAuthorPersonaId().isBlank()
                ? Set.of(partner.getId(), row.getAuthorPersonaId()) : Set.of(partner.getId());
        List<Persona> drawnCommenters = personaLottery.drawCommenters(
                personas.findByActiveTrue(), row.getCategory(), exclude, CALL2_CAST_MAX - 1,
                java.util.concurrent.ThreadLocalRandom.current());
        Map<String, Object> partnerMap = planPersonaMapper.mapAuthor(partner);
        List<Map<String, Object>> personasCast = new ArrayList<>(1 + drawnCommenters.size());
        personasCast.add(partnerMap);
        personasCast.addAll(planPersonaMapper.mapCast(drawnCommenters));

        Map<String, Object> authorPost = new LinkedHashMap<>();
        if (row.getAuthorPersonaId() != null && !row.getAuthorPersonaId().isBlank()) {
            authorPost.put("personaId", row.getAuthorPersonaId());
        }
        authorPost.put("title", row.getAuthorTitle() != null ? row.getAuthorTitle() : "");
        authorPost.put("body", row.getAuthorBody() != null ? row.getAuthorBody() : "");

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", provider);
        request.put("model", model);
        request.put("promptOverrides", promptTemplateCache.overrides());
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

        // persona-diversity-v4 WP2 재배선 — Call1이 claim한 계약7 골격을 상대방(B) 재구성에도
        // 태운다. 골격이 없으면(claim 실패·freestyle) 기존 자유 생성 동작 그대로.
        Optional<PairedSkeleton> skeletonOpt = loadPairedSkeleton(row.getScheduledPostId());
        skeletonOpt.ifPresent(skeleton -> {
            if (skeleton.sourceContext() != null && !skeleton.sourceContext().isEmpty()) {
                request.put("sourceContext", skeleton.sourceContext());
            }
            if (skeleton.reconstructMode() != null) {
                request.put("reconstructMode", skeleton.reconstructMode());
            }
            if (skeleton.sourceExampleId() != null) {
                request.put("sourceExampleId", skeleton.sourceExampleId());
            }
            if (skeleton.bSideViable() != null) {
                request.put("bSideViable", skeleton.bSideViable());
            }
        });

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
        Long sourceExampleId = skeletonOpt.map(PairedSkeleton::sourceExampleId).orElse(null);
        return Optional.of(new Call2Result(
                partnerBody.strip(), extractPartnerSplits(response), response, items, sourceExampleId));
    }

    /**
     * example_bank 원문을 id로 재조회한다(claim 시점 값은 다른 lease/row라 메모리에 없음).
     * 조회 실패(learning 비활성·네트워크 오류·행 삭제)는 fail-open — 이 배선 이전(대조 없음)과
     * 같은 위험 수준을 유지할 뿐 더 나쁘게 만들지 않는다. 반환값은 호출 스코프를 벗어나지 않고
     * SourceOverlapGuard 대조 직후 버려진다(로그·DB 미기록).
     */
    private Optional<String> fetchSourceBodyForOverlapCheck(Long sourceExampleId) {
        try {
            return aiLearningClient.getExampleById(sourceExampleId)
                    .map(AiLearningClient.ExampleItem::getContent);
        } catch (Exception e) {
            log.debug("[PartnerAnswer] source refetch failed (non-critical) exampleId={}: {}",
                    sourceExampleId, e.getMessage());
            return Optional.empty();
        }
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
                               Map<String, Object> response, int itemCount, Long sourceExampleId) { }

    private record PairedSkeleton(Map<String, Object> sourceContext, Boolean reconstructMode,
                                   Long sourceExampleId, Boolean bSideViable) { }

    /**
     * Re-reads the Call1 hold row ({@code ai_scheduled_posts}) by {@code scheduledPostId} and
     * extracts the {@value #PAIRED_SKELETON_KEY} block {@link com.againspring.aiuser.orchestrator
     * .scheduler.PairedPostScheduler} embedded in its {@code candidates_json} — the same claimed
     * 계약7 골격 Call1 used, so Call2 can restate the same incident via {@code counterpart_claim}.
     * Row missing, JSON unparsable, or key absent → {@link Optional#empty()} (freestyle, unchanged
     * behavior).
     */
    private Optional<PairedSkeleton> loadPairedSkeleton(String scheduledPostId) {
        if (scheduledPostId == null || scheduledPostId.isBlank()) return Optional.empty();
        try {
            String candidatesJson = jdbcTemplate.queryForObject(
                    "SELECT candidates_json FROM ai_scheduled_posts WHERE id = ?",
                    String.class, scheduledPostId);
            if (candidatesJson == null || candidatesJson.isBlank()) return Optional.empty();
            Map<String, Object> root = SKELETON_JSON.readValue(candidatesJson, new TypeReference<Map<String, Object>>() { });
            Object raw = root.get(PAIRED_SKELETON_KEY);
            if (!(raw instanceof Map<?, ?> meta)) return Optional.empty();
            Object sourceContextRaw = meta.get("sourceContext");
            Map<String, Object> sourceContext = null;
            if (sourceContextRaw instanceof Map<?, ?> sc) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : sc.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue());
                sourceContext = out;
            }
            Boolean reconstructMode = meta.get("reconstructMode") instanceof Boolean b ? b : null;
            Long sourceExampleId = meta.get("sourceExampleId") instanceof Number n ? n.longValue() : null;
            Boolean bSideViable = meta.get("bSideViable") instanceof Boolean b2 ? b2 : null;
            return Optional.of(new PairedSkeleton(sourceContext, reconstructMode, sourceExampleId, bSideViable));
        } catch (Exception e) {
            log.debug("[PartnerAnswer] paired skeleton load failed (non-critical) scheduledPostId={}: {}",
                    scheduledPostId, e.getMessage());
            return Optional.empty();
        }
    }
}
