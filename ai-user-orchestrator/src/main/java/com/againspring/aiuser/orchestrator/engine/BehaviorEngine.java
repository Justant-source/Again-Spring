package com.againspring.aiuser.orchestrator.engine;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.client.dto.PostFeedPage;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserRuntime;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.AiUserRuntimeRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
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
    private final OrchestratorProperties props;

    // InteractionScanner and ActionExecutor injected lazily to avoid circular dependency issues
    @Autowired(required = false)
    private com.againspring.aiuser.orchestrator.discovery.InteractionScanner interactionScanner;

    @Autowired(required = false)
    private com.againspring.aiuser.orchestrator.task.ActionExecutor actionExecutor;

    public void tick() {
        // 1. Kill-switch check
        AiUserRuntime rt = runtimeRepo.findById(1).orElse(null);
        if (rt == null || !rt.isEnabled()) {
            log.debug("BehaviorEngine tick skipped: kill-switch OFF or runtime not initialized");
            return;
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
        double hourWeight = quotaCalc.circadianWeight(currentHour, null); // global curve
        int ticksPerDay = quotaCalc.estimateTicksPerDay(props.getTickCron());
        int remaining = rt.getDailyGlobalCap() - rt.getActionsToday();
        int budget = quotaCalc.calculate(rt.getDailyGlobalCap(), ticksPerDay, hourWeight, remaining);

        log.debug("Tick: hour={} hourWeight={} budget={} remaining={}",
            currentHour, String.format("%.2f", hourWeight), budget, remaining);

        if (budget <= 0) {
            log.debug("Zero budget for this tick. Skipping.");
            return;
        }

        // 5. Fetch feed
        List<PostDto> feedPosts = backendBotClient.getFeed(0, 20)
            .map(PostFeedPage::getContent)
            .orElse(Collections.emptyList());

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
        for (int i = 0; i < budget * 3 && actionsPlanned < budget; i++) {
            // pick a persona (try up to 3x budget to find non-cooldown ones)
            Optional<Persona> pOpt = personaSelector.pick(activePersonas, currentHour);
            if (pOpt.isEmpty()) break;
            Persona persona = pOpt.get();

            if (personaSelector.isOnCooldown(persona)) continue;

            Optional<PlannedAction> actionOpt = actionPlanner.plan(persona, feedPosts, replyTargets);
            if (actionOpt.isEmpty()) continue;

            PlannedAction action = actionOpt.get();
            if (actionExecutor != null) {
                final Persona finalPersona = persona;
                final PlannedAction finalAction = action;
                jitter.scheduleWithinTick(() -> actionExecutor.execute(finalPersona, finalAction));
            }
            actionsPlanned++;
        }

        // 9. Update runtime counter
        rt.setActionsToday(rt.getActionsToday() + actionsPlanned);
        rt.setUpdatedAt(Instant.now());
        runtimeRepo.save(rt);

        log.info("Tick complete: planned={} actionsToday={}/{} hour={}",
            actionsPlanned, rt.getActionsToday(), rt.getDailyGlobalCap(), currentHour);
    }
}
