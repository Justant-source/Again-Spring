package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4 fix-round-1 follow-up: {@code ScheduledPostPublisher.publishDue} already fails closed
 * on ai_user_kill_switch/schedule_execution_paused before firing due rows; PartnerAnswerPublisher
 * (the Call2/partner-answer publishing path) did not have the same check. This locks in the
 * added fail-closed gate.
 */
class PartnerAnswerPublisherKillSwitchTest {

    private PartnerAnswerPublisher publisher;
    private PartnerAnswerLeaseService leases;
    private AiUserGenerationConfigRepository generationConfigRepository;

    @BeforeEach
    void setUp() {
        OrchestratorProperties props = new OrchestratorProperties();
        props.setEnabled(true);
        props.getPairedPost().setEnabled(true);
        props.getPairedPost().setPartnerPublisherEnabled(true);
        props.getPairedPost().setPartnerPublishBatchSize(5);

        leases = mock(PartnerAnswerLeaseService.class);
        generationConfigRepository = mock(AiUserGenerationConfigRepository.class);

        publisher = new PartnerAnswerPublisher(
                leases,
                mock(com.againspring.aiuser.orchestrator.repository.PersonaRepository.class),
                mock(com.againspring.aiuser.orchestrator.client.LlmAiUserClient.class),
                mock(com.againspring.aiuser.orchestrator.client.BackendBotClient.class),
                mock(com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard.class),
                mock(com.againspring.aiuser.orchestrator.safety.SourceOverlapGuard.class),
                mock(com.againspring.aiuser.orchestrator.client.AiLearningClient.class),
                mock(ThreadPlanGenerationService.class),
                props,
                generationConfigRepository,
                mock(PlanPersonaMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.againspring.aiuser.orchestrator.service.GenerationConfigSupport.class),
                mock(com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService.class),
                mock(com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache.class),
                mock(com.againspring.aiuser.orchestrator.service.persona.PersonaLottery.class));
    }

    @Test
    void publishDue_skipsClaimWhenKillSwitchOn() {
        AiUserGenerationConfig config = mock(AiUserGenerationConfig.class);
        when(config.isAiUserKillSwitch()).thenReturn(true);
        when(generationConfigRepository.findById(1)).thenReturn(Optional.of(config));

        publisher.publishDue();

        verify(leases, never()).claimDue(anyString(), anyInt(), any(Duration.class), any(Instant.class));
    }

    @Test
    void publishDue_skipsClaimWhenSchedulePaused() {
        AiUserGenerationConfig config = mock(AiUserGenerationConfig.class);
        when(config.isAiUserKillSwitch()).thenReturn(false);
        when(config.isScheduleExecutionPaused()).thenReturn(true);
        when(generationConfigRepository.findById(1)).thenReturn(Optional.of(config));

        publisher.publishDue();

        verify(leases, never()).claimDue(anyString(), anyInt(), any(Duration.class), any(Instant.class));
    }

    @Test
    void publishDue_missingConfigRowFailsClosed() {
        when(generationConfigRepository.findById(1)).thenReturn(Optional.empty());

        publisher.publishDue();

        verify(leases, never()).claimDue(anyString(), anyInt(), any(Duration.class), any(Instant.class));
    }

    @Test
    void publishDue_claimsWhenNotBlocked() {
        AiUserGenerationConfig config = mock(AiUserGenerationConfig.class);
        when(config.isAiUserKillSwitch()).thenReturn(false);
        when(config.isScheduleExecutionPaused()).thenReturn(false);
        when(generationConfigRepository.findById(1)).thenReturn(Optional.of(config));
        when(leases.claimDue(anyString(), anyInt(), any(Duration.class), any(Instant.class)))
                .thenReturn(java.util.List.of());

        publisher.publishDue();

        verify(leases).claimDue(anyString(), anyInt(), any(Duration.class), any(Instant.class));
    }
}
