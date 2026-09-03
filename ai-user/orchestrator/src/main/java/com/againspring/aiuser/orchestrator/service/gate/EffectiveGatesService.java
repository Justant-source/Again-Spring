package com.againspring.aiuser.orchestrator.service.gate;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 모든 생성/발행 게이트(env·yml·DB에 흩어진 ~13종)를 한 번에 해석한다.
 * 관리자 화면의 "왜 (안) 나가나" 단일 진실 — {@code GET /admin/trigger/effective-gates}.
 *
 * <p>여기서 계산하는 generationAllowed/publishingAllowed는 각 소비자
 * ({@link com.againspring.aiuser.orchestrator.service.threadplan.ThreadPlanGenerationService},
 * {@link com.againspring.aiuser.orchestrator.service.threadplan.ThreadPlanPublisher},
 * {@link com.againspring.aiuser.orchestrator.service.threadplan.ScheduledPostPublisher},
 * {@link com.againspring.aiuser.orchestrator.service.threadplan.HumanReplyBatchService},
 * {@link com.againspring.aiuser.orchestrator.scheduler.PairedPostScheduler},
 * {@link com.againspring.aiuser.orchestrator.service.engagement.PlanEngagementDispatcher})가
 * 실제로 확인하는 게이트를 요약한 것이지, 각 소비자의 정확한 분기를 재현한 것은 아니다
 * (예: provider는 콘텐츠 타입별로 개별 확인되지만 여기선 "하나라도 OFF 아님"으로 뭉뚱그린다).
 *
 * <p>{@code config_updated_by} 게이트 행은 Task 5.3의 {@code stale}(nightly-batch 자동 갱신
 * 감지) 판정을 위한 자리다 — 지금은 값만 노출하고 판정은 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class EffectiveGatesService {

    private static final int CONFIG_ID = 1;

    private final OrchestratorProperties props;
    private final AiUserGenerationConfigRepository configRepository;
    private final LlmGenerationGateService llmGate;

    public Map<String, Object> resolve() {
        AiUserGenerationConfig cfg = configRepository.findById(CONFIG_ID).orElse(null);
        OrchestratorProperties.ThreadPlan tp = props.getThreadPlan();
        List<Map<String, Object>> gates = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        boolean enabled = props.isEnabled();
        boolean tpEnabled = tp.isEnabled();
        boolean kill = cfg != null && cfg.isAiUserKillSwitch();
        boolean paused = cfg != null && cfg.isScheduleExecutionPaused();
        boolean held = llmGate.isHeld();
        String pAi = cfg == null ? tp.getAiPostProvider() : nz(cfg.getProviderAiPostBundle());
        String pHuman = cfg == null ? tp.getHumanPlanProvider() : nz(cfg.getProviderHumanPostPlan());
        String pInter = cfg == null ? "OFF" : nz(cfg.getProviderHumanInteraction());
        String pVote = cfg == null ? "OFF" : nz(cfg.getProviderVoteLike());

        gate(gates, "AI_USER_ENABLED", "env", enabled, "all");
        gate(gates, "thread-plan.enabled", "yml", tpEnabled, "generation+publishing");
        gate(gates, "thread-plan.publisher-enabled", "yml", tp.isPublisherEnabled(), "comment publishing");
        gate(gates, "thread-plan.scheduled-post-publisher-enabled", "yml", tp.isScheduledPostPublisherEnabled(), "scheduled post publishing");
        gate(gates, "thread-plan.human-reply-batch-enabled", "yml", tp.isHumanReplyBatchEnabled(), "human reply");
        gate(gates, "paired-post.enabled", "yml", props.getPairedPost().isEnabled(), "paired posts");
        gate(gates, "engagement.enabled", "yml", tp.getEngagement().isEnabled(), "likes/views");
        gate(gates, "ai_user_kill_switch", "db", kill, "generation+publishing");
        gate(gates, "schedule_execution_paused", "db", paused, "publishing");
        gate(gates, "provider_ai_post_bundle", "db", pAi, "AI post generation");
        gate(gates, "provider_human_post_plan", "db", pHuman, "human post plan generation");
        gate(gates, "provider_human_interaction", "db", pInter, "human reply generation");
        gate(gates, "provider_vote_like", "db", pVote, "vote/like");
        gate(gates, "llm_generation_gate", "db", held ? "HELD" : "ACTIVE", "generation");
        // Task 5.3 자리: config_updated_by='nightly-batch'면 stale 후보 — 판정은 아직 안 함.
        gate(gates, "config_updated_by", "db", cfg == null ? null : cfg.getUpdatedBy(), "-");
        gate(gates, "config_row_present", "db", cfg != null, "generation+publishing (row 없으면 fail-closed)");

        if (!enabled) reasons.add("AI_USER_ENABLED=false");
        if (!tpEnabled) reasons.add("thread-plan.enabled=false");
        if (kill) reasons.add("ai_user_kill_switch=true");
        if (paused) reasons.add("schedule_execution_paused=true");
        if (held) reasons.add("llm_generation_gate=HELD");
        if (cfg == null) reasons.add("ai_user_generation_config row missing");
        boolean anyProvider = !"OFF".equalsIgnoreCase(pAi) || !"OFF".equalsIgnoreCase(pHuman) || !"OFF".equalsIgnoreCase(pInter);
        if (!anyProvider) reasons.add("all providers OFF");
        boolean anyPublisher = tp.isPublisherEnabled() || tp.isScheduledPostPublisherEnabled();
        if (!anyPublisher) reasons.add("no publisher enabled (yml)");

        boolean generationAllowed = enabled && tpEnabled && !kill && !held && cfg != null && anyProvider;
        boolean publishingAllowed = enabled && tpEnabled && !kill && !paused && cfg != null && anyPublisher;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generationAllowed", generationAllowed);
        out.put("publishingAllowed", publishingAllowed);
        out.put("reasons", reasons);
        out.put("gates", gates);
        return out;
    }

    private static String nz(String v) {
        return v == null || v.isBlank() ? "OFF" : v;
    }

    private static void gate(List<Map<String, Object>> gates, String name, String source, Object value, String blocks) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("name", name);
        g.put("source", source);
        g.put("value", value);
        g.put("blocks", blocks);
        gates.add(g);
    }
}
