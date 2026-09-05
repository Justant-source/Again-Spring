package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.notification.ScheduledPostTelegramNotifier;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.scheduler.PairedPostScheduler;
import com.againspring.aiuser.orchestrator.service.threadplan.CategoryMixPlanner;
import com.againspring.aiuser.orchestrator.service.persona.PersonaLottery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.againspring.aiuser.orchestrator.client.AiUserMlClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Shared retry loop for admin manual solo fill and the nightly combined fill.
 * Preferred source/plaza first; empty claims retry other plaza/source/persona without LLM.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NightlyScheduledFillService {

    public static final int LLM_CAP_MULTIPLIER = 3;

    private final AiPostBundleService aiPostBundleService;
    private final PairedPostScheduler pairedPostScheduler;
    private final PersonaRepository personaRepository;
    private final AiUserGenerationConfigRepository configRepository;
    private final OrchestratorProperties properties;
    private final ScheduledPostTelegramNotifier telegramNotifier;
    private final AiUserMlClient aiUserMlClient;
    private final PersonaLottery personaLottery;

    public record FillResult(
            int target,
            int saved,
            int attempted,
            int llmUsed,
            int llmMax,
            int pairedSaved,
            int soloSaved,
            List<String> scheduledIds,
            List<NightlySlotFailure> failures,
            String error,
            int skipped
    ) {
        public List<String> failureReasons() {
            return failures.stream().map(NightlySlotFailure::format).toList();
        }
        public FillResult(int target, int saved, int attempted, int llmUsed, int llmMax,
                          int pairedSaved, int soloSaved, List<String> scheduledIds,
                          List<NightlySlotFailure> failures, String error) {
            this(target, saved, attempted, llmUsed, llmMax, pairedSaved, soloSaved,
                 scheduledIds, failures, error, 0);
        }
    }

    public FillResult fillNightly(int fromHour, int toHour, long minSpacingMinutes, boolean notifyOnShortfall) {
        AiUserGenerationConfig cfg = configRepository.findById(1).orElse(null);
        int n = cfg == null ? 0 : Math.max(0, Math.min(cfg.getTargetPosts(), 100));
        double share = cfg == null ? 0.20 : cfg.getNightlyPairedShare();
        int pairedTarget = pairedCountFor(n, share);
        LlmCallBudget budget = LlmCallBudget.ofMultiplier(n, LLM_CAP_MULTIPLIER);
        List<NightlySlotFailure> failures = new ArrayList<>();
        List<String> scheduledIds = new ArrayList<>();
        int attempted = 0;

        int pairedSaved = 0;
        if (n > 0 && pairedTarget > 0) {
            PairedPostScheduler.PairHoldBatch paired = pairedPostScheduler.tryHoldPairs(pairedTarget, budget, failures);
            pairedSaved = paired.saved();
            attempted += paired.attempted();
            scheduledIds.addAll(paired.scheduledIds());
        }

        int soloNeed = Math.max(0, n - pairedSaved);
        FillResult solo = fillSolo(soloNeed, fromHour, toHour, minSpacingMinutes, budget, failures, scheduledIds);
        attempted += solo.attempted();
        int saved = pairedSaved + solo.soloSaved();

        log.info("[nightly-fill] attempted={} saved={} (paired={} solo={}) target={} llm={}/{} failures={} skipped={}",
                attempted, saved, pairedSaved, solo.soloSaved(), n, budget.used(), budget.max(), failures.size(), solo.skipped());
        for (NightlySlotFailure f : failures) {
            log.warn("[nightly-fill] slot failure: {}", f.format());
        }

        if (notifyOnShortfall && n > 0 && saved < n) {
            telegramNotifier.nightlyShortfall(n, saved, budget.used(), budget.max(),
                    failures.stream().map(NightlySlotFailure::format).toList());
        }

        return new FillResult(n, saved, attempted, budget.used(), budget.max(), pairedSaved, solo.soloSaved(),
                scheduledIds, failures, solo.error(), solo.skipped());
    }

    public FillResult fillSolo(int count, int fromHour, int toHour, long minSpacingMinutes, LlmCallBudget budget) {
        List<NightlySlotFailure> failures = new ArrayList<>();
        List<String> scheduledIds = new ArrayList<>();
        return fillSolo(count, fromHour, toHour, minSpacingMinutes, budget, failures, scheduledIds);
    }

    /**
     * {@code skipSourceClaim=true} — dev canary only, see {@link AiPostBundleService#generateAndHoldResult(
     * Persona, String, String, String, java.time.Instant, String, java.util.Set, boolean)}.
     */
    public FillResult fillSolo(int count, int fromHour, int toHour, long minSpacingMinutes,
                                LlmCallBudget budget, boolean skipSourceClaim) {
        List<NightlySlotFailure> failures = new ArrayList<>();
        List<String> scheduledIds = new ArrayList<>();
        return fillSolo(count, fromHour, toHour, minSpacingMinutes, budget, failures, scheduledIds, skipSourceClaim);
    }

    FillResult fillSolo(int count, int fromHour, int toHour, long minSpacingMinutes,
                        LlmCallBudget budget, List<NightlySlotFailure> failures, List<String> scheduledIds) {
        return fillSolo(count, fromHour, toHour, minSpacingMinutes, budget, failures, scheduledIds, false);
    }

    FillResult fillSolo(int count, int fromHour, int toHour, long minSpacingMinutes,
                        LlmCallBudget budget, List<NightlySlotFailure> failures, List<String> scheduledIds,
                        boolean skipSourceClaim) {
        int need = Math.max(0, Math.min(count, 100));
        if (need == 0) {
            return new FillResult(0, 0, 0, budget.used(), budget.max(), 0, 0, scheduledIds, failures, null, 0);
        }
        List<Persona> active = new ArrayList<>(personaRepository.findByActiveTrue());
        if (active.isEmpty()) {
            failures.add(new NightlySlotFailure("solo", "-", "-", "-", HoldResult.Outcome.GENERATION_SKIPPED,
                    "outcome=GENERATION_SKIPPED source=- plaza=- persona=- exampleId=- llmInvoked=false 활성 페르소나 없음"));
            log.warn("[nightly-fill] solo skipped: no active personas");
            return new FillResult(need, 0, 0, budget.used(), budget.max(), 0, 0, scheduledIds, failures, "활성 페르소나 없음", 0);
        }

        Random rng = new Random();
        List<String> sources = SourceMixPlanner.planSources(need, rng);
        List<Instant> slots;
        try {
            slots = sampleSlots(need, fromHour, toHour, minSpacingMinutes, rng);
        } catch (IllegalArgumentException e) {
            return new FillResult(need, 0, 0, budget.used(), budget.max(), 0, 0, scheduledIds, failures,
                    "슬롯 샘플링 실패: " + e.getMessage(), 0);
        }
        return fillSoloWithPlan(need, sources, slots, new ArrayList<>(active), budget, failures, scheduledIds, rng,
                skipSourceClaim);
    }

    FillResult fillSoloWithPlan(int need, List<String> sources, List<Instant> slots, List<Persona> pool,
                                LlmCallBudget budget, List<NightlySlotFailure> failures,
                                List<String> scheduledIds, Random rng) {
        return fillSoloWithPlan(need, sources, slots, pool, budget, failures, scheduledIds, rng, false);
    }

    FillResult fillSoloWithPlan(int need, List<String> sources, List<Instant> slots, List<Persona> pool,
                                LlmCallBudget budget, List<NightlySlotFailure> failures,
                                List<String> scheduledIds, Random rng, boolean skipSourceClaim) {
        // Precompute empty (source, plaza) pairs to avoid doomed claim attempts
        Set<String> emptyPlazaSources = precomputeEmptyPairs();
        Set<Long> skipExampleIds = new HashSet<>();
        int attempted = 0;
        int soloSaved = 0;
        int skipped = 0;

        // WP3 계약 5: 카테고리는 CategoryMixPlanner가 슬롯 단위로 고정 배정한다(voice_type 조건
        // 삭제). 작성자는 계약 6 가중 추첨(PersonaLottery)으로 뽑는다.
        List<CategoryMixPlanner.Slot> categorySlots = CategoryMixPlanner.plan(need, rng);
        for (int i = 0; i < need; i++) {
            if (!budget.hasRemaining()) {
                failures.add(unfilled("LLM cap reached before slot " + i, sources.get(i)));
                continue;
            }
            String preferred = sources.get(i);
            Instant slot = slots.get(i);
            String category = i < categorySlots.size() ? categorySlots.get(i).category() : CategoryMixPlanner.WORK;
            SlotSave save = tryFillOneSlot(i, preferred, category, slot, pool, rng, budget, emptyPlazaSources,
                    skipExampleIds, failures, skipSourceClaim);
            attempted += save.attempted();
            skipped += save.skipped();
            if (save.savedId() != null) {
                soloSaved++;
                scheduledIds.add(save.savedId());
            } else if (save.lastFailure() == null) {
                failures.add(unfilled("could not save slot " + i + " preferred=" + preferred, preferred));
            }
        }

        log.info("[nightly-fill] solo attempted={} saved={} need={} llm={}/{} skipped={}",
                attempted, soloSaved, need, budget.used(), budget.max(), skipped);
        return new FillResult(need, soloSaved, attempted, budget.used(), budget.max(), 0, soloSaved,
                scheduledIds, failures, null, skipped);
    }

    /**
     * Precompute (source, plaza) pairs with zero claimable inventory.
     * Avoids burning retry attempts on guaranteed CLAIM_EMPTY outcomes.
     * Uses ML service to query available counts for all source/plaza combinations.
     * On error/network failure, conservatively assumes inventory exists.
     * When familyPlazaGenerationEnabled=false, FAMILY is excluded (will never be requested).
     */
    private Set<String> precomputeEmptyPairs() {
        Set<String> empty = new HashSet<>();
        String[] sources = {SourceMixPlanner.SOURCE_BLIND, SourceMixPlanner.SOURCE_NATEPAN};
        // When FAMILY generation is disabled, exclude it from inventory checks
        boolean includeFamilyInPrecompute = properties.isFamilyPlazaGenerationEnabled();
        List<String> plazaList = new ArrayList<>();
        plazaList.add("MARRIED");
        plazaList.add("COUPLE");
        plazaList.add("WORK");
        if (includeFamilyInPrecompute) {
            plazaList.add("FAMILY");
        }
        plazaList.add("FRIEND");
        plazaList.add("OTHER");
        String[] plazas = plazaList.toArray(new String[0]);

        for (String source : sources) {
            for (String plaza : plazas) {
                try {
                    int count = aiUserMlClient.getAvailableCount(source, plaza, 14);
                    // Only skip if count is exactly 0 (not -1, which means error/unknown)
                    if (count == 0) {
                        String key = source + "|" + plaza;
                        empty.add(key);
                        log.info("[nightly-fill] precompute empty source={} plaza={}", source, plaza);
                    }
                } catch (Exception e) {
                    log.debug("[nightly-fill] precompute error (conservatively assume non-empty) source={} plaza={} error={}",
                            source, plaza, e.getMessage());
                    // On exception, don't add to empty set; let normal retry logic handle it
                }
            }
        }
        return empty;
    }

    /**
     * WP3: 슬롯 카테고리는 고정(CategoryMixPlanner)이다. 소스(blind/natepan)는 콘텐츠 claim
     * 용도로만 재시도하고, 작성자는 소스가 아니라 카테고리 기준 가중 추첨(계약 6)으로 뽑는다 —
     * 같은 슬롯에서 실패 시 최대 3회 다른 추첨자로 재시도한다.
     */
    private SlotSave tryFillOneSlot(int slotIndex, String preferredSource, String category, Instant slot,
                                    List<Persona> pool, Random rng, LlmCallBudget budget,
                                    Set<String> emptyPlazaSources, Set<Long> skipExampleIds,
                                    List<NightlySlotFailure> failures, boolean skipSourceClaim) {
        int attempted = 0;
        int skipped = 0;
        List<String> sourceOrder = sourceRetryOrder(preferredSource);
        for (String source : sourceOrder) {
            if (!budget.hasRemaining()) break;
            String emptyKey = source + "|" + category;
            if (emptyPlazaSources.contains(emptyKey)) {
                skipped++;
                log.info("[nightly-fill] SKIP_NO_INVENTORY slot={} source={} plaza={}",
                        slotIndex, source, category);
                continue;
            }
            List<Persona> tried = new ArrayList<>();
            for (int authorAttempt = 0; authorAttempt < 3 && budget.hasRemaining(); authorAttempt++) {
                List<Persona> candidatePool = new ArrayList<>(pool);
                candidatePool.removeAll(tried);
                List<Persona> drawn = personaLottery.drawAuthors(candidatePool, category, 1, rng);
                if (drawn.isEmpty()) {
                    log.warn("[nightly-fill] no eligible persona for category={} slot={}", category, slotIndex);
                    break;
                }
                Persona persona = drawn.get(0);
                tried.add(persona);
                int inner = 0;
                while (budget.hasRemaining() && inner < 8) {
                    inner++;
                    String corrId = "nightly-hold-" + persona.getId() + "-s" + slotIndex + "-a" + attempted;
                    HoldResult result = skipSourceClaim
                            ? aiPostBundleService.generateAndHoldResult(
                                    persona, category, null, corrId, slot, source, skipExampleIds, true)
                            : aiPostBundleService.generateAndHoldResult(
                                    persona, category, null, corrId, slot, source, skipExampleIds);
                    attempted++;
                    log.info("[nightly-fill] attempt slot={} {}", slotIndex, result.detailedReason());
                    if (result.outcome() == HoldResult.Outcome.CLAIM_EMPTY) {
                        emptyPlazaSources.add(emptyKey);
                        failures.add(NightlySlotFailure.fromHold("solo", result));
                        break;
                    }
                    if (result.outcome() == HoldResult.Outcome.SAME_EXAMPLE) {
                        if (result.exampleId() != null) {
                            skipExampleIds.add(result.exampleId());
                        }
                        failures.add(NightlySlotFailure.fromHold("solo", result));
                        continue;
                    }
                    if (result.outcome() == HoldResult.Outcome.GENERATION_SKIPPED) {
                        failures.add(NightlySlotFailure.fromHold("solo", result));
                        if (result.detail() != null && result.detail().contains("provider is OFF")) {
                            return new SlotSave(attempted, null, result, skipped);
                        }
                        break;
                    }
                    if (result.llmInvoked()) {
                        budget.consume();
                    }
                    if (result.exampleId() != null
                            && result.outcome() != HoldResult.Outcome.SAVED) {
                        skipExampleIds.add(result.exampleId());
                    }
                    if (result.outcome() == HoldResult.Outcome.SAVED) {
                        pool.remove(persona);
                        Optional<AiScheduledPost> row = result.saved();
                        return new SlotSave(attempted, row.map(AiScheduledPost::getId).orElse(null), result, skipped);
                    }
                    failures.add(NightlySlotFailure.fromHold("solo", result));
                }
                if (emptyPlazaSources.contains(emptyKey)) break;
            }
        }
        return new SlotSave(attempted, null, null, skipped);
    }

    static List<String> sourceRetryOrder(String preferredSource) {
        String pref = preferredSource == null ? SourceMixPlanner.SOURCE_BLIND
                : preferredSource.trim().toLowerCase();
        String alt = SourceMixPlanner.SOURCE_BLIND.equals(pref)
                ? SourceMixPlanner.SOURCE_NATEPAN : SourceMixPlanner.SOURCE_BLIND;
        if (!SourceMixPlanner.SOURCE_BLIND.equals(pref) && !SourceMixPlanner.SOURCE_NATEPAN.equals(pref)) {
            pref = SourceMixPlanner.SOURCE_BLIND;
            alt = SourceMixPlanner.SOURCE_NATEPAN;
        }
        return List.of(pref, alt);
    }

    static int pairedCountFor(int n, double share) {
        if (n <= 0) return 0;
        if (Double.isNaN(share) || share <= 0) return 0;
        int p = (int) Math.ceil(n * share);
        if (p < 1) p = 1;
        return Math.min(p, n);
    }

    private List<Instant> sampleSlots(int n, int fromHour, int toHour, long minSpacingMinutes, Random rng) {
        ZoneId kst = ActivityCurve.KST;
        LocalDate today = LocalDate.now(kst);
        Instant from = Instant.now().isAfter(today.atStartOfDay(kst).plusHours(fromHour).toInstant())
                ? Instant.now() : today.atStartOfDay(kst).plusHours(fromHour).toInstant();
        Instant to = today.atStartOfDay(kst).plusHours(toHour).toInstant();
        return ActivityCurve.sampleFutureInstants(from, to, n,
                properties.getThreadPlan().getKstHourlyHumanWeights(),
                Duration.ofMinutes(minSpacingMinutes), rng);
    }

    private static NightlySlotFailure unfilled(String detail, String source) {
        return new NightlySlotFailure("solo", source, "-", "-", HoldResult.Outcome.CLAIM_EMPTY,
                "outcome=UNFILLED source=" + source + " plaza=- persona=- exampleId=- llmInvoked=false " + detail);
    }

    private record SlotSave(int attempted, String savedId, HoldResult lastFailure, int skipped) {
        SlotSave(int attempted, String savedId, HoldResult lastFailure) {
            this(attempted, savedId, lastFailure, 0);
        }
    }
}
