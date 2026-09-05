package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 4 fix-round-1: EffectiveGatesService reports generationAllowed=false when
 * ai_user_kill_switch is on, but PairedPostScheduler.generateCall1 never read it — paired
 * generation kept running while the dashboard said "막힘". This locks in the fix: same config
 * row already fetched for the provider check must also gate on the kill switch.
 */
class PairedPostKillSwitchTest {

    private PairedPostScheduler scheduler;
    private AiUserGenerationConfigRepository generationConfigRepository;
    private com.againspring.aiuser.orchestrator.client.LlmAiUserClient llmClient;

    @BeforeEach
    void setUp() {
        OrchestratorProperties props = new OrchestratorProperties();
        props.setEnabled(true);
        props.getPairedPost().setEnabled(true);

        generationConfigRepository = mock(AiUserGenerationConfigRepository.class);
        llmClient = mock(com.againspring.aiuser.orchestrator.client.LlmAiUserClient.class);

        scheduler = new PairedPostScheduler(
                mock(com.againspring.aiuser.orchestrator.repository.PersonaRelationshipRepository.class),
                mock(com.againspring.aiuser.orchestrator.repository.PersonaRepository.class),
                llmClient,
                props,
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard.class),
                generationConfigRepository,
                mock(com.againspring.aiuser.orchestrator.service.DailyPostQuotaService.class),
                mock(com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.againspring.aiuser.orchestrator.service.threadplan.PlanPersonaMapper.class),
                mock(com.againspring.aiuser.orchestrator.service.threadplan.CandidateScheduleSupport.class),
                mock(com.againspring.aiuser.orchestrator.service.GenerationConfigSupport.class),
                mock(com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService.class),
                mock(com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache.class),
                mock(com.againspring.aiuser.orchestrator.service.persona.PersonaLottery.class),
                pairedSourceStoryResolverStub(),
                mock(com.againspring.aiuser.orchestrator.service.threadplan.AiPostBundleService.class),
                new com.againspring.aiuser.orchestrator.safety.SourceOverlapGuard());
    }

    private Persona author() {
        Persona p = mock(Persona.class);
        when(p.getId()).thenReturn("author-1");
        return p;
    }

    @Test
    void generateCall1_skipsWhenKillSwitchOn() {
        AiUserGenerationConfig config = mock(AiUserGenerationConfig.class);
        when(config.isAiUserKillSwitch()).thenReturn(true);
        when(config.getProviderAiPostBundle()).thenReturn("CLAUDE"); // provider ON — kill must still win
        when(generationConfigRepository.findById(1)).thenReturn(Optional.of(config));

        PairedPostScheduler.Call1Attempt attempt =
                scheduler.generateCall1(author(), "COUPLE", "corr123", Instant.now(), "BLIND");

        assertThat(attempt.hold()).isEmpty();
        assertThat(attempt.llmInvoked()).isFalse();
        assertThat(attempt.detail()).isEqualTo("kill switch");
        org.mockito.Mockito.verifyNoInteractions(llmClient);
    }

    @Test
    void generateCall1_missingConfigRowDoesNotTreatAsKilled() {
        when(generationConfigRepository.findById(1)).thenReturn(Optional.empty());
        // Row missing -> provider falls back to yml default, which is OFF here, so the
        // attempt is skipped for provider reasons — but NOT for "kill switch".
        PairedPostScheduler.Call1Attempt attempt =
                scheduler.generateCall1(author(), "COUPLE", "corr456", Instant.now(), "BLIND");

        assertThat(attempt.hold()).isEmpty();
        assertThat(attempt.detail()).isNotEqualTo("kill switch");
    }

    /** paired guard용 source claim은 이 테스트들의 관심사가 아니다 — 항상 빈 결과로 fail-open 시킨다. */
    private static com.againspring.aiuser.orchestrator.service.threadplan.PlanSourceStoryResolver pairedSourceStoryResolverStub() {
        var resolver = mock(com.againspring.aiuser.orchestrator.service.threadplan.PlanSourceStoryResolver.class);
        when(resolver.claimAndResolve(any(), any(), any(), any(), any())).thenReturn(java.util.Optional.empty());
        return resolver;
    }
}
