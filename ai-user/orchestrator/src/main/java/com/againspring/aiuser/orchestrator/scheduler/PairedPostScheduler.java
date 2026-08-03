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
import com.againspring.aiuser.orchestrator.service.threadplan.ActivityCurve;
import com.againspring.aiuser.orchestrator.service.threadplan.CandidateScheduleSupport;
import com.againspring.aiuser.orchestrator.service.threadplan.PairedHoldMeta;
import com.againspring.aiuser.orchestrator.service.threadplan.PlanPersonaMapper;
import com.againspring.aiuser.orchestrator.service.threadplan.QuietHours;
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
        return runPairedPosts(null);
    }

    public int triggerNow(int maxPairs) {
        return runPairedPosts(Math.max(0, maxPairs));
    }

    /**
     * @param maxPairsOverride null이면 config.pairsPerRun, 아니면 그 값으로 상한
     * @return 이번 실행에서 홀딩에 성공한 pair 수
     */
    public int runPairedPosts(Integer maxPairsOverride) {
        if (!props.isEnabled()) {
            log.debug("[PairedPost] AI_USER_ENABLED=false — skip");
            return 0;
        }
        OrchestratorProperties.PairedPost config = props.getPairedPost();
        if (config == null || !config.isEnabled()) {
            log.debug("[PairedPost] disabled — skip");
            return 0;
        }
        List<PersonaRelationship> all =
            relationshipRepo.findByRelationTypeInAndStatus(PAIR_TYPES, "ACTIVE");
        if (all.isEmpty()) {
            log.warn("[PairedPost] No COUPLE/MARRIAGE/FRIEND relationships found. " +
                     "Seed ai-user/docs/personas/profiles/relationships.yml first.");
            return 0;
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
        int toRun = Math.min(Math.min(runCap, remainingTarget), shuffled.size());
        if (quotaRemaining >= 0 && targetPosts > 0) {
            toRun = Math.min(toRun, quotaRemaining);
        }

        if (toRun <= 0) {
            log.debug("[PairedPost] target satisfied — totalToday={} pairedToday={} desiredToday={} runCap={} quotaRemaining={}",
                totalSyntheticToday, pairedToday, desiredToday, runCap, quotaRemaining);
            return 0;
        }

        EnumMap<PairBucket, Integer> mixCounts = countPairedMixToday();
        List<Instant> slots = sampleAuthorSlots(toRun, config);
        log.info("[PairedPost] Holding {} pair(s) (pool={}, totalToday={}, pairedToday={}, desiredToday={}, romanticToday={}, friendToday={})",
            toRun,
            all.size(),
            totalSyntheticToday,
            pairedToday,
            desiredToday,
            mixCounts.get(PairBucket.ROMANTIC),
            mixCounts.get(PairBucket.FRIEND));

        int attempted = 0;
        for (int i = 0; i < toRun; i++) {
            PairBucket desiredBucket = chooseNextBucket(config, mixCounts);
            Optional<PersonaRelationship> relOpt = takeCandidateForBucket(shuffled, desiredBucket);
            if (relOpt.isEmpty()) {
                relOpt = takeAnyCandidate(shuffled);
            }
            if (relOpt.isEmpty()) {
                break;
            }

            try {
                PersonaRelationship rel = relOpt.get();
                Instant slot = i < slots.size() ? slots.get(i) : QuietHours.enforceAuthorSlot(Instant.now().plus(Duration.ofHours(1)));
                if (holdPair(rel, slot)) {
                    attempted++;
                    mixCounts.merge(bucketForRelationType(rel.getRelationType()), 1, Integer::sum);
                }
            } catch (Exception e) {
                log.error("[PairedPost] Pair {} failed: {}", i, e.getMessage(), e);
            }
        }
        return attempted;
    }

    /**
     * Call1 ({@code PAIRED_PHASE1}) → hold in {@code ai_scheduled_posts}. No backend write until
     * {@code ScheduledPostPublisher} fires the non-quiet slot.
     */
    private boolean holdPair(PersonaRelationship rel, Instant scheduledPublishAt) {
        Optional<Persona> authorOpt = personaRepo.findById(rel.getPersonaId());
        Optional<Persona> partnerOpt = personaRepo.findById(rel.getOtherId());
        if (authorOpt.isEmpty() || partnerOpt.isEmpty()) {
            log.warn("[PairedPost] Persona not found — personaId={} otherId={}",
                rel.getPersonaId(), rel.getOtherId());
            return false;
        }
        Persona author = authorOpt.get();
        Persona partner = partnerOpt.get();

        String category = categoryForRelationType(rel.getRelationType());
        String corrId = UUID.randomUUID().toString().substring(0, 8);
        Instant slot = QuietHours.enforceAuthorSlot(scheduledPublishAt);
        if (QuietHours.isQuiet(slot)) {
            log.warn("[PairedPost] Author slot still quiet after enforce — skip corrId={}", corrId);
            return false;
        }

        Optional<Call1Hold> call1 = generateCall1(author, category, corrId, slot);
        if (call1.isEmpty()) {
            log.warn("[PairedPost] Call1 failed corrId={}", corrId);
            return false;
        }
        Call1Hold heldContent = call1.get();
        ContentSafetyGuard.GuardResult authorGuard =
                safetyGuard.check(heldContent.body(), ContentSafetyGuard.ContentType.POST);
        if (!authorGuard.passed()) {
            log.warn("[PairedPost] Author body blocked: {}", authorGuard.reason());
            return false;
        }

        String candidatesJson;
        try {
            Map<String, Object> payload = new LinkedHashMap<>(heldContent.response());
            payload.put(PairedHoldMeta.KEY,
                    PairedHoldMeta.wrap(partner.getId(), rel.getRelationType(), corrId, 3)
                            .get(PairedHoldMeta.KEY));
            candidatesJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("[PairedPost] Could not serialize Call1 hold corrId={}", corrId, e);
            return false;
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
        return true;
    }

    private Optional<Call1Hold> generateCall1(Persona author, String category, String corrId, Instant slot) {
        AiUserGenerationConfig config = generationConfigRepository.findById(1).orElse(null);
        String provider = config == null ? props.getThreadPlan().getAiPostProvider()
                : config.getProviderAiPostBundle();
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) {
            provider = props.getThreadPlan().getAiPostProvider();
        }
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) {
            log.info("[PairedPost] Call1 skipped: provider OFF corrId={}", corrId);
            return Optional.empty();
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
        request.put("correlationId", corrId);
        request.put("category", category);
        request.put("author", authorMap);
        request.put("personas", personas);
        request.put("maxTopLevel", 4);
        request.put("maxReplies", 0);
        request.put("minTopLevel", 2);
        request.put("minItems", 2);

        Optional<Map<String, Object>> responseOpt = llmClient.generatePairedCall1(request);
        if (responseOpt.isEmpty()) return Optional.empty();
        Map<String, Object> response = new LinkedHashMap<>(responseOpt.get());
        if (!(response.get("items") instanceof List<?>) && response.get("comments") instanceof List<?> comments) {
            response.put("items", comments);
        }
        candidateScheduleSupport.enrichMissingScheduledAts(response, slot);

        Object postRaw = response.get("post");
        if (!(postRaw instanceof Map<?, ?> post)) {
            log.warn("[PairedPost] Call1 missing post corrId={}", corrId);
            return Optional.empty();
        }
        String title = stringVal(post.get("title"));
        String body = stringVal(post.get("body"));
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            log.warn("[PairedPost] Call1 empty title/body corrId={}", corrId);
            return Optional.empty();
        }
        int items = response.get("items") instanceof List<?> list ? list.size() : 0;
        return Optional.of(new Call1Hold(title.strip(), body.strip(), response, provider, model, items));
    }

    private static String stringVal(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private record Call1Hold(String title, String body, Map<String, Object> response,
                             String provider, String model, int itemCount) { }

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

    private Optional<PersonaRelationship> takeCandidateForBucket(
        List<PersonaRelationship> candidates,
        PairBucket bucket
    ) {
        Iterator<PersonaRelationship> iterator = candidates.iterator();
        while (iterator.hasNext()) {
            PersonaRelationship rel = iterator.next();
            if (bucketForRelationType(rel.getRelationType()) == bucket) {
                iterator.remove();
                return Optional.of(rel);
            }
        }
        return Optional.empty();
    }

    private Optional<PersonaRelationship> takeAnyCandidate(List<PersonaRelationship> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.remove(0));
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
