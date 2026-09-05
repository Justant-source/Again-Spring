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
import com.againspring.aiuser.orchestrator.safety.SourceOverlapGuard;
import com.againspring.aiuser.orchestrator.service.DailyPostQuotaService;
import com.againspring.aiuser.orchestrator.service.GenerationConfigSupport;
import com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService;
import com.againspring.aiuser.orchestrator.service.threadplan.AiPostBundleService;
import com.againspring.aiuser.orchestrator.service.threadplan.CategoryMixPlanner;
import com.againspring.aiuser.orchestrator.service.persona.PersonaLottery;
import com.againspring.aiuser.orchestrator.service.threadplan.ActivityCurve;
import com.againspring.aiuser.orchestrator.service.threadplan.CandidateScheduleSupport;
import com.againspring.aiuser.orchestrator.service.threadplan.HoldResult;
import com.againspring.aiuser.orchestrator.service.threadplan.LlmCallBudget;
import com.againspring.aiuser.orchestrator.service.threadplan.NightlySlotFailure;
import com.againspring.aiuser.orchestrator.service.threadplan.PairedHoldMeta;
import com.againspring.aiuser.orchestrator.service.threadplan.PlanPersonaMapper;
import com.againspring.aiuser.orchestrator.service.threadplan.PlanSourceStoryResolver;
import com.againspring.aiuser.orchestrator.service.threadplan.QuietHours;
import com.againspring.aiuser.orchestrator.service.threadplan.SourceMixPlanner;
import com.againspring.aiuser.orchestrator.service.threadplan.SourceReservationSupport;
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
    private final PersonaLottery personaLottery;
    private final PlanSourceStoryResolver sourceStoryResolver;
    private final AiPostBundleService aiPostBundleService;
    private final SourceOverlapGuard sourceOverlapGuard;
    private final SourceReservationSupport sourceReservationSupport;

    /** Phase1 cast stays small (author + commenters for ~2–4 top-level). */
    private static final int CALL1_CAST_MAX = 12;

    /**
     * persona-diversity-v4 WP2 배선 갱신 — Call1에서 실제로 소비한 골격을 {@code ai_scheduled_posts
     * .candidates_json}에 함께 실어, 나중에(다른 lease/row인) Call2({@link PartnerAnswerPublisher})가
     * 같은 뼈대로 상대방(B) 본문을 재구성하게 한다. {@link PairedHoldMeta#KEY}와 별개 키.
     */
    static final String PAIRED_SKELETON_KEY = "_pairedSkeleton";

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

        List<Instant> slots = sampleAuthorSlots(toRun, config);
        // preferredSource stays a stylistic anchor for Call1 (blind/natepan tone hint) only —
        // WP3부터 author 선택은 voice_type 매칭이 아니라 PersonaLottery 가중 추첨이다.
        List<String> sources = SourceMixPlanner.planSources(toRun, RNG);
        Map<String, Persona> personasById = loadPersonasById(shuffled);
        Deque<String> categoryQueue = pairedCategoryQueue(toRun, RNG);
        log.info("[PairedPost] Holding {} pair(s) (pool={}, totalToday={}, pairedToday={}, desiredToday={})",
            toRun, all.size(), totalSyntheticToday, pairedToday, desiredToday);

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
            String category = categoryQueue.isEmpty() ? CategoryMixPlanner.COUPLE : categoryQueue.poll();
            Optional<PersonaRelationship> relOpt = drawRelationshipForCategory(shuffled, personasById, category, RNG);
            if (relOpt.isEmpty()) {
                log.warn("[PairedPost] No relationship author available for category={} — skip slot {}",
                        category, i);
                addFailure(failures, "paired", preferredSource, category, "-", HoldResult.Outcome.CLAIM_EMPTY,
                        "no relationship author for category=" + category);
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

    /** WP3 계약 6: 관계 보유자 풀에서 {@code category}에 맞는 작성자를 가중 비복원 추첨한다. */
    private Optional<PersonaRelationship> drawRelationshipForCategory(
            List<PersonaRelationship> pool, Map<String, Persona> personasById, String category, Random rng) {
        String relationType = relationTypeForCategory(category);
        List<PersonaRelationship> candidates = new ArrayList<>();
        for (PersonaRelationship r : pool) {
            if (relationType.equalsIgnoreCase(r.getRelationType())) candidates.add(r);
        }
        if (candidates.isEmpty()) return Optional.empty();
        List<Persona> authorPool = new ArrayList<>();
        Map<String, PersonaRelationship> relByPersonaId = new LinkedHashMap<>();
        for (PersonaRelationship r : candidates) {
            Persona author = personasById.get(r.getPersonaId());
            if (author == null) continue;
            authorPool.add(author);
            relByPersonaId.putIfAbsent(author.getId(), r);
        }
        List<Persona> drawn = personaLottery.drawAuthors(authorPool, category, 1, rng);
        if (drawn.isEmpty()) return Optional.empty();
        PersonaRelationship chosen = relByPersonaId.get(drawn.get(0).getId());
        if (chosen != null) pool.remove(chosen);
        return Optional.ofNullable(chosen);
    }

    /** {@link #categoryForRelationType} 역방향: MARRIED→MARRIAGE, 나머지는 카테고리명과 동일. */
    private static String relationTypeForCategory(String category) {
        if ("MARRIED".equalsIgnoreCase(category)) return "MARRIAGE";
        if ("FRIEND".equalsIgnoreCase(category)) return "FRIEND";
        return "COUPLE";
    }

    /** {@link CategoryMixPlanner} 비율대로 양면 허용 카테고리만 뽑아 최소 {@code minSize}개를 채운 큐. */
    private static Deque<String> pairedCategoryQueue(int minSize, Random rng) {
        Deque<String> queue = new ArrayDeque<>();
        int guard = 0;
        while (queue.size() < Math.max(1, minSize) && guard++ < 20) {
            for (CategoryMixPlanner.Slot slot : CategoryMixPlanner.plan(20, rng)) {
                if (slot.pairedAllowed()) queue.add(slot.category());
            }
        }
        return queue;
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

        // persona-diversity-v4 WP2/WP3 배선: 계약7 골격의 b_side_viable을 보려면 popular source를
        // claim해야 한다. 실패(claim 없음·skeleton 추출 실패·네트워크 오류)는 전부 기존 동작
        // (freestyle, viable 취급)으로 fail-open — 이 배선 이전에는 paired가 claim 자체를
        // 하지 않았으므로 회귀 위험을 만들지 않는다.
        String reservationKey = "paired-" + corrId;
        Instant reserveUntil = slot.plus(Duration.ofHours(24));
        PlanSourceStoryResolver.ResolvedSource resolvedSource = claimForPairedGuard(
                author, preferredSource, reservationKey, reserveUntil, category, corrId);

        // 코드리뷰 #2 대응: claim(위) 이후 구간에서 unchecked 예외(예: generateCall1 내부)가
        // 나면 기존에는 release 체크포인트가 전부 정상 반환 경로에만 있어 전부 건너뛰고
        // runPairedPosts()의 바깥 catch로 곧장 전파됐다 — 24h TTL이 있어 영구 누수는 아니지만
        // 그 사이 소스가 잠긴다. try/finally로 감싸 예외 경로에서도 정확히 한 번 해제되게 한다.
        // sourceHandled=true는 "이미 명시적으로 release했거나, candidatesJson에 실려 실제로
        // DB에 저장돼 예약 소유권이 ScheduledPostPublisher로 넘어갔다"는 뜻 — finally는 그
        // 경우 다시 release하지 않는다(이중 해제 방지). resolvedSource==null이면 애초에 claim한
        // 것이 없으므로 항상 true(해제할 것 없음)로 시작한다.
        boolean sourceHandled = resolvedSource == null;
        try {
            if (resolvedSource != null && !isBSideViable(resolvedSource)) {
                log.warn("[PairedPost] PAIRED_DOWNGRADED_TO_SOLO corrId={} persona={} category={} exampleId={}",
                        corrId, author.getId(), category, resolvedSource.sourceExampleId());
                sourceStoryResolver.release(resolvedSource.sourceExampleId(), reservationKey);
                sourceHandled = true;
                return downgradeToSolo(author, category, corrId, slot, preferredSource, budget);
            }

            Call1Attempt call1 = generateCall1(author, category, corrId, slot, preferredSource, partner.getId(), resolvedSource);
            if (call1.llmInvoked() && budget != null) {
                budget.consume();
            }
            if (call1.hold().isEmpty()) {
                log.warn("[PairedPost] Call1 failed corrId={} reason={}", corrId, call1.detail());
                if (resolvedSource != null) {
                    sourceStoryResolver.release(resolvedSource.sourceExampleId(), reservationKey);
                    sourceHandled = true;
                }
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
                if (resolvedSource != null) {
                    sourceStoryResolver.release(resolvedSource.sourceExampleId(), reservationKey);
                    sourceHandled = true;
                }
                return HoldPairResult.fail(HoldResult.Outcome.LLM_OR_SAFETY, true,
                        "unsafe author body: " + authorGuard.reason());
            }

            if (resolvedSource != null) {
                // persona-diversity-v4 WP2 item5 배선: paired author 본문 경로. 원문(sourceBody)은
                // 이 검사에서만 메모리로 쓰고 로그·DB에는 절대 남기지 않는다.
                SourceOverlapGuard.GuardResult overlap = sourceOverlapGuard.check(
                        heldContent.title() + "\n" + heldContent.body(), resolvedSource.sourceBody());
                if (!overlap.passed()) {
                    log.error("[PairedPost] {} overlapRatio={} corrId={}",
                            overlap.reason(), overlap.overlapRatio(), corrId);
                    sourceStoryResolver.release(resolvedSource.sourceExampleId(), reservationKey);
                    sourceHandled = true;
                    return HoldPairResult.fail(HoldResult.Outcome.LLM_OR_SAFETY, true, overlap.reason());
                }
                // WP2 item5 재배선: 골격을 Call1 프롬프트에 실제로 태웠으므로 여기서 release하지 않는다
                // (release=미사용 취급). solo의 SOURCE_PROVENANCE_KEY 패턴대로 candidates_json에
                // reservationKey를 실어 두면, 실제 발행 시 ScheduledPostPublisher가 solo와 동일하게
                // commitFromCandidatesJson/releaseFromCandidatesJson으로 정상 소비 처리한다. 아래
                // persist가 실제로 성공해야 소유권이 넘어간 것이므로 sourceHandled는 아직 두지 않는다.
            }

            String candidatesJson;
            try {
                Map<String, Object> payload = new LinkedHashMap<>(heldContent.response());
                payload.put(PairedHoldMeta.KEY,
                        PairedHoldMeta.wrap(partner.getId(), rel.getRelationType(), corrId)
                                .get(PairedHoldMeta.KEY));
                if (resolvedSource != null) {
                    payload.put(AiPostBundleService.SOURCE_PROVENANCE_KEY,
                            sourceReservationSupport.provenanceWithReservation(resolvedSource, reservationKey));
                    // Call2(PartnerAnswerPublisher)는 다른 lease/row에서 나중에 돈다 — 같은 골격으로
                    // 상대방(B) 본문을 재구성하도록 skeleton 자체도 함께 실어 둔다.
                    Map<String, Object> pairedSkeleton = new LinkedHashMap<>();
                    pairedSkeleton.put("sourceContext", resolvedSource.sourceContext());
                    pairedSkeleton.put("reconstructMode", resolvedSource.reconstructMode());
                    if (resolvedSource.sourceExampleId() != null) {
                        pairedSkeleton.put("sourceExampleId", resolvedSource.sourceExampleId());
                    }
                    pairedSkeleton.put("bSideViable", isBSideViable(resolvedSource));
                    payload.put(PAIRED_SKELETON_KEY, pairedSkeleton);
                }
                candidatesJson = objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                log.error("[PairedPost] Could not serialize Call1 hold corrId={}", corrId, e);
                if (resolvedSource != null) {
                    sourceStoryResolver.release(resolvedSource.sourceExampleId(), reservationKey);
                    sourceHandled = true;
                }
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
            try {
                scheduledPostRepository.save(held);
            } catch (RuntimeException persistFailure) {
                log.error("[PairedPost] hold persist failed corrId={}", corrId, persistFailure);
                if (resolvedSource != null) {
                    sourceStoryResolver.release(resolvedSource.sourceExampleId(), reservationKey);
                    sourceHandled = true;
                }
                return HoldPairResult.fail(HoldResult.Outcome.PERSIST, true, persistFailure.getMessage());
            }

            // 성공: candidatesJson이 실제로 DB에 저장돼 예약 소유권이 row로 넘어갔다 — 여기서는
            // release하지 않는다(ScheduledPostPublisher가 나중에 commit/release로 소비).
            sourceHandled = true;

            log.info("[PairedPost] ⏳ held corrId={} scheduledId={} author={} partner={} cat={} slot={} phase1Items={}",
                    corrId, held.getId(),
                    author.getId().substring(0, Math.min(8, author.getId().length())),
                    partner.getId().substring(0, Math.min(8, partner.getId().length())),
                    category, slot, heldContent.itemCount());
            return new HoldPairResult(true, held.getId(), HoldResult.Outcome.SAVED, "saved");
        } finally {
            if (resolvedSource != null && !sourceHandled) {
                log.warn("[PairedPost] releasing source reservation after unexpected exception corrId={} exampleId={}",
                        corrId, resolvedSource.sourceExampleId());
                try {
                    sourceStoryResolver.release(resolvedSource.sourceExampleId(), reservationKey);
                } catch (Exception releaseFailure) {
                    log.debug("[PairedPost] source release-on-exception failed (non-critical) corrId={}: {}",
                            corrId, releaseFailure.getMessage());
                }
            }
        }
    }

    /**
     * persona-diversity-v4 배선 — b_side_viable 판정과 {@link SourceOverlapGuard} 대조에만 쓸
     * popular source를 claim한다. 실패(claim 없음·skeleton 추출 실패·예외)는 전부 {@code null}
     * (기존 freestyle 동작 유지, fail-open).
     */
    private PlanSourceStoryResolver.ResolvedSource claimForPairedGuard(
            Persona author, String preferredSource, String reservationKey, Instant reserveUntil,
            String category, String corrId) {
        try {
            return sourceStoryResolver
                    .claimAndResolve(author, preferredSource, reservationKey, reserveUntil, category)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("[PairedPost] source claim for guard failed (non-critical) corrId={}: {}",
                    corrId, e.getMessage());
            return null;
        }
    }

    /** 계약7 골격의 {@code b_side_viable}. 키 부재·파싱 불가 시 기존 동작 유지를 위해 true(강행). */
    private static boolean isBSideViable(PlanSourceStoryResolver.ResolvedSource source) {
        if (source == null || source.sourceContext() == null) return true;
        Object v = source.sourceContext().get("b_side_viable");
        if (v == null) return true;
        if (v instanceof Boolean b) return b;
        return !"false".equalsIgnoreCase(String.valueOf(v).trim());
    }

    /** WP3 배선 — b_side_viable=false 골격의 paired 슬롯을 solo 홀딩으로 강등한다. */
    private HoldPairResult downgradeToSolo(Persona author, String category, String corrId, Instant slot,
                                           String preferredSource, LlmCallBudget budget) {
        HoldResult solo = aiPostBundleService.generateAndHoldResult(
                author, category, null, corrId, slot, preferredSource, Set.of());
        if (budget != null && solo.llmInvoked()) {
            budget.consume();
        }
        if (solo.outcome() == HoldResult.Outcome.SAVED) {
            String scheduledId = solo.saved().map(AiScheduledPost::getId).orElse(null);
            log.info("[PairedPost] downgraded-to-solo saved corrId={} scheduledId={}", corrId, scheduledId);
            return new HoldPairResult(true, scheduledId, HoldResult.Outcome.SAVED, "downgraded-to-solo");
        }
        return HoldPairResult.fail(solo.outcome(), solo.llmInvoked(), "downgraded-to-solo: " + solo.detail());
    }

    Call1Attempt generateCall1(Persona author, String category, String corrId, Instant slot,
                                       String preferredSource) {
        return generateCall1(author, category, corrId, slot, preferredSource, null, null);
    }

    Call1Attempt generateCall1(Persona author, String category, String corrId, Instant slot,
                                       String preferredSource, String partnerId) {
        return generateCall1(author, category, corrId, slot, preferredSource, partnerId, null);
    }

    /**
     * @param resolvedSource claim한 계약7 골격(있으면). null이면 기존 freestyle 동작
     *                       (claim 없음·claim 실패)과 동일 — SKELETON 없이 생성한다.
     */
    Call1Attempt generateCall1(Persona author, String category, String corrId, Instant slot,
                                       String preferredSource, String partnerId,
                                       PlanSourceStoryResolver.ResolvedSource resolvedSource) {
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

        // WP3 계약 6: 캐스트 = [작성자] + 가중 비복원 추첨 댓글자 11명. 파트너는 Call2에서
        // 등장하므로 Call1 방청객(bystander)에서 제외한다.
        java.util.Set<String> exclude = partnerId != null && !partnerId.isBlank()
                ? java.util.Set.of(author.getId(), partnerId) : java.util.Set.of(author.getId());
        List<Persona> drawnCommenters = personaLottery.drawCommenters(
                personaRepo.findByActiveTrue(), category, exclude, CALL1_CAST_MAX - 1, RNG);
        Map<String, Object> authorMap = planPersonaMapper.mapAuthor(author);
        List<Map<String, Object>> personas = new ArrayList<>(1 + drawnCommenters.size());
        personas.add(authorMap);
        personas.addAll(planPersonaMapper.mapCast(drawnCommenters));

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
        // persona-diversity-v4 WP2 재배선 — claim한 골격을 실제로 Call1에 실어 보낸다
        // (AiPostBundleService.baseAiPostRequest의 sourceContext/reconstructMode와 동일 패턴).
        if (resolvedSource != null) {
            if (resolvedSource.topicSeed() != null && !resolvedSource.topicSeed().isBlank()) {
                request.put("topicHint", resolvedSource.topicSeed());
            }
            request.put("sourceContext", resolvedSource.sourceContext());
            request.put("reconstructMode", resolvedSource.reconstructMode());
            if (resolvedSource.sourceExampleId() != null) {
                request.put("sourceExampleId", resolvedSource.sourceExampleId());
            }
            if (resolvedSource.dynamicExamples() != null && !resolvedSource.dynamicExamples().isBlank()) {
                request.put("dynamicExamples", resolvedSource.dynamicExamples());
            }
            List<String> recentOutputs =
                    PlanSourceStoryResolver.recentOutputsForRequest(resolvedSource.recentBodies(), 200);
            if (!recentOutputs.isEmpty()) {
                request.put("recentOutputs", recentOutputs);
            }
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
}
