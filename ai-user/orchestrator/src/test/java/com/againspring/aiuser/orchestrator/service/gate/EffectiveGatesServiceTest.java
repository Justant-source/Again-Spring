package com.againspring.aiuser.orchestrator.service.gate;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AiUserGenerationConfig has no Lombok setter (JPA @Immutable read-only mapping), so the
 * config row is mocked rather than constructed — see ScheduledPostPublisherGateTest.
 */
class EffectiveGatesServiceTest {

    private static OrchestratorProperties enabledProps() {
        OrchestratorProperties props = new OrchestratorProperties();
        props.setEnabled(true);
        props.getThreadPlan().setEnabled(true);
        props.getThreadPlan().setPublisherEnabled(true);
        return props;
    }

    @Test
    void killSwitchBlocksBothAndIsListedAsReason() {
        OrchestratorProperties props = enabledProps();
        AiUserGenerationConfigRepository repo = mock(AiUserGenerationConfigRepository.class);
        AiUserGenerationConfig cfg = mock(AiUserGenerationConfig.class);
        when(cfg.isAiUserKillSwitch()).thenReturn(true);
        when(cfg.getProviderAiPostBundle()).thenReturn("CLAUDE");
        when(repo.findById(1)).thenReturn(Optional.of(cfg));
        LlmGenerationGateService gate = mock(LlmGenerationGateService.class);
        when(gate.isHeld()).thenReturn(false);

        Map<String, Object> out = new EffectiveGatesService(props, repo, gate).resolve();

        assertThat(out.get("generationAllowed")).isEqualTo(false);
        assertThat(out.get("publishingAllowed")).isEqualTo(false);
        assertThat(reasons(out)).contains("ai_user_kill_switch=true");
        assertThat(gates(out)).hasSizeGreaterThanOrEqualTo(14);
    }

    @Test
    void allowedWhenEverythingOpenAndConfigRowPresent() {
        OrchestratorProperties props = enabledProps();
        AiUserGenerationConfigRepository repo = mock(AiUserGenerationConfigRepository.class);
        AiUserGenerationConfig cfg = mock(AiUserGenerationConfig.class);
        when(cfg.isAiUserKillSwitch()).thenReturn(false);
        when(cfg.isScheduleExecutionPaused()).thenReturn(false);
        when(cfg.getProviderAiPostBundle()).thenReturn("CLAUDE");
        when(cfg.getProviderHumanPostPlan()).thenReturn("CLAUDE");
        when(cfg.getProviderHumanInteraction()).thenReturn("CLAUDE");
        when(cfg.getProviderVoteLike()).thenReturn("CLAUDE");
        when(cfg.getUpdatedBy()).thenReturn("admin");
        when(repo.findById(1)).thenReturn(Optional.of(cfg));
        LlmGenerationGateService gate = mock(LlmGenerationGateService.class);
        when(gate.isHeld()).thenReturn(false);

        Map<String, Object> out = new EffectiveGatesService(props, repo, gate).resolve();

        assertThat(out.get("generationAllowed")).isEqualTo(true);
        assertThat(out.get("publishingAllowed")).isEqualTo(true);
        assertThat(reasons(out)).isEmpty();
    }

    @Test
    void llmGateHeldBlocksGenerationOnlyNotPublishing() {
        OrchestratorProperties props = enabledProps();
        AiUserGenerationConfigRepository repo = mock(AiUserGenerationConfigRepository.class);
        AiUserGenerationConfig cfg = mock(AiUserGenerationConfig.class);
        when(cfg.getProviderAiPostBundle()).thenReturn("CLAUDE");
        when(repo.findById(1)).thenReturn(Optional.of(cfg));
        LlmGenerationGateService gate = mock(LlmGenerationGateService.class);
        when(gate.isHeld()).thenReturn(true);

        Map<String, Object> out = new EffectiveGatesService(props, repo, gate).resolve();

        assertThat(out.get("generationAllowed")).isEqualTo(false);
        assertThat(out.get("publishingAllowed")).isEqualTo(true);
        assertThat(reasons(out)).contains("llm_generation_gate=HELD");
    }

    @Test
    void missingConfigRowIsReasonNotException() {
        OrchestratorProperties props = enabledProps();
        AiUserGenerationConfigRepository repo = mock(AiUserGenerationConfigRepository.class);
        when(repo.findById(1)).thenReturn(Optional.empty());
        LlmGenerationGateService gate = mock(LlmGenerationGateService.class);
        when(gate.isHeld()).thenReturn(false);

        Map<String, Object> out = new EffectiveGatesService(props, repo, gate).resolve();

        assertThat(out.get("generationAllowed")).isEqualTo(false);
        assertThat(out.get("publishingAllowed")).isEqualTo(false);
        assertThat(reasons(out)).contains("ai_user_generation_config row missing");
    }

    @SuppressWarnings("unchecked")
    private static List<String> reasons(Map<String, Object> out) {
        return (List<String>) out.get("reasons");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> gates(Map<String, Object> out) {
        return (List<Map<String, Object>>) out.get("gates");
    }
}
