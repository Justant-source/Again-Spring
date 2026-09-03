package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaRelationship;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRelationshipRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.service.DailyPostQuotaService;
import com.againspring.aiuser.orchestrator.service.GenerationConfigSupport;
import com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService;
import com.againspring.aiuser.orchestrator.service.threadplan.ActivityCurve;
import com.againspring.aiuser.orchestrator.service.threadplan.CandidateScheduleSupport;
import com.againspring.aiuser.orchestrator.service.threadplan.HoldResult;
import com.againspring.aiuser.orchestrator.service.threadplan.LlmCallBudget;
import com.againspring.aiuser.orchestrator.service.threadplan.NightlySlotFailure;
import com.againspring.aiuser.orchestrator.service.threadplan.PairedHoldMeta;
import com.againspring.aiuser.orchestrator.service.threadplan.PlanPersonaMapper;
import com.againspring.aiuser.orchestrator.service.threadplan.QuietHours;
import com.againspring.aiuser.orchestrator.service.threadplan.SourceMixPlanner;
import com.againspring.aiuser.orchestrator.service.threadplan.StoryPersonaCommentFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * AI 유저 양면 갈등 시나리오 스케줄러.
 *
 * <p>생성 ≠ 발행 (solo {@code generateAndHold}와 동일):</p>
 * <ol>
 *   <li>persona_relationships에서 COUPLE/MARRIAGE/FRIEND 페어 선택</li>
 *   <li>Call1 ({@code PAIRED_PHASE1}): 작성자 본문 + phase1 댓글 → {@code ai_scheduled_posts} 홀딩
 *       (scheduledPublishAt은 KST 02–06 quiet hours 하드 밴)</li>
 *   <li>{@code ScheduledPostPublisher}가 슬롯 도래 시 author PUBLIC + invite 발급</li>
 *   <li>partner는 T0+Δ({@code PartnerDelaySampler})에 {@code ai_scheduled_partner_answers}로 제출</li>
 * </ol>
 *
 * <p>환경변수: PAIRED_POST_* (delay/publisher/slot 포함)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PairedPostScheduler {

    private final PersonaRelationshipRepository relationshipRepo;
    private final PersonaRepository personaRepo;
    private final LlmAiUserClient llmClient;
    private final OrchestratorProperties props;
    private final JdbcTemplate jdbcTemplate;
    private final ContentSafetyGuard safetyGuard;
    private final AiUserGenerationConfigRepository generationConfigRepository;
    private final DailyPostQuotaService dailyPostQuotaService;
    private final AiScheduledPostRepository scheduledPostRepository;
    private final ObjectMapper objectMapper;
    private final PlanPersonaMapper planPersonaMapper;
    private final CandidateScheduleSupport candidateScheduleSupport;
    private final GenerationConfigSupport generationConfigSupport;
    private final LlmGenerationGateService llmGenerationGateService;
    private final com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache promptTemplateCache;

    /** Phase1 cast stays small (author + commenters for ~2–4 top-level). */
    private static final int CALL1_CAST_MAX = 12;

    private static final Random RNG = new Random();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<String> PAIR_TYPES = List.of("COUPLE", "MARRIAGE", "FRIEND");

    @Scheduled(cron = "${ai-user.paired-post.cron:0 0 5 * * *}")
    public void runPairedPosts() {
        runPairedPosts(null);
    }

    public int triggerNow() {
        return runPairedPosts(null, null, null, false).saved();
    }

    public int triggerNow(int maxPairs) {
        return runPairedPosts(Math.max(0, maxPairs), null, null, false).saved();
    }

    /**
     * Nightly fill: try up to {@code maxPairs} holds, sharing {@code budget}. Does not shrink
     * the request by the daytime remainingTarget (paired dry → caller fills solo remainder).
     */
    public PairHoldBatch tryHoldPairs(int maxPairs, LlmCallBudget budget, List<NightlySlotFailure> failures) {
        return runPairedPosts(Math.max(0, maxPairs), budget, failures, true);
    }

    /**
     * @param maxPairsOverride null이면 config.pairsPerRun, 아니면 그 값으로 상한
     * @return 이번 실행에서 홀딩에 성공한 pair 수
     */
    public int runPairedPosts(Integer maxPairsOverride) {
        return runPairedPosts(maxPairsOverride, null, null, false).saved();
    }

    public record PairHoldBatch(int saved, int attempted, List<String> scheduledIds) {}

    PairHoldBatch runPairedPosts(Integer maxPairsOverride, LlmCallBudget budget,
                                 List<NightlySlotFailure> failures, boolean ignoreDailyTarget) {
        if (!props.isEnabled()) {
            log.debug("[PairedPost] AI_USER_ENABLED=false — skip");
            return new PairHoldBatch(0, 0, List.of());
        }
        OrchestratorProperties.PairedPost config = props.getPairedPost();
        if (config == null || !config.isEnabled()) {
            log.debug("[PairedPost] disabled — skip");
            return new PairHoldBatch(0, 0, List.of());
        }
        List<PersonaRelationship> all =
            relationshipRepo.findByRelationTypeInAndStatus(PAIR_TYPES, "ACTIVE");
        if (all.isEmpty()) {
            log.warn("[PairedPost] No COUPLE/MARRIAGE/FRIEND relationships found. " +
                     "Seed ai-user/docs/personas/profiles/relationships.yml first.");
            addFailure(failures, "paired", "-", "-", "-", HoldResult.Outcome.GENERATION_SKIPPED,
                    "no COUPLE/MARRIAGE/FRIEND relationships");
            return new PairHoldBatch(0, 0, List.of());
        }

        List<PersonaRelationship> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled, RNG);

        int targetPosts = generationConfigRepository.findById(1)
            .map(AiUserGenerationConfig::getTargetPosts)
            .orElse(0);
        int totalSyntheticToday = dailyPostQuotaService.postsCreatedToday();
        int pairedToday = countPairedPostsToday();
        int desiredToday = desiredPairedPostsToday(config, targetPosts, totalSyntheticToday);
        int remainingTarget = Math.max(0, desiredToday - pairedToday);
        int quotaRemaining = targetPosts > 0
            ? dailyPostQuotaService.remaining(targetPosts)
            : remainingTarget;
        int runCap = maxPairsOverride != null
            ? Math.max(0, maxPairsOverride)
            : Math.max(0, config.getPairsPerRun());
        int toRun;
        if (ignoreDailyTarget) {
            toRun = Math.min(runCap, shuffled.size());
        } else {
            toRun = Math.min(Math.min(runCap, remainingTarget), shuffled.size());
            if (quotaRemaining >= 0 && targetPosts > 0) {
                toRun = Math.min(toRun, quotaRemaining);
            }
        }

        if (toRun <= 0) {
            log.debug("[PairedPost] target satisfied — totalToday={} pairedToday={} desiredToday={} runCap={} quotaRemaining={}",
                totalSyntheticToday, pairedToday, desiredToday, runCap, quotaRemaining);
            return new PairHoldBatch(0, 0, List.of());
        }

        EnumMap<PairBucket, Integer> mixCounts = countPairedMixToday();
        List<Instant> slots = sampleAuthorSlots(toRun, config);
        List<String> sources = SourceMixPlanner.planSources(toRun, RNG);
        SourceMixPlanner.MixCounts sourceMix = SourceMixPlanner.planCounts(toRun);
        Map<String, Persona> personasById = loadPersonasById(shuffled);
        log.info("[PairedPost] Holding {} pair(s) (pool={}, totalToday={}, pairedToday={}, desiredToday={}, romanticToday={}, friendToday={}, sourceMix blind={}/natepan={})",
            toRun,
            all.size(),
            totalSyntheticToday,
            pairedToday,
            desiredToday,
            mixCounts.get(PairBucket.ROMANTIC),
            mixCounts.get(PairBucket.FRIEND),
            sourceMix.blind(),
            sourceMix.natepan());

        int saved = 0;
        int attempted = 0;
        List<String> scheduledIds = new ArrayList<>();
        for (int i = 0; i < toRun; i++) {
            if (budget != null && !budget.hasRemaining()) {
                log.info("[PairedPost] LLM budget exhausted used={}/{} — stop paired, leftover becomes solo",
                        budget.used(), budget.max());
                addFailure(failures, "paired", i < sources.size() ? sources.get(i) : "-", "-", "-",
                        HoldResult.Outcome.GENERATION_SKIPPED, "LLM cap reached during paired fill");
                break;
            }
            String preferredSource = sources.get(i);
            PairBucket desiredBucket = chooseNextBucket(config, mixCounts);
            Optional<PersonaRelationship> relOpt = takeCandidateForSourceAndBucket(
                    shuffled, personasById, preferredSource, desiredBucket);
            if (relOpt.isEmpty()) {
                relOpt = takeCandidateForSource(shuffled, personasById, preferredSource);
            }
            if (relOpt.isEmpty()) {
                log.warn("[PairedPost] No relationship author with voice_type for source={} — skip slot {}",
                        preferredSource, i);
                addFailure(failures, "paired", preferredSource, "-", "-", HoldResult.Outcome.CLAIM_EMPTY,
                        "no relationship author for source=" + preferredSource);
                continue;
            }

            try {
                PersonaRelationship rel = relOpt.get();
                Instant slot = i < slots.size() ? slots.get(i) : QuietHours.enforceAuthorSlot(Instant.now().plus(Duration.ofHours(1)));
                HoldPairResult held = holdPair(rel, slot, preferredSource, budget);
                attempted++;
                if (held.saved()) {
                    saved++;
                    if (held.scheduledId() != null) scheduledIds.add(held.scheduledId());
                    mixCounts.merge(bucketForRelationType(rel.getRelationType()), 1, Integer::sum);
                } else {
                    addFailure(failures, "paired", preferredSource, categoryForRelationType(rel.getRelationType()),
                            rel.getPersonaId(), held.outcome(), held.detail());
                }
            } catch (Exception e) {
                attempted++;
                log.error("[PairedPost] Pair {} source={} failed: {}", i, preferredSource, e.getMessage(), e);
                addFailure(failures, "paired", preferredSource, "-", "-", HoldResult.Outcome.LLM_OR_SAFETY,
                        e.getMessage());
            }
        }
        return new PairHoldBatch(saved, attempted, scheduledIds);
    }

    /**
     * Call1 ({@code PAIRED_PHASE1}) → hold in {@code ai_scheduled_posts}. No backend write until
     * {@code ScheduledPostPublisher} fires the non-quiet slot.
     */
    private HoldPairResult holdPair(PersonaRelationship rel, Instant scheduledPublishAt, String preferredSource,
                                    LlmCallBudget budget) {
        Optional<Persona> authorOpt = personaRepo.findById(rel.getPersonaId());
        Optional<Persona> partnerOpt = personaRepo.findById(rel.getOtherId());
        if (authorOpt.isEmpty() || partnerOpt.isEmpty()) {
            log.warn("[PairedPost] Persona not found — personaId={} otherId={}",
                rel.getPersonaId(), rel.getOtherId());
            return HoldPairResult.fail(HoldResult.Outcome.GENERATION_SKIPPED, false,
                    "persona not found personaId=" + rel.getPersonaId());
        }
        Persona author = authorOpt.get();
        Persona partner = partnerOpt.get();

        String category = categoryForRelationType(rel.getRelationType());
        String corrId = UUID.randomUUID().toString().substring(0, 8);
        Instant slot = QuietHours.enforceAuthorSlot(scheduledPublishAt);
        if (QuietHours.isQuiet(slot)) {
            log.warn("[PairedPost] Author slot still quiet after enforce — skip corrId={}", corrId);
            return HoldPairResult.fail(HoldResult.Outcome.GENERATION_SKIPPED, false, "author slot still quiet");
        }

        Call1Attempt call1 = generateCall1(author, category, corrId, slot, preferredSource);
        if (call1.llmInvoked() && budget != null) {
            budget.consume();
        }
        if (call1.hold().isEmpty()) {
            log.warn("[PairedPost] Call1 failed corrId={} reason={}", corrId, call1.detail());
            return HoldPairResult.fail(
                    call1.llmInvoked() ? HoldResult.Outcome.LLM_OR_SAFETY : HoldResult.Outcome.GENERATION_SKIPPED,
                    call1.llmInvoked(),
                    call1.detail());
        }
        Call1Hold heldContent = call1.hold().get();
        ContentSafetyGuard.GuardResult authorGuard =
                safetyGuard.check(heldContent.body(), ContentSafetyGuard.ContentType.POST);
        if (!authorGuard.passed()) {
            log.warn("[PairedPost] Author body blocked: {}", authorGuard.reason());
            return HoldPairResult.fail(HoldResult.Outcome.LLM_OR_SAFETY, true,
                    "unsafe author body: " + authorGuard.reason());
        }

        String candidatesJson;
        try {
            Map<String, Object> payload = new LinkedHashMap<>(heldContent.response());
            payload.put(PairedHoldMeta.KEY,
                    PairedHoldMeta.wrap(partner.getId(), rel.getRelationType(), corrId)
                            .get(PairedHoldMeta.KEY));
            candidatesJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("[PairedPost] Could not serialize Call1 hold corrId={}", corrId, e);
            return HoldPairResult.fail(HoldResult.Outcome.SERIALIZE, true, e.getMessage());
        }

        AiScheduledPost held = AiScheduledPost.builder()
                .personaId(author.getId())
                .category(category)
                .title(heldContent.title())
                .body(heldContent.body())
                .candidatesJson(candidatesJson)
                .scheduledPublishAt(slot)
                .status(ScheduledPostStatus.SCHEDULED)
                .provider(heldContent.provider())
                .model(heldContent.model())
                .origin(PairedHoldMeta.ORIGIN_PAIRED)
                .build();
        scheduledPostRepository.save(held);

        log.info("[PairedPost] ⏳ held corrId={} scheduledId={} author={} partner={} cat={} slot={} phase1Items={}",
                corrId, held.getId(),
                author.getId().substring(0, Math.min(8, author.getId().length())),
                partner.getId().substring(0, Math.min(8, partner.getId().length())),
                category, slot, heldContent.itemCount());
        return new HoldPairResult(true, held.getId(), HoldResult.Outcome.SAVED, "saved");
    }

    Call1Attempt generateCall1(Persona author, String category, String corrId, Instant slot,
                                       String preferredSource) {
        AiUserGenerationConfig config = generationConfigRepository.findById(1).orElse(null);
        // Missing row = no admin switch present -> treat kill as false (fail-open only for this
        // flag; provider resolution below still falls back to yml when the row is absent).
        boolean killSwitchOn = config != null && config.isAiUserKillSwitch();
        if (killSwitchOn) {
            log.info("[PairedPost] Call1 skipped: kill switch on corrId={}", corrId);
            return Call1Attempt.skipped("kill switch");
        }
        String provider = config == null ? props.getThreadPlan().getAiPostProvider() : config.getProviderAiPostBundle();
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) {
            log.info("[PairedPost] Call1 skipped: provider OFF corrId={}", corrId);
            return Call1Attempt.skipped("provider OFF");
        }
        String model = props.getThreadPlan().getAiPostModel();

        List<Persona> pool = PlanPersonaMapper.capCastPool(personaRepo.findByActiveTrue(), CALL1_CAST_MAX);
        List<Map<String, Object>> cast = planPersonaMapper.mapCast(pool);
        // Keep author first for voice grounding.
        Map<String, Object> authorMap = planPersonaMapper.mapAuthor(author);
        List<Map<String, Object>> personas = new ArrayList<>();
        personas.add(authorMap);
        for (Map<String, Object> p : cast) {
            if (author.getId().equals(String.valueOf(p.getOrDefault("personaId", "")))) continue;
            personas.add(p);
            if (personas.size() >= CALL1_CAST_MAX) break;
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", provider);
        request.put("model", model);
        request.put("promptOverrides", promptTemplateCache.overrides());
        request.put("correlationId", corrId);
        request.put("timeoutMs", generationConfigSupport.bundleTimeoutMs());
        request.put("category", category);
        request.put("author", authorMap);
        request.put("personas", personas);
        request.put("maxTopLevel", 4);
        request.put("maxReplies", 0);
        request.put("minTopLevel", 2);
        request.put("minItems", 2);
        if (preferredSource != null && !preferredSource.isBlank()) {
            request.put("preferredSource", preferredSource);
        }

        // LLM Generation Gate check: skip generation if held
        if (llmGenerationGateService.isHeld()) {
            log.info("[PairedPost] Call1 generation held (LLM gate) corrId={}", corrId);
            return Call1Attempt.skipped("LLM generation gate held");
        }

        Optional<Map<String, Object>> responseOpt = llmClient.generatePairedCall1(request);
        if (responseOpt.isEmpty()) return Call1Attempt.llmFail("Call1 LLM empty response");
        Map<String, Object> response = new LinkedHashMap<>(responseOpt.get());
        if (!(response.get("items") instanceof List<?>) && response.get("comments") instanceof List<?> comments) {
            response.put("items", comments);
        }
        candidateScheduleSupport.enrichMissingScheduledAts(response, slot);

        Object postRaw = response.get("post");
        if (!(postRaw instanceof Map<?, ?> post)) {
            log.warn("[PairedPost] Call1 missing post corrId={}", corrId);
            return Call1Attempt.llmFail("Call1 missing post");
        }
        String title = stringVal(post.get("title"));
        String body = stringVal(post.get("body"));
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            log.warn("[PairedPost] Call1 empty title/body corrId={}", corrId);
            return Call1Attempt.llmFail("Call1 empty title/body");
        }
        int items = response.get("items") instanceof List<?> list ? list.size() : 0;
        int stripped = StoryPersonaCommentFilter.stripFromResponse(response, Set.of(author.getId()));
        if (stripped > 0) {
            log.info("[PairedPost] Call1 stripped {} author self-comment(s) corrId={}", stripped, corrId);
            items = response.get("items") instanceof List<?> list ? list.size() : 0;
        }
        return Call1Attempt.ok(new Call1Hold(title.strip(), body.strip(), response, provider, model, items));
    }

    private static String stringVal(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    record Call1Hold(String title, String body, Map<String, Object> response,
                             String provider, String model, int itemCount) { }

    record Call1Attempt(Optional<Call1Hold> hold, boolean llmInvoked, String detail) {
        static Call1Attempt ok(Call1Hold hold) {
            return new Call1Attempt(Optional.of(hold), true, "ok");
        }
        static Call1Attempt llmFail(String detail) {
            return new Call1Attempt(Optional.empty(), true, detail);
        }
        static Call1Attempt skipped(String detail) {
            return new Call1Attempt(Optional.empty(), false, detail);
        }
    }

    private record HoldPairResult(boolean saved, String scheduledId, HoldResult.Outcome outcome, String detail) {
        static HoldPairResult fail(HoldResult.Outcome outcome, boolean ignoredLlm, String detail) {
            return new HoldPairResult(false, null, outcome, detail);
        }
    }

    private static void addFailure(List<NightlySlotFailure> failures, String kind, String source, String plaza,
                                   String personaId, HoldResult.Outcome outcome, String detail) {
        if (failures == null) return;
        String reason = String.format("outcome=%s source=%s plaza=%s persona=%s exampleId=- %s",
                outcome, source, plaza, personaId, detail == null ? "" : detail);
        failures.add(new NightlySlotFailure(kind, source, plaza, personaId, outcome, reason));
    }

    /**
     * Samples author publish slots via ActivityCurve, then hard-bans KST 02–06.
     * Window: max(now, today@fromHour) → today@toHour (extends +1 day if needed).
     */
    List<Instant> sampleAuthorSlots(int count, OrchestratorProperties.PairedPost config) {
        if (count <= 0) return List.of();
        int fromHour = config.getAuthorSlotFromHour();
        int toHour = config.getAuthorSlotToHour();
        LocalDate today = LocalDate.now(KST);
        Instant now = Instant.now();
        Instant from = today.atStartOfDay(KST).plusHours(fromHour).toInstant();
        if (now.isAfter(from)) from = now;
        Instant to = today.atStartOfDay(KST).plusHours(toHour).toInstant();
        if (!to.isAfter(from)) {
            to = from.plus(Duration.ofHours(Math.max(4, toHour - fromHour)));
        }

        long minSpacing = Math.max(15, props.getThreadPlan().getPostSlotMinSpacingMinutes());
        List<Instant> raw;
        try {
            raw = ActivityCurve.sampleFutureInstants(
                    from, to, count,
                    props.getThreadPlan().getKstHourlyHumanWeights(),
                    Duration.ofMinutes(minSpacing),
                    RNG);
        } catch (IllegalArgumentException e) {
            log.warn("[PairedPost] slot sampling failed ({}), using sequential offsets", e.getMessage());
            raw = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                raw.add(from.plus(Duration.ofMinutes(minSpacing * (long) i)));
            }
        }

        List<Instant> out = new ArrayList<>(raw.size());
        for (Instant slot : raw) {
            Instant enforced = QuietHours.enforceAuthorSlot(slot);
            // Reject if still quiet (misconfig) — bump further with nextResume.
            if (QuietHours.isQuiet(enforced)) {
                enforced = QuietHours.nextResumeAfter(enforced);
            }
            out.add(enforced);
        }
        out.sort(Instant::compareTo);
        return out;
    }

    private int desiredPairedPostsToday(
        OrchestratorProperties.PairedPost config,
        int targetPosts,
        int totalSyntheticToday
    ) {
        int basePostCount = targetPosts > 0 ? targetPosts : totalSyntheticToday;
        if (basePostCount <= 0) {
            return 0;
        }

        double share = clampShare(config.getTargetShare());
        if (share <= 0.0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(basePostCount * share));
    }

    /**
     * Counts held PAIRED rows created today (generate≠publish) plus legacy completed
     * partner-answered posts that may not have gone through the hold table.
     */
    private int countPairedPostsToday() {
        int held = countHeldPairedToday();
        int answered = countAnsweredPairedToday();
        return Math.max(held, answered);
    }

    private int countHeldPairedToday() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_scheduled_posts " +
                "WHERE origin = ? " +
                "  AND status <> 'CANCELLED' " +
                "  AND created_at >= ?",
                Integer.class,
                PairedHoldMeta.ORIGIN_PAIRED,
                todayStartTimestamp());
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("[PairedPost] countHeldPairedToday failed: {}", e.getMessage());
            return 0;
        }
    }

    private int countAnsweredPairedToday() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts p " +
                "JOIN users u ON p.author_id = u.id " +
                "WHERE u.synthetic = 1 " +
                "  AND p.deleted_at IS NULL " +
                "  AND p.created_at >= ? " +
                "  AND p.partner_answered_at IS NOT NULL " +
                "  AND p.partner_body_published IS NOT NULL",
                Integer.class,
                todayStartTimestamp());
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("[PairedPost] countAnsweredPairedToday failed: {}", e.getMessage());
            return 0;
        }
    }

    private EnumMap<PairBucket, Integer> countPairedMixToday() {
        EnumMap<PairBucket, Integer> counts = new EnumMap<>(PairBucket.class);
        counts.put(PairBucket.ROMANTIC, 0);
        counts.put(PairBucket.FRIEND, 0);

        try {
            List<Map<String, Object>> heldRows = jdbcTemplate.queryForList(
                "SELECT category AS category, COUNT(*) AS cnt " +
                "FROM ai_scheduled_posts " +
                "WHERE origin = ? " +
                "  AND status <> 'CANCELLED' " +
                "  AND created_at >= ? " +
                "  AND category IN ('COUPLE', 'MARRIED', 'FRIEND') " +
                "GROUP BY category",
                PairedHoldMeta.ORIGIN_PAIRED,
                todayStartTimestamp());
            for (Map<String, Object> row : heldRows) {
                PairBucket bucket = bucketForCategory(Objects.toString(row.get("category"), ""));
                int count = ((Number) row.getOrDefault("cnt", 0)).intValue();
                counts.merge(bucket, count, Integer::sum);
            }
        } catch (Exception e) {
            log.warn("[PairedPost] countPairedMixToday(held) failed: {}", e.getMessage());
        }

        if (counts.get(PairBucket.ROMANTIC) + counts.get(PairBucket.FRIEND) > 0) {
            return counts;
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.category AS category, COUNT(*) AS cnt " +
                "FROM posts p " +
                "JOIN users u ON p.author_id = u.id " +
                "WHERE u.synthetic = 1 " +
                "  AND p.deleted_at IS NULL " +
                "  AND p.created_at >= ? " +
                "  AND p.partner_answered_at IS NOT NULL " +
                "  AND p.partner_body_published IS NOT NULL " +
                "  AND p.category IN ('COUPLE', 'MARRIED', 'FRIEND') " +
                "GROUP BY p.category",
                todayStartTimestamp());

            for (Map<String, Object> row : rows) {
                PairBucket bucket = bucketForCategory(Objects.toString(row.get("category"), ""));
                int count = ((Number) row.getOrDefault("cnt", 0)).intValue();
                counts.merge(bucket, count, Integer::sum);
            }
        } catch (Exception e) {
            log.warn("[PairedPost] countPairedMixToday(answered) failed: {}", e.getMessage());
        }

        return counts;
    }

    private PairBucket chooseNextBucket(OrchestratorProperties.PairedPost config, EnumMap<PairBucket, Integer> counts) {
        int romanticCount = counts.getOrDefault(PairBucket.ROMANTIC, 0);
        int friendCount = counts.getOrDefault(PairBucket.FRIEND, 0);
        int nextTotal = romanticCount + friendCount + 1;

        double romanticShare = clampShare(config.getRomanticShare());
        double friendShare = 1.0 - romanticShare;

        double romanticDeficit = (nextTotal * romanticShare) - romanticCount;
        double friendDeficit = (nextTotal * friendShare) - friendCount;
        return friendDeficit > romanticDeficit ? PairBucket.FRIEND : PairBucket.ROMANTIC;
    }

    private Map<String, Persona> loadPersonasById(List<PersonaRelationship> relationships) {
        Set<String> ids = new LinkedHashSet<>();
        for (PersonaRelationship rel : relationships) {
            if (rel.getPersonaId() != null) ids.add(rel.getPersonaId());
            if (rel.getOtherId() != null) ids.add(rel.getOtherId());
        }
        Map<String, Persona> byId = new HashMap<>();
        for (String id : ids) {
            personaRepo.findById(id).ifPresent(p -> byId.put(id, p));
        }
        return byId;
    }

    /**
     * Prefer HEAVY author with matching voice_type in the desired romantic/friend bucket.
     * Never returns a wrong voice_type.
     */
    private Optional<PersonaRelationship> takeCandidateForSourceAndBucket(
            List<PersonaRelationship> candidates,
            Map<String, Persona> personasById,
            String preferredSource,
            PairBucket bucket) {
        return takeMatchingAuthor(candidates, personasById, preferredSource, bucket, true)
                .or(() -> takeMatchingAuthor(candidates, personasById, preferredSource, bucket, false));
    }

    private Optional<PersonaRelationship> takeCandidateForSource(
            List<PersonaRelationship> candidates,
            Map<String, Persona> personasById,
            String preferredSource) {
        return takeMatchingAuthor(candidates, personasById, preferredSource, null, true)
                .or(() -> takeMatchingAuthor(candidates, personasById, preferredSource, null, false));
    }

    private Optional<PersonaRelationship> takeMatchingAuthor(
            List<PersonaRelationship> candidates,
            Map<String, Persona> personasById,
            String preferredSource,
            PairBucket bucketOrNull,
            boolean heavyOnly) {
        Optional<String> voiceOpt = SourceMixPlanner.voiceTypeForSource(preferredSource);
        if (voiceOpt.isEmpty()) return Optional.empty();
        String voice = voiceOpt.get();

        Iterator<PersonaRelationship> iterator = candidates.iterator();
        while (iterator.hasNext()) {
            PersonaRelationship rel = iterator.next();
            if (bucketOrNull != null && bucketForRelationType(rel.getRelationType()) != bucketOrNull) {
                continue;
            }
            Persona author = personasById.get(rel.getPersonaId());
            if (author == null || !SourceMixPlanner.matchesVoice(author, voice)) {
                continue;
            }
            if (heavyOnly && !"HEAVY".equals(author.getTier())) {
                continue;
            }
            iterator.remove();
            return Optional.of(rel);
        }
        return Optional.empty();
    }

    private PairBucket bucketForRelationType(String relationType) {
        return "FRIEND".equalsIgnoreCase(relationType) ? PairBucket.FRIEND : PairBucket.ROMANTIC;
    }

    private PairBucket bucketForCategory(String category) {
        return "FRIEND".equalsIgnoreCase(category) ? PairBucket.FRIEND : PairBucket.ROMANTIC;
    }

    private String categoryForRelationType(String relationType) {
        return switch (relationType != null ? relationType.toUpperCase(Locale.ROOT) : "") {
            case "COUPLE" -> "COUPLE";
            case "FRIEND" -> "FRIEND";
            case "MARRIAGE" -> "MARRIED";
            default -> "MARRIED";
        };
    }

    private Timestamp todayStartTimestamp() {
        ZonedDateTime todayStart = LocalDate.now(KST).atStartOfDay(KST);
        return Timestamp.from(todayStart.toInstant());
    }

    private double clampShare(double share) {
        if (Double.isNaN(share)) return 0.0;
        return Math.max(0.0, Math.min(1.0, share));
    }

    private enum PairBucket {
        ROMANTIC,
        FRIEND
    }
}
