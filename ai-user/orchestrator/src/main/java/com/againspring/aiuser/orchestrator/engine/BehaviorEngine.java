package com.againspring.aiuser.orchestrator.engine;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.client.dto.PostFeedPage;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.AiUserRuntime;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.enums.ActionType;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserRuntimeRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.service.ActionTypeQuotaService;
import com.againspring.aiuser.orchestrator.service.DailyPostQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI 유저 행동 엔진 — cron tick의 진입점.
 * top-down: kill-switch → 볼륨 쿼터 → 페르소나 선택 → 행동 계획 → jitter 실행.
 * LLM 미사용.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorEngine {

    private final AiUserRuntimeRepository runtimeRepo;
    private final PersonaRepository personaRepo;
    private final BackendBotClient backendBotClient;
    private final VolumeQuotaCalculator quotaCalc;
    private final PersonaSelector personaSelector;
    private final ActionPlanner actionPlanner;
    private final Jitter jitter;
    private final ViewDispatcher viewDispatcher;
    private final OrchestratorProperties props;
    private final com.againspring.aiuser.orchestrator.service.PostAnalysisService postAnalysisService;
    private final DailyPostQuotaService postQuotaService;
    private final AiUserGenerationConfigRepository generationConfigRepository;
    private final ActionTypeQuotaService actionTypeQuotaService;

    // InteractionScanner and ActionExecutor injected lazily to avoid circular dependency issues
    @Autowired(required = false)
    private com.againspring.aiuser.orchestrator.discovery.InteractionScanner interactionScanner;

    @Autowired(required = false)
    private com.againspring.aiuser.orchestrator.task.ActionExecutor actionExecutor;

    /** 목표 게시글 1개당 필요한 총 행동 수 (vote+comment+like+reply+comment_like+post+여유). */
    private static final int ACTIONS_PER_TARGET_POST = 20;

    public void tick() {
        if (!props.isEnabled()) {
            log.debug("BehaviorEngine tick skipped: AI_USER_ENABLED=false");
            return;
        }

        // 1. Kill-switch check
        AiUserRuntime rt = runtimeRepo.findById(1).orElse(null);
        if (rt == null || !rt.isEnabled()) {
            log.debug("BehaviorEngine tick skipped: kill-switch OFF or runtime not initialized");
            return;
        }

        // 1.5. daily_global_cap = 5개 목표 합 × 1.1 (admin UI 단일 진실원)
        // generation_config 부재/합 0이면 env var fallback.
        AiUserGenerationConfig genConfig = generationConfigRepository.findById(1).orElse(null);
        if (genConfig != null) {
            int targetSum = genConfig.getTargetPosts() + genConfig.getTargetComments()
                          + genConfig.getTargetReplies() + genConfig.getTargetVotes()
                          + genConfig.getTargetLikes();
            if (targetSum > 0) {
                int autoCap = (int) Math.ceil(targetSum * 1.1);
                if (rt.getDailyGlobalCap() != autoCap) {
                    log.info("Daily cap 갱신: targets합={} → cap={} (이전={})", targetSum, autoCap, rt.getDailyGlobalCap());
                    rt.setDailyGlobalCap(autoCap);
                    runtimeRepo.save(rt);
                }
            } else if (props.getPersonaTarget() > 0) {
                // fallback: config 목표가 모두 0일 때만 env var 사용
                int fallbackCap = props.getPersonaTarget() * ACTIONS_PER_TARGET_POST;
                if (rt.getDailyGlobalCap() != fallbackCap) {
                    log.warn("generation_config 목표 모두 0 — env var fallback cap={}", fallbackCap);
                    rt.setDailyGlobalCap(fallbackCap);
                    runtimeRepo.save(rt);
                }
            }
        }

        // 2. Day bucket rollover
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        if (!today.equals(rt.getDayBucket())) {
            rt.setActionsToday(0);
            rt.setDayBucket(today);
            log.info("Day bucket rolled over: new day={}", today);
        }

        // 3. Daily cap check
        if (rt.getActionsToday() >= rt.getDailyGlobalCap()) {
            log.info("Daily global cap reached ({}/{}). Skipping tick.",
                rt.getActionsToday(), rt.getDailyGlobalCap());
            return;
        }

        // 4. Volume quota for this tick
        int currentHour = LocalTime.now(ZoneId.of("Asia/Seoul")).getHour();
        double hourWeight = props.isForceActive() ? 1.0
            : quotaCalc.circadianWeight(currentHour, null);
        int ticksPerDay = quotaCalc.estimateTicksPerDay(props.getTickCron());
        int remaining = rt.getDailyGlobalCap() - rt.getActionsToday();
        int budget = props.isForceActive()
            ? Math.max(3, quotaCalc.calculate(rt.getDailyGlobalCap(), ticksPerDay, hourWeight, remaining))
            : quotaCalc.calculate(rt.getDailyGlobalCap(), ticksPerDay, hourWeight, remaining);

        // 4.5 타입별 결손 스냅샷 — 틱 시작 시 1회 집계
        // 틱 내 계획 시 in-memory 차감 → 행동당 DB 쿼리 없음
        int remainingHours = Math.max(1, 24 - currentHour);
        int remainingTicksEst = Math.max(1, ticksPerDay * remainingHours / 24);
        Map<ActionType, ActionTypeQuotaService.TypeQuota> quotaSnapshot =
            (genConfig != null)
                ? actionTypeQuotaService.computeToday(genConfig)
                : Collections.emptyMap();

        // 틱당 타입별 예산 = deficit × hourWeight × 2.0 / remainingTicksEst (결손 분산)
        // 단, budget=0이면 전체 skip 이미 처리됨
        Map<ActionType, Integer> tickTypeBudget = computeTickTypeBudgetForTest(
            quotaSnapshot, hourWeight, remainingTicksEst);

        log.debug("Tick: hour={} hourWeight={} budget={} remaining={} forceActive={} hasQuota={}",
            currentHour, String.format("%.2f", hourWeight), budget, remaining,
            props.isForceActive(), !quotaSnapshot.isEmpty());

        if (budget <= 0) {
            log.debug("Zero budget for this tick. Skipping.");
            // Save dayBucket even when skipping so it doesn't repeatedly log rollover
            runtimeRepo.save(rt);
            return;
        }

        // 5. Fetch feed — 여러 페이지 병합으로 충분한 게시물 확보
        List<PostDto> feedPosts = new java.util.ArrayList<>();
        for (int p = 0; p < 5; p++) {
            java.util.List<PostDto> page = backendBotClient.getFeed(p, 20)
                .map(PostFeedPage::getContent)
                .orElse(Collections.emptyList());
            feedPosts.addAll(page);
            if (page.size() < 20) break; // 마지막 페이지면 종료
        }

        // 5.5 콘텐츠 인식 결정용 지연 분석 — 틱당 budget 제한으로 토큰 통제.
        // 신규(미분석) 글만, 최대 budget건. 캐시되면 이후 좋아요·투표는 LLM 0.
        if (props.isContentAwareEnabled()) {
            int budgetAnalyze = props.getAnalysisBudgetPerTick();
            int attempts = 0;
            for (PostDto post : feedPosts) {
                if (attempts >= budgetAnalyze) break;            // LLM 호출 상한 (성공·실패 무관)
                if (post.getId() == null) continue;
                if (postAnalysisService.getCached(post.getId()) != null) continue; // 이미 분석됨
                attempts++;
                postAnalysisService.analyzeAndSave(post);
            }
            if (attempts > 0) {
                log.info("Content analysis: {} new post(s) attempted (budget {})", attempts, budgetAnalyze);
            }
        }

        // 6. Get reply targets
        List<ReplyTarget> replyTargets = (interactionScanner != null)
            ? interactionScanner.findReplyTargets()
            : Collections.emptyList();

        // 7. Get active personas
        List<Persona> activePersonas = personaRepo.findByActiveTrue();
        if (activePersonas.isEmpty()) {
            log.info("No active personas. Skipping tick.");
            return;
        }

        // 8. 결손 가중 추첨으로 행동 타입 선택 → planForType() 실행
        int actionsPlanned = 0;
        // tickTypeBudget의 in-memory 가변 복사본 (틱 내 차감용)
        Map<ActionType, Integer> remaining_type_budget = new EnumMap<>(tickTypeBudget);
        // quota가 비어있으면 전체 budget을 균등 배분 (legacy / 초기 설정 없음)
        boolean hasQuota = !remaining_type_budget.isEmpty()
                           && remaining_type_budget.values().stream().anyMatch(v -> v > 0);

        for (int i = 0; i < budget * 3 && actionsPlanned < budget; i++) {
            Optional<Persona> pOpt = personaSelector.pick(activePersonas, currentHour);
            if (pOpt.isEmpty()) break;
            Persona persona = pOpt.get();
            if (personaSelector.isOnCooldown(persona)) continue;

            // 타입 선택: 결손 가중 추첨
            ActionType selectedType = hasQuota
                ? pickTypeByDeficit(remaining_type_budget, persona, replyTargets, feedPosts)
                : pickTypeLegacy(persona, replyTargets, feedPosts); // fallback (초기 config 없음)

            if (selectedType == null) continue;

            Optional<PlannedAction> actionOpt = actionExecutor != null
                ? actionPlanner.planForType(persona, selectedType, feedPosts, replyTargets)
                : Optional.empty();

            if (actionOpt.isEmpty()) {
                // 해당 타입 실행 불가 — 틱 예산 소모 없이 시도 횟수만 증가
                remaining_type_budget.computeIfPresent(selectedType, (k, v) -> Math.max(0, v - 1));
                continue;
            }

            if (actionExecutor != null) {
                final Persona finalPersona = persona;
                final PlannedAction finalAction = actionOpt.get();
                // Phase 4: REPLY는 현실 지연(5~60분)으로 별도 스케줄
                if (finalAction.type() == ActionType.REPLY) {
                    jitter.scheduleReplyWithDelay(() -> actionExecutor.execute(finalPersona, finalAction));
                } else {
                    jitter.scheduleWithinTick(() -> actionExecutor.execute(finalPersona, finalAction));
                }
            }
            remaining_type_budget.computeIfPresent(selectedType, (k, v) -> Math.max(0, v - 1));
            actionsPlanned++;
        }

        // 9. Update runtime counter
        rt.setActionsToday(rt.getActionsToday() + actionsPlanned);
        rt.setUpdatedAt(Instant.now());
        runtimeRepo.save(rt);

        // 10. Dispatch VIEW actions from daily quotas
        int viewsDispatched = viewDispatcher.dispatchViews();

        log.info("Tick complete: planned={} views={} actionsToday={}/{} hour={}",
            actionsPlanned, viewsDispatched,
            rt.getActionsToday(), rt.getDailyGlobalCap(), currentHour);
    }

    // ══════════════════════ Helper Methods ══════════════════════

    /**
     * Computes per-tick budget for each action type based on deficit.
     * Package-private for testing.
     */
    Map<ActionType, Integer> computeTickTypeBudgetForTest(
            Map<ActionType, ActionTypeQuotaService.TypeQuota> quotas,
            double hourWeight, int remainingTicksEst) {
        Map<ActionType, Integer> result = new EnumMap<>(ActionType.class);
        for (Map.Entry<ActionType, ActionTypeQuotaService.TypeQuota> e : quotas.entrySet()) {
            ActionTypeQuotaService.TypeQuota q = e.getValue();
            if (q.deficit() <= 0) {
                result.put(e.getKey(), 0);
            } else {
                double expected = (double) q.deficit() * hourWeight * 2.0 / Math.max(remainingTicksEst, 1);
                result.put(e.getKey(), quotaCalc.stochasticRound(expected));
            }
        }
        return result;
    }

    /**
     * Pick action type by deficit-weighted distribution.
     * Returns null if no eligible type found.
     */
    private ActionType pickTypeByDeficit(
            Map<ActionType, Integer> budgets,
            Persona persona,
            List<ReplyTarget> replyTargets,
            List<PostDto> feedPosts) {

        // 실행 불가 타입 사전 필터
        boolean hasReplies = replyTargets.stream()
            .anyMatch(rt -> !persona.getId().equals(rt.commentAuthorId()));
        boolean hasFeed = !feedPosts.isEmpty();
        boolean isHeavy = "HEAVY".equals(persona.getTier());

        // 실행 가능한 타입만 후보로
        List<Map.Entry<ActionType, Integer>> candidates = budgets.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .filter(e -> switch (e.getKey()) {
                case REPLY -> hasReplies;
                case VOTE, LIKE, COMMENT, COMMENT_LIKE -> hasFeed;
                case POST -> isHeavy;
                default -> false;
            })
            .collect(Collectors.toList());

        if (candidates.isEmpty()) return null;

        // 결손 합계 기준 가중 추첨
        int total = candidates.stream().mapToInt(Map.Entry::getValue).sum();
        double r = Math.random() * total;
        double cumul = 0;
        for (Map.Entry<ActionType, Integer> entry : candidates) {
            cumul += entry.getValue();
            if (r < cumul) return entry.getKey();
        }
        return candidates.get(candidates.size() - 1).getKey();
    }

    /**
     * Legacy fallback: pick type by simple probabilities.
     * Used when quota configuration is not available.
     */
    private ActionType pickTypeLegacy(Persona persona, List<ReplyTarget> replyTargets, List<PostDto> feedPosts) {
        boolean hasReplies = replyTargets.stream().anyMatch(rt -> !persona.getId().equals(rt.commentAuthorId()));
        boolean hasFeed = !feedPosts.isEmpty();
        // Simple priority: REPLY > VOTE > LIKE > COMMENT > POST
        if (hasReplies && Math.random() < 0.15) return ActionType.REPLY;
        if (hasFeed && Math.random() < 0.35) return ActionType.VOTE;
        if (hasFeed && Math.random() < 0.50) return ActionType.LIKE;
        if (hasFeed && Math.random() < 0.25) return ActionType.COMMENT;
        if ("HEAVY".equals(persona.getTier())) return ActionType.POST;
        return hasFeed ? ActionType.LIKE : null;
    }
}
