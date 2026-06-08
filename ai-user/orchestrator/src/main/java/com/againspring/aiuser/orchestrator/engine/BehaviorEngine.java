package com.againspring.aiuser.orchestrator.engine;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.client.dto.PostFeedPage;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.AiUserRuntime;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserRuntimeRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
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
import java.util.List;
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

    // InteractionScanner and ActionExecutor injected lazily to avoid circular dependency issues
    @Autowired(required = false)
    private com.againspring.aiuser.orchestrator.discovery.InteractionScanner interactionScanner;

    @Autowired(required = false)
    private com.againspring.aiuser.orchestrator.task.ActionExecutor actionExecutor;

    /** 목표 게시글 1개당 필요한 총 행동 수 (vote+comment+like+reply+comment_like+post+여유). */
    private static final int ACTIONS_PER_TARGET_POST = 20;

    public void tick() {
        // 1. Kill-switch check
        AiUserRuntime rt = runtimeRepo.findById(1).orElse(null);
        if (rt == null || !rt.isEnabled()) {
            log.debug("BehaviorEngine tick skipped: kill-switch OFF or runtime not initialized");
            return;
        }

        // 1.5. personaTarget 기반 daily cap 자동 동기화
        // AI_USER_PERSONA_TARGET 변경(+재시작)만으로 cap이 자동 반영됨.
        if (props.getPersonaTarget() > 0) {
            int autoCap = props.getPersonaTarget() * ACTIONS_PER_TARGET_POST;
            if (rt.getDailyGlobalCap() != autoCap) {
                log.info("Daily cap auto-sync: {}posts × {} = {} (was {})",
                    props.getPersonaTarget(), ACTIONS_PER_TARGET_POST, autoCap, rt.getDailyGlobalCap());
                rt.setDailyGlobalCap(autoCap);
                runtimeRepo.save(rt);
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

        // 4.5 POST 쿼터 — targetPosts(관리자 설정)를 실제 글 발행 수로 시행
        int targetPosts = generationConfigRepository.findById(1)
            .map(AiUserGenerationConfig::getTargetPosts)
            .orElse(0);
        int remainingPosts = postQuotaService.remaining(targetPosts);
        // 남은 시간 기준으로 분산 — 전체 틱(1440)으로 나누면 확률이 너무 낮아 prod에서 목표 미달
        // 예) hour=17, remaining=46: 1440분모→prob=0.043(4.3%), 420분모→prob=0.147(14.7%)
        int remainingHours = Math.max(1, 24 - currentHour);
        int remainingTicksEst = Math.max(1, ticksPerDay * remainingHours / 24);
        int postBudgetThisTick = props.isForceActive()
            ? Math.max(1, quotaCalc.stochasticRound((double) remainingPosts / Math.max(ticksPerDay / 2, 1)))
            : quotaCalc.stochasticRound((double) remainingPosts * hourWeight * 2.0 / remainingTicksEst);

        log.debug("Tick: hour={} hourWeight={} budget={} remaining={} postsToday={}/{} postBudgetThisTick={} forceActive={}",
            currentHour, String.format("%.2f", hourWeight), budget, remaining,
            postQuotaService.postsCreatedToday(), targetPosts, postBudgetThisTick, props.isForceActive());

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

        // 8. Execute actions up to budget
        int actionsPlanned = 0;
        int postsPlannedThisTick = 0;
        for (int i = 0; i < budget * 3 && actionsPlanned < budget; i++) {
            // pick a persona (try up to 3x budget to find non-cooldown ones)
            Optional<Persona> pOpt = personaSelector.pick(activePersonas, currentHour);
            if (pOpt.isEmpty()) break;
            Persona persona = pOpt.get();

            if (personaSelector.isOnCooldown(persona)) continue;

            Optional<PlannedAction> actionOpt = actionPlanner.plan(persona, feedPosts, replyTargets, postBudgetThisTick - postsPlannedThisTick);
            if (actionOpt.isEmpty()) continue;

            PlannedAction action = actionOpt.get();
            if (action.type() == com.againspring.aiuser.orchestrator.domain.enums.ActionType.POST) {
                postsPlannedThisTick++;
            }
            if (actionExecutor != null) {
                final Persona finalPersona = persona;
                final PlannedAction finalAction = action;
                jitter.scheduleWithinTick(() -> actionExecutor.execute(finalPersona, finalAction));
            }
            actionsPlanned++;
        }

        // 8.5 POST 전용 패스 — ActionPlanner 누적확률 체인 문제 우회
        // 구조적 원인: REPLY(0.15)+VOTE(0.30)+LIKE(0.45)+COMMENT(0.20) = 1.10 > 1.0
        // rand∈[0,1] 이므로 COMMENT가 항상 먼저 캐치 → POST 코드 도달 불가.
        // 해결: postBudgetThisTick > 0일 때 HEAVY 페르소나를 직접 지명해 POST 예약.
        int postsScheduled = 0;
        if (postBudgetThisTick > 0 && actionExecutor != null) {
            List<Persona> heavyReady = activePersonas.stream()
                .filter(p -> "HEAVY".equals(p.getTier()))
                .filter(p -> !personaSelector.isOnCooldown(p))
                .collect(Collectors.toList());
            Collections.shuffle(heavyReady);
            for (Persona hp : heavyReady) {
                if (postsScheduled >= postBudgetThisTick) break;
                Optional<PlannedAction> postOpt = actionPlanner.planPost(hp);
                if (postOpt.isPresent()) {
                    postsScheduled++;
                    final Persona fhp = hp;
                    final PlannedAction fa = postOpt.get();
                    jitter.scheduleWithinTick(() -> actionExecutor.execute(fhp, fa));
                }
            }
            if (postsScheduled > 0) {
                log.debug("POST 전용 패스: {}개 예약 (postBudgetThisTick={})", postsScheduled, postBudgetThisTick);
            }
        }

        // 9. Update runtime counter
        rt.setActionsToday(rt.getActionsToday() + actionsPlanned + postsScheduled);
        rt.setUpdatedAt(Instant.now());
        runtimeRepo.save(rt);

        // 10. Dispatch VIEW actions from daily quotas
        int viewsDispatched = viewDispatcher.dispatchViews();

        log.info("Tick complete: planned={} postsScheduled={} views={} actionsToday={}/{} postsToday={}/{} hour={}",
            actionsPlanned, postsScheduled, viewsDispatched,
            rt.getActionsToday(), rt.getDailyGlobalCap(),
            postQuotaService.postsCreatedToday(), targetPosts, currentHour);
    }
}
