package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.CreatePostDto;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.StoryProfile;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.safety.PlazaTopicalFitGate;
import com.againspring.aiuser.orchestrator.safety.ProofreadQualityGate;
import com.againspring.aiuser.orchestrator.safety.SoftProofread;
import com.againspring.aiuser.orchestrator.service.persona.PersonaLottery;
import com.againspring.aiuser.orchestrator.service.storyprofile.StoryProfileAnalyzer;
import com.againspring.aiuser.orchestrator.service.GenerationConfigSupport;
import com.againspring.aiuser.orchestrator.util.LiteralNewlineNormalizer;
import com.againspring.aiuser.orchestrator.notification.StructuredGenerationFailureTelegramNotifier;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PLAN-mode AI post creation boundary.
 *
 * <p>Default path (micro-batch ON): author+post in call 1 with the first 4~6 comment
 * personas. Follow-up HUMAN_POST runs only while items are below READY min
 * ({@code ready-min-items}, default 6) and only against a capped commenter prefix
 * (one extra slice). Mega-call remains when
 * {@code ai-user.thread-plan.micro-batch-enabled=false}.</p>
 *
 * <p>Source-story grounding (popular crawl claim via {@link PlanSourceStoryResolver},
 * author voice, reconstruct mode, anti-self-copy recent bodies, and
 * {@link StoryTwinGuard}) are injected here — not empty topicHint alone.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPostBundleService {
    /** Embedded in candidates_json until W1-H adds a dedicated column on ai_scheduled_posts. */
    public static final String SOURCE_PROVENANCE_KEY = "_sourceProvenance";

    private final AiUserGenerationConfigRepository configRepository;
    private final OrchestratorProperties properties;
    private final PersonaRepository personaRepository;
    private final LlmAiUserClient llmClient;
    private final BackendBotClient backendBot;
    private final ContentSafetyGuard safetyGuard;
    private final ThreadPlanService planService;
    private final ThreadPlanGenerationService planGenerationService;
    private final AiScheduledPostRepository scheduledPostRepository;
    private final CandidateScheduleSupport candidateScheduleSupport;
    private final ObjectMapper objectMapper;
    private final PlanPersonaMapper planPersonaMapper;
    private final PlanSourceStoryResolver sourceStoryResolver;
    private final StoryProfileAnalyzer storyProfileAnalyzer;
    private final PersonaLottery personaLottery;
    private final StoryTwinGuard storyTwinGuard;
    private final SourceReservationSupport sourceReservationSupport;
    private final GenerationConfigSupport generationConfigSupport;
    private final JdbcTemplate jdbcTemplate;
    private final com.againspring.aiuser.orchestrator.service.llm.LlmCircuitBreaker circuitBreaker;
    private final StructuredGenerationFailureTelegramNotifier structuredGenNotifier;
    private final PlazaTopicalFitGate plazaTopicalFitGate;
    private final com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache promptTemplateCache;

    /** Derive environment (dev/prod) from backend base URL for alerting. */
    private String deriveEnvironment() {
        String url = properties.getBackendBaseUrl();
        if (url != null) {
            if (url.contains("prod")) return "prod";
            if (url.contains("dev")) return "dev";
        }
        return "unknown";
    }

    /** 최근 과다 사용된 메타포 top-10 (post_metaphors 집계) — LLM 프롬프트에 다양성 힌트로 전달. */
    private List<String> fetchOverusedMetaphorIds() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT metaphor_id FROM post_metaphors GROUP BY metaphor_id "
                            + "ORDER BY COUNT(*) DESC LIMIT 10",
                    String.class);
        } catch (Exception e) {
            log.warn("fetchOverusedMetaphorIds failed — proceeding without variety hint: {}", e.getMessage());
            return List.of();
        }
    }

    /** A PLAN rollout owns post generation even when its workload provider is OFF. */
    public boolean ownsPostGeneration() {
        return properties.isEnabled() && properties.getThreadPlan().isEnabled()
                && configRepository.findById(1)
                .map(c -> !c.isAiUserKillSwitch())
                .orElse(false);
    }

    public Optional<PublishedBundle> generateAndPublish(Persona author, String jwt, String category,
                                                         String topicHint, String correlationId) {
        return generateAndPublish(author, jwt, category, topicHint, correlationId, null);
    }

    /**
     * Claim popular source → LLM → backend publish. Commits on createPost OK;
     * releases on empty claim, LLM/safety failure, or backend write failure.
     */
    public Optional<PublishedBundle> generateAndPublish(Persona author, String jwt, String category,
                                                         String topicHint, String correlationId,
                                                         String preferredSource) {
        String reservationKey = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();
        Instant reserveUntil = Instant.now().plus(Duration.ofHours(24));
        String sourceName = SourceReservationSupport.resolvePreferredSource(preferredSource, author);

        Optional<PlanSourceStoryResolver.ResolvedSource> claimed = sourceStoryResolver.claimAndResolve(
                author, sourceName, reservationKey, reserveUntil, category);
        if (claimed.isEmpty()) {
            log.info("AI post publish skipped: no claimed source preferred={} corr={}",
                    sourceName, correlationId);
            return Optional.empty();
        }
        Long exampleId = claimed.get().sourceExampleId();

        BundleAttempt generated = generateBundleWithSource(author, category, correlationId, claimed.get());
        if (!generated.ok()) {
            if (generated.llmInvoked()) {
                alertStructuredGenerationBundleLost(correlationId, generated.detail());
            }
            sourceReservationSupport.release(exampleId, reservationKey);
            return Optional.empty();
        }
        Bundle bundle = generated.bundle();

        CreatePostDto.CreatePostDtoBuilder postBuilder = CreatePostDto.builder()
                .userTitle(bundle.content.title()).bodyRaw(bundle.content.body()).category(category)
                .visibility("PUBLIC")
                .captureSplitAfterLines(bundle.content.captureSplitAfterLines())
                .captureSplitAfterLine(
                        bundle.content.captureSplitAfterLines() != null
                                && !bundle.content.captureSplitAfterLines().isEmpty()
                                ? bundle.content.captureSplitAfterLines().get(0) : null)
                .promoTitle(bundle.content.promoTitle())
                .hookEmotion(bundle.content.hookEmotion())
                .metaphorId(bundle.content.metaphorId())
                .metaphorIds(bundle.content.metaphorIds());
        applyProvenance(postBuilder, bundle.source);
        Optional<PostDto> published = backendBot.createPost(jwt, postBuilder.build());
        if (published.isEmpty() || published.get().getId() == null) {
            sourceReservationSupport.release(exampleId, reservationKey);
            return Optional.empty();
        }

        // Post is live — hard-commit even if plan persistence fails below.
        sourceReservationSupport.commit(exampleId, reservationKey);

        PostDto post = published.get();
        try {
            // POST_PUBLISHED outbox delivery will find this revision and must not request another LLM plan.
            AiThreadPlan plan = planService.reservePreGeneratedBundle(post.getId(), 1, Instant.now(),
                    bundle.content.title(), bundle.content.body(), category, bundle.provider, bundle.model);
            // Quality gate + READY policy (shared with ScheduledPostPublisher replay).
            planGenerationService.persistAndFinalize(plan.getId(), bundle.response, bundle.castIds);
            return Optional.of(new PublishedBundle(post, bundle.content.body(),
                    bundle.source != null ? bundle.source.sourceExampleId() : null));
        } catch (RuntimeException persistenceFailure) {
            // The post was accepted by backend, but it must never cause a second content call.
            log.error("Published AI post {} but could not persist its pre-generated bundle corr={}",
                    post.getId(), correlationId, persistenceFailure);
            return Optional.empty();
        }
    }

    /**
     * Same one-shot structured generation as {@link #generateAndPublish}, but the post is never
     * sent to backend — it is held in {@code ai_scheduled_posts} until {@code scheduledPublishAt}.
     */
    public Optional<AiScheduledPost> generateAndHold(Persona author, String category, String topicHint,
                                                      String correlationId, Instant scheduledPublishAt) {
        return generateAndHoldResult(author, category, topicHint, correlationId, scheduledPublishAt, null, Set.of())
                .saved();
    }

    /**
     * Soft-reserve under the scheduled-post id ({@code reserveUntil = scheduledPublishAt + 24h}),
     * generate, and hold. Empty claim → skip (no freestyle). LLM/safety/serialize failure → release.
     *
     * @param preferredSource {@code "blind"}|{@code "natepan"} or null (derive from voice_type)
     */
    public Optional<AiScheduledPost> generateAndHold(Persona author, String category, String topicHint,
                                                      String correlationId, Instant scheduledPublishAt,
                                                      String preferredSource) {
        return generateAndHoldResult(author, category, topicHint, correlationId, scheduledPublishAt,
                preferredSource, Set.of()).saved();
    }

    /** @see #generateAndHoldResult(Persona, String, String, String, Instant, String, Set) */
    public HoldResult generateAndHoldResult(Persona author, String category, String topicHint,
                                            String correlationId, Instant scheduledPublishAt,
                                            String preferredSource) {
        return generateAndHoldResult(author, category, topicHint, correlationId, scheduledPublishAt,
                preferredSource, Set.of());
    }

    /**
     * Distinguishes skip reasons for nightly fill retry. Same claimed {@code exampleId} in
     * {@code skipExampleIds} is released without a second LLM/safeguard pass.
     */
    public HoldResult generateAndHoldResult(Persona author, String category, String topicHint,
                                            String correlationId, Instant scheduledPublishAt,
                                            String preferredSource, Set<Long> skipExampleIds) {
        return generateAndHoldResult(author, category, topicHint, correlationId, scheduledPublishAt,
                preferredSource, skipExampleIds, false);
    }

    /**
     * {@code skipSourceClaim=true} bypasses the popular-source claim entirely and generates from a
     * freestyle source (no {@code sourceContext}/{@code sourceExampleId}) — <strong>dev canary
     * only</strong>, so the canary script does not need real crawled content in {@code example_bank}
     * to produce a test post.
     */
    public HoldResult generateAndHoldResult(Persona author, String category, String topicHint,
                                            String correlationId, Instant scheduledPublishAt,
                                            String preferredSource, Set<Long> skipExampleIds,
                                            boolean skipSourceClaim) {
        String personaId = author == null ? null : author.getId();
        String plaza = category;
        if (scheduledPublishAt == null) {
            log.warn("AI post hold skipped: scheduledPublishAt is null corr={}", correlationId);
            return HoldResult.generationSkipped(preferredSource, plaza, personaId, null,
                    "scheduledPublishAt is null");
        }
        String holdId = UUID.randomUUID().toString();
        Instant reserveUntil = scheduledPublishAt.plus(Duration.ofHours(24));
        String sourceName = SourceReservationSupport.resolvePreferredSource(preferredSource, author);

        Optional<PlanSourceStoryResolver.ResolvedSource> claimed = skipSourceClaim
                ? Optional.of(freestyleSource(sourceName))
                : sourceStoryResolver.claimAndResolve(
                        author, sourceName, holdId, reserveUntil, category, skipExampleIds);
        if (claimed.isEmpty()) {
            log.info("AI post hold skipped: no claimed source preferred={} plaza={} holdId={} corr={}",
                    sourceName, plaza, holdId, correlationId);
            return HoldResult.claimEmpty(sourceName, plaza, personaId,
                    "no claimed source (no freestyle)");
        }
        Long exampleId = claimed.get().sourceExampleId();
        if (exampleId != null && skipExampleIds != null && skipExampleIds.contains(exampleId)) {
            log.info("AI post hold skipped: same exampleId={} without LLM retry holdId={} corr={}",
                    exampleId, holdId, correlationId);
            sourceReservationSupport.release(exampleId, holdId);
            return HoldResult.sameExample(sourceName, plaza, personaId, exampleId,
                    "released duplicate example without LLM retry");
        }

        BundleAttempt attempt = generateBundleWithSource(author, category, correlationId, claimed.get());
        if (!attempt.ok()) {
            sourceReservationSupport.release(exampleId, holdId);
            if (attempt.llmInvoked()) {
                alertStructuredGenerationBundleLost(correlationId, attempt.detail());
                return HoldResult.llmOrSafety(sourceName, plaza, personaId, exampleId, attempt.detail());
            }
            return HoldResult.generationSkipped(sourceName, plaza, personaId, exampleId, attempt.detail());
        }
        Bundle bundle = attempt.bundle();

        candidateScheduleSupport.enrichMissingScheduledAts(bundle.response, scheduledPublishAt);

        // Soft-reserve refs for publisher commit / admin cancel·fail release.
        bundle.response.put(SOURCE_PROVENANCE_KEY,
                sourceReservationSupport.provenanceWithReservation(bundle.source, holdId));

        String candidatesJson;
        try {
            candidatesJson = objectMapper.writeValueAsString(bundle.response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException serializationFailure) {
            log.error("AI post bundle generated but candidates could not be serialized corr={}",
                    correlationId, serializationFailure);
            sourceReservationSupport.release(exampleId, holdId);
            return HoldResult.serialize(sourceName, plaza, personaId, exampleId,
                    serializationFailure.getMessage());
        }

        AiScheduledPost.AiScheduledPostBuilder rowBuilder = AiScheduledPost.builder()
                .id(holdId)
                .personaId(author.getId())
                .category(category)
                .title(bundle.content.title())
                .body(bundle.content.body())
                .candidatesJson(candidatesJson)
                .scheduledPublishAt(scheduledPublishAt)
                .status(ScheduledPostStatus.SCHEDULED)
                .provider(bundle.provider)
                .model(bundle.model);
        // W1-H may add sourceExampleId on the entity — set when present without owning the migration.
        applyScheduledSourceHook(rowBuilder, bundle.source);
        try {
            AiScheduledPost saved = scheduledPostRepository.save(rowBuilder.build());
            return HoldResult.saved(saved, sourceName, plaza, personaId, exampleId);
        } catch (RuntimeException persistFailure) {
            log.error("AI post hold persist failed holdId={} corr={}", holdId, correlationId, persistFailure);
            sourceReservationSupport.release(exampleId, holdId);
            return HoldResult.persist(sourceName, plaza, personaId, exampleId, persistFailure.getMessage());
        }
    }

    /**
     * Synthetic {@link PlanSourceStoryResolver.ResolvedSource} for {@code skipSourceClaim=true}:
     * no popular-crawl grounding ({@code sourceContext}/{@code sourceExampleId}/{@code sourceBody}
     * all null, {@code reconstructMode=false}) — dev canary only, never a real claim.
     */
    private static PlanSourceStoryResolver.ResolvedSource freestyleSource(String sourceName) {
        return new PlanSourceStoryResolver.ResolvedSource(
                "", null, false, null, null, sourceName, null, null, "", List.of());
    }

    /**
     * Shared generation step given an already-claimed source. Callers own claim / release / commit.
     */
    private BundleAttempt generateBundleWithSource(
            Persona author, String category, String correlationId,
            PlanSourceStoryResolver.ResolvedSource source) {
        if (author == null) {
            log.warn("AI post bundle skipped: author is null corr={}", correlationId);
            return BundleAttempt.skipped("author is null");
        }
        if (source == null) {
            log.warn("AI post bundle skipped: source is null corr={}", correlationId);
            return BundleAttempt.skipped("source is null");
        }
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        String provider = config == null ? properties.getThreadPlan().getAiPostProvider()
                : config.getProviderAiPostBundle();
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) {
            log.info("AI post bundle skipped: provider is OFF corr={}", correlationId);
            return BundleAttempt.skipped("provider is OFF");
        }
        String model = properties.getThreadPlan().getAiPostModel();
        int pool = config == null ? 24 : Math.max(8, Math.min(30, config.getCandidatePoolSize()));

        // WP3: StoryProfile once per source (grounding hints stay useful even without the matcher).
        StoryProfile storyProfile = storyProfileAnalyzer.analyze(
                source.sourceTitle(),
                source.sourceBody(),
                category,
                registerHint(author, source),
                source.sourceExampleId());
        String corr = correlationId == null ? "ai-post-bundle" : correlationId;

        // WP3 계약 6: 캐스트 = [작성자] + 가중 비복원 추첨 댓글자 11명. 매 글 추첨이므로
        // 상위 슬라이스가 고정되지 않는다(reorderCastByMatcher/캡슐 폐기).
        List<Persona> active = personaRepository.findByActiveTrue();
        List<Persona> drawnCommenters = personaLottery.drawCommenters(
                active, category, Set.of(author.getId()), 11, ThreadLocalRandom.current());
        List<Map<String, Object>> personas = new ArrayList<>(1 + drawnCommenters.size());
        personas.add(planPersonaMapper.mapAuthor(author));
        personas.addAll(planPersonaMapper.mapCast(drawnCommenters));
        Set<String> castIds = planPersonaMapper.castIds(personas);

        OrchestratorProperties.ThreadPlan tp = properties.getThreadPlan();
        BundleAttempt attempt;
        if (tp.isMicroBatchEnabled()) {
            attempt = generateBundleMicroBatch(
                    author, category, correlationId, corr,
                    provider, model, pool, source, storyProfile, personas, castIds);
        } else {
            attempt = generateBundleMegaCall(
                    author, category, correlationId, provider, model, pool, source, storyProfile, personas, castIds);
        }
        if (!attempt.ok()) return attempt;
        Bundle bundle = attempt.bundle();
        int stripped = StoryPersonaCommentFilter.stripFromResponse(bundle.response(), Set.of(author.getId()));
        if (stripped > 0) {
            log.info("AI post bundle stripped {} story-persona comment(s) corr={}", stripped, correlationId);
        }
        // Evaluate plaza topical fit (log-only, no blocking yet)
        evaluatePlazaTopicalFit(category, bundle.content().title(), bundle.content().body(), correlationId);

        return proofreadBundle(bundle, correlationId)
                .map(BundleAttempt::ok)
                .orElseGet(() -> BundleAttempt.llmFail("proofread dropped bundle"));
    }

    /**
     * Soft spelling pass: LLM only when {@link SoftProofread#needsLlm} matches.
     * Call/structure/safety failure keeps the generated body (fail-open). Nightly logs
     * showed fail-closed proofread discarding holds on {@code PROOFREAD_STRUCTURE_CHANGED}
     * (newline drift) and 504 — not source length or punctuation.
     * {@code bundle.response()} stays the raw LLM payload for audit.
     */
    private Optional<Bundle> proofreadBundle(Bundle bundle, String correlationId) {
        String original = bundle.content().body();
        if (!SoftProofread.needsLlm(original)) {
            log.debug("[LLMSTATS] type=PROOFREAD action=skipped_not_needed corr={}", correlationId);
            return Optional.of(bundle);
        }
        Optional<String> proofreadOpt = llmClient.proofreadPost(original, correlationId);
        if (proofreadOpt.isEmpty()) {
            log.warn("AI post bundle proofread call failed corr={} — keeping original", correlationId);
        } else {
            ProofreadQualityGate.Result quality = ProofreadQualityGate.validate(original, proofreadOpt.get());
            if (!quality.passed()) {
                log.warn("AI post bundle proofread quality rejected corr={} {} — keeping original",
                        correlationId, quality.reason());
            }
            ContentSafetyGuard.GuardResult guard =
                    safetyGuard.check(proofreadOpt.get(), ContentSafetyGuard.ContentType.POST);
            if (!guard.passed()) {
                log.warn("AI post bundle proofread output blocked corr={} {} — keeping original",
                        correlationId, guard.reason());
            }
        }
        boolean safetyOk = proofreadOpt
                .map(text -> safetyGuard.check(text, ContentSafetyGuard.ContentType.POST).passed())
                .orElse(false);
        String body = SoftProofread.resolve(original, proofreadOpt, safetyOk);
        if (body.equals(original)) {
            return Optional.of(bundle);
        }
        PostContent c = bundle.content();
        PostContent updated = new PostContent(c.title(), body, c.captureSplitAfterLines(),
            c.promoTitle(), c.hookEmotion(), c.metaphorId(), c.metaphorIds());
        return Optional.of(new Bundle(bundle.response(), updated, bundle.provider(), bundle.model(),
            bundle.castIds(), bundle.source()));
    }

    /** Legacy single-call path: full cast in one AI_POST request. */
    private BundleAttempt generateBundleMegaCall(
            Persona author, String category, String correlationId,
            String provider, String model, int pool,
            PlanSourceStoryResolver.ResolvedSource source, StoryProfile storyProfile,
            List<Map<String, Object>> personas, Set<String> castIds) {
        Map<String, Object> request = baseAiPostRequest(
                author, category, correlationId, provider, model, source, storyProfile);
        // Same 2026-08-01 outage fix as ThreadPlanGenerationService: cap the cast so this
        // single-call fallback can't blow past Claude's token budget if micro-batch is ever
        // disabled. Index 0 stays put — reorderCastByMatcher already placed the matched author
        // there. Sending fewer candidates never invalidates validateCast(), which only checks
        // returned IDs are a subset of the original castIds.
        request.put("personas", capMegaCallCast(personas, properties.getThreadPlan().getPlanPersonaCastMax()));
        int roots = Math.min(14, pool);
        request.put("maxTopLevel", roots);
        request.put("maxReplies", pool - roots);
        putParsePlanFloors(request);

        // Check circuit breaker before generation
        if (circuitBreaker.isOpen()) {
            log.error("[CIRCUIT] OPEN skipped=THREAD_PLAN corrId={} reason={}", correlationId, circuitBreaker.getTelemetry().getReason());
            return BundleAttempt.llmFail("Circuit breaker OPEN");
        }

        Optional<Map<String, Object>> response = llmClient.generateThreadPlan(request);
        if (response.isEmpty()) {
            log.warn("[CIRCUIT] recordFailure type=THREAD_PLAN retryReason=GEN_FAILED corrId={}", correlationId);
            circuitBreaker.recordFailure("GEN_FAILED", null);
            return BundleAttempt.llmFail("LLM empty response");
        }
        try {
            PostContent postContent = readAndValidatePost(response.get());
            rejectIfStoryTwin(postContent, correlationId);
            validateCast(response.get(), castIds);
            circuitBreaker.recordSuccess();
            return BundleAttempt.ok(new Bundle(response.get(), postContent, provider, model, castIds, source));
        } catch (IllegalArgumentException invalid) {
            log.warn("AI post bundle rejected corr={}: {}", correlationId, invalid.getMessage());
            log.warn("[CIRCUIT] recordFailure type=THREAD_PLAN retryReason=VALIDATION_FAIL corrId={}", correlationId);
            circuitBreaker.recordFailure("VALIDATION_FAIL", null);
            return BundleAttempt.llmFail(invalid.getMessage());
        }
    }

    /**
     * Micro-batch path (§7.7 / WP4): call 1 = AI_POST with author + first slice;
     * calls 2..N = HUMAN_POST comment-only with existingTitle/Body from call 1.
     * All batches finish inside this generation job.
     */
    @SuppressWarnings("unchecked")
    private BundleAttempt generateBundleMicroBatch(
            Persona author, String category, String correlationId, String corr,
            String provider, String model, int pool,
            PlanSourceStoryResolver.ResolvedSource source, StoryProfile storyProfile,
            List<Map<String, Object>> personas, Set<String> castIds) {
        int batchSize = properties.getThreadPlan().resolvedMicroBatchSize();
        Map<String, Object> authorEntry = personas.get(0);
        List<Map<String, Object>> commenters = personas.size() <= 1
                ? List.of()
                : personas.subList(1, personas.size());
        int readyMinItems = Math.max(1, properties.getThreadPlan().getReadyMinItems());
        commenters = capCommentersForMicroBatch(commenters, batchSize, readyMinItems);

        List<List<Map<String, Object>>> slices = sliceCommenters(commenters, batchSize);
        if (slices.isEmpty()) {
            // Author-only cast: still need a post — one AI_POST with author alone.
            slices = List.of(List.of());
        }

        List<Map<String, Object>> firstSlice = slices.get(0);
        List<Map<String, Object>> firstPersonas = new ArrayList<>(1 + firstSlice.size());
        firstPersonas.add(authorEntry);
        firstPersonas.addAll(firstSlice);

        int remaining = pool;
        int firstTop = Math.max(1, Math.min(firstSlice.isEmpty() ? 1 : firstSlice.size(), remaining));
        int firstReplies = Math.max(0, Math.min(firstSlice.size(), remaining - firstTop));

        Map<String, Object> firstReq = baseAiPostRequest(
                author, category, corr + "-b0", provider, model, source, storyProfile);
        firstReq.put("personas", firstPersonas);
        firstReq.put("maxTopLevel", firstTop);
        firstReq.put("maxReplies", firstReplies);
        putParsePlanFloors(firstReq);

        Optional<Map<String, Object>> firstOpt = llmClient.generateThreadPlan(firstReq);
        if (firstOpt.isEmpty()) return BundleAttempt.llmFail("LLM empty response (micro-batch[0])");

        PostContent postContent;
        Map<String, Object> merged;
        try {
            postContent = readAndValidatePost(firstOpt.get());
            rejectIfStoryTwin(postContent, correlationId);
            validateCast(firstOpt.get(), castIds);
            merged = new LinkedHashMap<>(firstOpt.get());
        } catch (IllegalArgumentException invalid) {
            log.warn("AI post micro-batch[0] rejected corr={}: {}", correlationId, invalid.getMessage());
            return BundleAttempt.llmFail("micro-batch[0]: " + invalid.getMessage());
        }

        List<Map<String, Object>> mergedItems = new ArrayList<>();
        Set<String> usedRefs = new LinkedHashSet<>();
        appendRemappedItems(mergedItems, usedRefs, firstOpt.get(), "b0", remaining);
        remaining = pool - mergedItems.size();

        int followUps = 0;
        for (int i = 1; i < slices.size() && remaining > 0; i++) {
            if (mergedItems.size() >= readyMinItems) {
                break;
            }
            List<Map<String, Object>> slice = slices.get(i);
            if (slice.isEmpty()) continue;
            int top = Math.max(1, Math.min(slice.size(), remaining));
            int replies = Math.max(0, Math.min(slice.size(), remaining - top));

            Map<String, Object> follow = new LinkedHashMap<>();
            follow.put("kind", "HUMAN_POST");
            String humanProvider = properties.getThreadPlan().getHumanPlanProvider();
            follow.put("provider", (humanProvider != null && !humanProvider.isBlank()) ? humanProvider : provider);
            String humanModel = properties.getThreadPlan().getHumanPlanModel();
            if (humanModel != null && !humanModel.isBlank()) follow.put("model", humanModel);
            follow.put("correlationId", corr + "-b" + i);
            follow.put("timeoutMs", generationConfigSupport.bundleTimeoutMs());
            follow.put("category", category == null ? "OTHER" : category);
            follow.put("topicHint", source.topicSeed());
            follow.put("existingTitle", postContent.title());
            follow.put("existingBody", postContent.body());
            follow.put("personas", slice);
            follow.put("maxTopLevel", top);
            follow.put("maxReplies", replies);
            putParsePlanFloors(follow);

            Optional<Map<String, Object>> followOpt = llmClient.generateThreadPlan(follow);
            if (followOpt.isEmpty()) {
                log.warn("AI post micro-batch[{}] empty corr={} — keeping {} items so far",
                        i, correlationId, mergedItems.size());
                break;
            }
            try {
                validateCast(followOpt.get(), castIds);
            } catch (IllegalArgumentException invalid) {
                log.warn("AI post micro-batch[{}] cast rejected corr={}: {} — keeping {} items",
                        i, correlationId, invalid.getMessage(), mergedItems.size());
                break;
            }
            appendRemappedItems(mergedItems, usedRefs, followOpt.get(), "b" + i, remaining);
            remaining = pool - mergedItems.size();
            followUps++;
        }

        merged.put("items", mergedItems);
        // Drop follow-up post payloads if any slipped through; keep call-1 post only.
        Map<String, Object> postMap = new LinkedHashMap<>();
        postMap.put("title", postContent.title());
        postMap.put("body", postContent.body());
        postMap.put("promo_title", postContent.promoTitle());
        postMap.put("hook_emotion", postContent.hookEmotion());
        postMap.put("metaphor_id", postContent.metaphorId());
        postMap.put("capture_split_after_lines", postContent.captureSplitAfterLines());
        if (postContent.captureSplitAfterLines() != null && !postContent.captureSplitAfterLines().isEmpty()) {
            postMap.put("capture_split_after_line", postContent.captureSplitAfterLines().get(0));
        }
        postMap.put("metaphor_ids", postContent.metaphorIds());
        merged.put("post", postMap);
        log.info("AI post micro-batch done corr={} llmCalls={} followUps={} plannedSlices={} items={}/{} size={}",
                correlationId, 1 + followUps, followUps, slices.size(), mergedItems.size(), pool,
                properties.getThreadPlan().resolvedMicroBatchSize());
        return BundleAttempt.ok(new Bundle(merged, postContent, provider, model, castIds, source));
    }

    private Map<String, Object> baseAiPostRequest(
            Persona author, String category, String correlationId,
            String provider, String model,
            PlanSourceStoryResolver.ResolvedSource source, StoryProfile storyProfile) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("kind", "AI_POST");
        request.put("provider", provider);
        if (model != null && !model.isBlank()) request.put("model", model);
        request.put("promptOverrides", promptTemplateCache.overrides());
        request.put("correlationId", correlationId);
        request.put("timeoutMs", generationConfigSupport.bundleTimeoutMs());
        request.put("category", category == null ? "OTHER" : category);
        request.put("topicHint", source.topicSeed());
        request.put("sourceContext", source.sourceContext());
        request.put("reconstructMode", source.reconstructMode());
        if (source.sourceExampleId() != null) request.put("sourceExampleId", source.sourceExampleId());
        // persona-diversity-v4 WP2 item3: sourceBody(원문 전문) 전달 제거 — sourceContext가
        // 이제 골격(뼈대) JSON만 담으므로 원문을 별도 필드로 다시 실을 필요가 없다.
        if (source.dynamicExamples() != null && !source.dynamicExamples().isBlank()) {
            request.put("dynamicExamples", source.dynamicExamples());
        }
        List<String> recent = PlanSourceStoryResolver.recentOutputsForRequest(source.recentBodies(), 200);
        if (!recent.isEmpty()) request.put("recentOutputs", recent);
        List<String> overusedMetaphors = fetchOverusedMetaphorIds();
        if (!overusedMetaphors.isEmpty()) request.put("overusedMetaphorIds", overusedMetaphors);
        request.put("storyProfile", storyProfileToMap(storyProfile));
        request.put("storySearchDoc", storyProfile.toSearchDocument());
        request.put("author", planPersonaMapper.mapAuthor(author));
        return request;
    }

    /** W5-A floors: sparse batches OK; quality gate drops later. */
    static void putParsePlanFloors(Map<String, Object> request) {
        request.put("minTopLevel", 1);
        request.put("minItems", 1);
    }

    /**
     * Commenters already come from {@code PersonaLottery.drawCommenters} (random weighted order,
     * not matcher-ranked). Keep enough for call-1 plus at most one extra slice so READY mins can
     * be filled — do not walk the full active roster (~150 → 30 slices).
     */
    static List<Map<String, Object>> capCommentersForMicroBatch(
            List<Map<String, Object>> commenters, int batchSize, int readyMinItems) {
        if (commenters == null || commenters.isEmpty()) {
            return List.of();
        }
        int size = Math.max(4, Math.min(6, batchSize <= 0 ? 5 : batchSize));
        int floor = Math.max(1, readyMinItems);
        int budget = Math.min(commenters.size(), Math.max(size, floor) + size);
        return List.copyOf(commenters.subList(0, budget));
    }

    static List<List<Map<String, Object>>> sliceCommenters(List<Map<String, Object>> commenters, int batchSize) {
        if (commenters == null || commenters.isEmpty()) return List.of();
        int size = Math.max(4, Math.min(6, batchSize <= 0 ? 5 : batchSize));
        List<List<Map<String, Object>>> out = new ArrayList<>();
        for (int i = 0; i < commenters.size(); i += size) {
            out.add(List.copyOf(commenters.subList(i, Math.min(i + size, commenters.size()))));
        }
        return out;
    }

    /**
     * Remap refs with a batch prefix so merged trees never collide; drop items past {@code remainingCap}.
     * Top-level first, then replies whose parentRef remaps within the same batch.
     */
    static void appendRemappedItems(
            List<Map<String, Object>> dest, Set<String> usedRefs,
            Map<String, Object> response, String batchPrefix, int remainingCap) {
        if (remainingCap <= 0) return;
        Object rawItems = response.get("items");
        if (!(rawItems instanceof List<?> rows) || rows.isEmpty()) return;

        Map<String, String> refMap = new LinkedHashMap<>();
        List<Map<String, Object>> staged = new ArrayList<>();

        for (Object raw : rows) {
            if (!(raw instanceof Map<?, ?> row)) continue;
            Object parent = row.get("parentRef");
            if (parent != null && !String.valueOf(parent).isBlank()) continue;
            String ref = textStatic(row.get("ref"));
            if (ref.isBlank()) continue;
            String mapped = uniqueRef(batchPrefix, ref, usedRefs);
            usedRefs.add(mapped);
            refMap.put(ref, mapped);
            Map<String, Object> copy = copyItemRow(row);
            copy.put("ref", mapped);
            copy.put("parentRef", null);
            staged.add(copy);
        }
        for (Object raw : rows) {
            if (!(raw instanceof Map<?, ?> row)) continue;
            Object parent = row.get("parentRef");
            if (parent == null || String.valueOf(parent).isBlank()) continue;
            String mappedParent = refMap.get(String.valueOf(parent).trim());
            if (mappedParent == null) continue;
            String ref = textStatic(row.get("ref"));
            if (ref.isBlank()) continue;
            String mapped = uniqueRef(batchPrefix, ref, usedRefs);
            usedRefs.add(mapped);
            refMap.put(ref, mapped);
            Map<String, Object> copy = copyItemRow(row);
            copy.put("ref", mapped);
            copy.put("parentRef", mappedParent);
            staged.add(copy);
        }

        int left = remainingCap;
        for (Map<String, Object> item : staged) {
            if (left <= 0) break;
            dest.add(item);
            left--;
        }
    }

    static Map<String, Object> copyItemRow(Map<?, ?> row) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : row.entrySet()) {
            copy.put(String.valueOf(e.getKey()), e.getValue());
        }
        return copy;
    }

    static String uniqueRef(String batchPrefix, String original, Set<String> used) {
        String base = (batchPrefix + "_" + original).replaceAll("[^A-Za-z0-9_-]", "_");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) base = "r" + base;
        if (base.length() > 64) base = base.substring(0, 64);
        String candidate = base;
        int n = 2;
        while (used.contains(candidate)) {
            String suffix = "_" + n++;
            candidate = base.substring(0, Math.min(base.length(), 64 - suffix.length())) + suffix;
        }
        return candidate;
    }

    private static String textStatic(Object value) {
        if (value == null) return "";
        return LiteralNewlineNormalizer.normalize(String.valueOf(value)).trim();
    }

    /**
     * Soft cast check at generation time. Out-of-cast items are dropped later by
     * {@link ThreadQualityGate} — do not reject the whole bundle here.
     */
    static int countOutOfCast(Map<String, Object> response, Set<String> castIds) {
        if (castIds == null || castIds.isEmpty()) return 0;
        Object rawItems = response.get("items");
        if (!(rawItems instanceof List<?> rows)) return 0;
        int bad = 0;
        for (Object raw : rows) {
            if (!(raw instanceof Map<?, ?> row)) continue;
            Object pid = row.get("personaId");
            if (pid == null) continue;
            String id = String.valueOf(pid).trim();
            if (!id.isEmpty() && !castIds.contains(id)) bad++;
        }
        return bad;
    }

    /** Soft alias — logs nothing; {@link ThreadQualityGate} enforces cast at persist. */
    static void validateCast(Map<String, Object> response, Set<String> castIds) {
        countOutOfCast(response, castIds);
    }

    /** Cap a single mega-call's persona payload, keeping index 0 (the reordered author) fixed. */
    static List<Map<String, Object>> capMegaCallCast(List<Map<String, Object>> personas, int max) {
        int effectiveMax = Math.max(1, max);
        if (personas == null || personas.size() <= effectiveMax) return personas;
        List<Map<String, Object>> rest = new ArrayList<>(personas.subList(1, personas.size()));
        java.util.Collections.shuffle(rest, java.util.concurrent.ThreadLocalRandom.current());
        List<Map<String, Object>> capped = new ArrayList<>(effectiveMax);
        capped.add(personas.get(0));
        capped.addAll(rest.subList(0, Math.min(effectiveMax - 1, rest.size())));
        return capped;
    }

    private static void applyProvenance(CreatePostDto.CreatePostDtoBuilder b,
                                        PlanSourceStoryResolver.ResolvedSource source) {
        if (source == null || !source.reconstructMode() || source.sourceExampleId() == null) return;
        b.sourceExampleId(source.sourceExampleId())
                .sourceCommunity(source.sourceCommunity())
                .sourceUrl(source.sourceUrl())
                .sourceOriginalTitle(source.sourceTitle())
                .sourceOriginalBody(PlanSourceStoryResolver.truncate(source.sourceBody(), 2000));
    }

    /**
     * Soft hook for W1-H: if AiScheduledPost gains {@code sourceExampleId}, populate it.
     * Until then provenance lives under {@link #SOURCE_PROVENANCE_KEY} in candidates JSON.
     */
    private static void applyScheduledSourceHook(AiScheduledPost.AiScheduledPostBuilder rowBuilder,
                                                 PlanSourceStoryResolver.ResolvedSource source) {
        if (source == null || source.sourceExampleId() == null) return;
        try {
            var method = AiScheduledPost.AiScheduledPostBuilder.class.getMethod("sourceExampleId", Long.class);
            method.invoke(rowBuilder, source.sourceExampleId());
        } catch (ReflectiveOperationException ignored) {
            // Column not yet present (W1-H). Provenance is in candidates JSON.
        }
    }

    static Map<String, Object> storyProfileToMap(StoryProfile p) {
        if (p == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centralConflict", p.centralConflict());
        m.put("category", p.category());
        m.put("topics", p.topics());
        m.put("explicitIdentity", p.explicitIdentity());
        m.put("lifeContext", p.lifeContext());
        m.put("valueAxis", p.valueAxis());
        m.put("sourceRegister", p.sourceRegister());
        m.put("replyAffordances", p.replyAffordances());
        m.put("searchDoc", p.toSearchDocument());
        return m;
    }

    /** Prefer example source community; else persona voice_type (NATEPAN|BLIND). */
    static String registerHint(Persona author, PlanSourceStoryResolver.ResolvedSource source) {
        if (source != null && source.sourceCommunity() != null && !source.sourceCommunity().isBlank()) {
            return source.sourceCommunity();
        }
        if (author != null && author.getVoiceProfile() != null) {
            Object vt = author.getVoiceProfile().get("voice_type");
            if (vt != null && !String.valueOf(vt).isBlank()) return String.valueOf(vt);
        }
        return "NATEPAN";
    }

    /**
     * Evaluate plaza topical fit for generated story (log-only, Phase 4).
     * When gate is configured for blocking (future), this would be a veto point.
     */
    private void evaluatePlazaTopicalFit(String category, String title, String body, String correlationId) {
        if (!properties.getThreadPlan().getPlazaTopicalFitGate().isLoggingEnabled()) {
            return;
        }
        try {
            PlazaTopicalFitGate.Result result = plazaTopicalFitGate.evaluate(category, title, body);
            if (!result.matches() && properties.getThreadPlan().getPlazaTopicalFitGate().isBlockingEnabled()) {
                log.warn("AI post bundle rejected: PLAZA_MISMATCH corr={} declaredPlaza={} inferredPlaza={}",
                    correlationId, result.declaredPlaza(), result.inferredPlaza());
                throw new IllegalArgumentException(
                    "plaza mismatch: declared=" + result.declaredPlaza() + " inferred=" + result.inferredPlaza());
            }
        } catch (IllegalArgumentException blocking) {
            throw blocking;
        } catch (Exception e) {
            log.warn("Plaza topical-fit evaluation failed corr={}: {}", correlationId, e.getMessage());
        }
    }

    /**
     * Alert on structured-generation bundle loss (hard failure: no post/comments produced).
     * Wrapped in try/catch so notification failure never breaks generation.
     */
    private void alertStructuredGenerationBundleLost(String correlationId, String failureDetail) {
        if (structuredGenNotifier == null || !properties.getThreadPlan().getStructuredGeneration().isFailureAlertsEnabled()) {
            return;
        }
        try {
            String env = deriveEnvironment();
            // Try to extract a snippet from the failure detail (truncate to ~200 chars)
            String snippet = failureDetail != null && failureDetail.length() > 200
                    ? failureDetail.substring(0, 200)
                    : failureDetail;
            structuredGenNotifier.bundleLost(correlationId, failureDetail, env, snippet);
        } catch (Exception e) {
            log.warn("Failed to alert bundle-lost via Telegram: {}", e.getMessage());
        }
    }

    private record BundleAttempt(Bundle bundle, String detail, boolean llmInvoked) {
        static BundleAttempt ok(Bundle bundle) {
            return new BundleAttempt(bundle, "", true);
        }

        static BundleAttempt llmFail(String detail) {
            return new BundleAttempt(null, detail == null ? "LLM or safety rejected" : detail, true);
        }

        static BundleAttempt skipped(String detail) {
            return new BundleAttempt(null, detail == null ? "generation skipped" : detail, false);
        }

        boolean ok() {
            return bundle != null;
        }
    }

    private record Bundle(Map<String, Object> response, PostContent content, String provider, String model,
                          Set<String> castIds, PlanSourceStoryResolver.ResolvedSource source) { }

    /**
     * After LLM returns title/body: reject obvious twins of recent published AI posts.
     * Throws so mega-call / micro-batch paths return empty and skip hold
     * (soft-reserve release is owned by the lifecycle path).
     */
    private void rejectIfStoryTwin(PostContent content, String correlationId) {
        if (content == null || storyTwinGuard == null) return;
        Optional<String> reason = storyTwinGuard.twinReason(
                content.title(), content.body(), storyTwinGuard.loadRecentAiPosts());
        if (reason.isPresent()) {
            log.warn("AI post rejected as story twin corr={} reason={}", correlationId, reason.get());
            throw new IllegalArgumentException("story twin of recent AI post: " + reason.get());
        }
    }

    @SuppressWarnings("unchecked")
    private PostContent readAndValidatePost(Map<String, Object> response) {
        if (!(response.get("post") instanceof Map<?, ?> raw)) throw new IllegalArgumentException("missing post");
        String title = text(raw.get("title"));
        String body = text(raw.get("body"));
        if (title.isBlank() || title.length() > 40 || body.isBlank()) throw new IllegalArgumentException("invalid post fields");
        String titleNorm = title.replaceAll("\\s+", " ").trim();
        String bodyNorm = body.replaceAll("\\s+", " ").trim();
        if (titleNorm.equals(bodyNorm)) throw new IllegalArgumentException("title must differ from body");
        ContentSafetyGuard.GuardResult guard = safetyGuard.check(body, ContentSafetyGuard.ContentType.POST);
        if (!guard.passed()) throw new IllegalArgumentException("unsafe post: " + guard.reason());
        List<Integer> splits = resolveCaptureSplits(body, readCaptureSplits(raw));
        String promoTitle = readAndNormalizePromoTitle(title, raw);
        String hookEmotion = readAndNormalizeHookEmotion(raw);
        String metaphorId = readMetaphorId(raw);
        List<String> metaphorIds = readMetaphorIds(raw);
        return new PostContent(title, body, splits, promoTitle, hookEmotion, metaphorId, metaphorIds);
    }

    private static String readMetaphorId(Map<?, ?> post) {
        Object v = post.get("metaphor_id");
        if (v == null) v = post.get("metaphorId");
        if (v == null) return null;
        String id = String.valueOf(v).trim().toLowerCase(java.util.Locale.ROOT);
        return id.isBlank() ? null : id;
    }

    private static List<String> readMetaphorIds(Map<?, ?> post) {
        Object v = post.get("metaphor_ids");
        if (v == null) v = post.get("metaphorIds");
        if (v == null || !(v instanceof List<?>)) return null;
        List<?> raw = (List<?>) v;
        if (raw.isEmpty()) return null;
        List<String> ids = new ArrayList<>();
        for (Object o : raw) {
            if (o != null) {
                String id = String.valueOf(o).trim().toLowerCase(java.util.Locale.ROOT);
                if (!id.isBlank()) ids.add(id);
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    private static String readAndNormalizePromoTitle(String title, Map<?, ?> post) {
        Object v = post.get("promo_title");
        if (v == null) v = post.get("promoTitle");
        String proposed = v == null ? null : String.valueOf(v);
        if (proposed != null) {
            proposed = proposed.replace("\\n", "\n").trim();
        }
        // Master SNS hook — independent of plaza title. Length/safety only.
        if (proposed != null && !proposed.isBlank()) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            boolean ok = true;
            for (String line : proposed.replace("\r\n", "\n").split("\n", -1)) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                if (t.length() > 20) { ok = false; break; }
                lines.add(t);
            }
            if (ok && !lines.isEmpty()) {
                String joined = String.join("\n", lines);
                String flat = joined.replace("\n", "").replaceAll("\\s+", "");
                if (flat.length() >= 4 && flat.length() <= 80) {
                    return joined;
                }
            }
        }
        return packPromoLines(title);
    }

    private static final Set<String> HOOK_EMOTIONS = Set.of("shock", "anger", "tension", "sad", "hype");

    private static String readAndNormalizeHookEmotion(Map<?, ?> post) {
        Object v = post.get("hook_emotion");
        if (v == null) v = post.get("hookEmotion");
        if (v == null) return "tension";
        String emotion = String.valueOf(v).trim().toLowerCase(java.util.Locale.ROOT);
        return HOOK_EMOTIONS.contains(emotion) ? emotion : "tension";
    }

    /** Pack eojeol into ≤10 char lines; avoid 1-char orphans. */
    static String packPromoLines(String title) {
        if (title == null || title.isBlank()) return title;
        String t = title.trim();
        if (t.length() <= 10) return t;
        String[] parts = t.split("\\s+");
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (cur.length() == 0) {
                cur.append(part.length() <= 10 ? part : part.substring(0, 10));
                if (part.length() > 10) {
                    lines.add(cur.toString());
                    cur.setLength(0);
                    for (int i = 10; i < part.length(); i += 10) {
                        lines.add(part.substring(i, Math.min(i + 10, part.length())));
                    }
                }
                continue;
            }
            String cand = cur + " " + part;
            if (cand.length() <= 10) {
                cur.setLength(0);
                cur.append(cand);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(part.length() <= 10 ? part : part.substring(0, Math.min(10, part.length())));
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return String.join("\n", lines);
    }

    private static List<Integer> readCaptureSplits(Map<?, ?> post) {
        Object v = post.get("capture_split_after_lines");
        if (v == null) v = post.get("captureSplitAfterLines");
        if (v instanceof List<?> list && !list.isEmpty()) {
            List<Integer> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Number n) out.add(n.intValue());
            }
            if (!out.isEmpty()) return out;
        }
        Integer legacy = readCaptureSplit(post);
        return legacy == null ? null : List.of(legacy);
    }

    private static Integer readCaptureSplit(Map<?, ?> post) {
        Object v = post.get("capture_split_after_line");
        if (v == null) v = post.get("captureSplitAfterLine");
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    /**
     * Prefer a valid LLM multi-cut; else heuristic when body has more than 8 non-empty newline blocks.
     * Mirrors backend {@code CaptureSplitSupport} (SHORT_POST_MAX_BLOCKS=8, max 4 parts).
     */
    static List<Integer> resolveCaptureSplits(String body, List<Integer> proposed) {
        int n = countNonEmptyBlocks(body);
        if (n <= 8) return null;
        if (proposed != null && !proposed.isEmpty()) {
            List<Integer> cuts = new ArrayList<>();
            int prev = 0;
            boolean ok = true;
            for (Integer p : proposed) {
                if (p == null || p <= prev || p >= n || (p - prev) > 8 || cuts.size() >= 3) {
                    ok = false;
                    break;
                }
                cuts.add(p);
                prev = p;
            }
            if (ok && !cuts.isEmpty()) {
                int last = n - cuts.get(cuts.size() - 1);
                if (last >= 1 && last <= 8) return List.copyOf(cuts);
            }
        }
        // Heuristic: chunk by 8
        List<Integer> cuts = new ArrayList<>();
        int end = 8;
        while (end < n && cuts.size() < 3) {
            int remaining = n - end;
            if (remaining <= 8) {
                cuts.add(end);
                break;
            }
            cuts.add(end);
            end += 8;
        }
        return cuts.isEmpty() ? null : cuts;
    }

    /** @deprecated use {@link #resolveCaptureSplits} */
    @Deprecated
    static Integer resolveCaptureSplit(String body, Integer proposed) {
        List<Integer> list = resolveCaptureSplits(body, proposed == null ? null : List.of(proposed));
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    static int countNonEmptyBlocks(String body) {
        if (body == null || body.isBlank()) return 0;
        int c = 0;
        for (String line : body.split("\\R", -1)) {
            if (!line.isBlank()) c++;
        }
        return c;
    }

    private static String text(Object value) {
        if (value == null) return "";
        return LiteralNewlineNormalizer.normalize(String.valueOf(value)).trim();
    }

    private record PostContent(String title, String body, List<Integer> captureSplitAfterLines, String promoTitle,
                               String hookEmotion, String metaphorId, List<String> metaphorIds) { }

    public record PublishedBundle(PostDto post, String body, Long sourceExampleId) {
        public PublishedBundle(PostDto post, String body) {
            this(post, body, null);
        }
    }
}
