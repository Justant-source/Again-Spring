package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.service.threadplan.PlanSourceStoryResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * persona-diversity-v4 WP2 재배선 — {@code PairedPostScheduler.generateCall1}이 claim한 계약7
 * 골격(sourceContext)을 실제로 Call1 LLM 요청에 실어 보내는지 잠근다. 이전에는 claim만 하고
 * {@code b_side_viable} 판정에만 쓴 뒤 버렸다(솔로 경로와 달리 LLM이 골격을 본 적이 없었음).
 */
class PairedPostCall1SkeletonTest {

    private PairedPostScheduler scheduler;
    private AiUserGenerationConfigRepository generationConfigRepository;
    private com.againspring.aiuser.orchestrator.client.LlmAiUserClient llmClient;

    @BeforeEach
    void setUp() {
        OrchestratorProperties props = new OrchestratorProperties();
        props.setEnabled(true);
        props.getPairedPost().setEnabled(true);

        generationConfigRepository = mock(AiUserGenerationConfigRepository.class);
        AiUserGenerationConfig config = mock(AiUserGenerationConfig.class);
        when(config.isAiUserKillSwitch()).thenReturn(false);
        when(config.getProviderAiPostBundle()).thenReturn("CLAUDE");
        when(generationConfigRepository.findById(1)).thenReturn(Optional.of(config));

        llmClient = mock(com.againspring.aiuser.orchestrator.client.LlmAiUserClient.class);
        when(llmClient.generatePairedCall1(any())).thenReturn(Optional.empty());

        com.againspring.aiuser.orchestrator.service.persona.PersonaLottery personaLottery =
                mock(com.againspring.aiuser.orchestrator.service.persona.PersonaLottery.class);
        when(personaLottery.drawCommenters(any(), any(), any(), anyInt(), any())).thenReturn(List.of());

        com.againspring.aiuser.orchestrator.service.threadplan.PlanPersonaMapper planPersonaMapper =
                mock(com.againspring.aiuser.orchestrator.service.threadplan.PlanPersonaMapper.class);
        when(planPersonaMapper.mapAuthor(any())).thenReturn(Map.of("personaId", "author-1"));
        when(planPersonaMapper.mapCast(any())).thenReturn(List.of());

        com.againspring.aiuser.orchestrator.repository.PersonaRepository personaRepo =
                mock(com.againspring.aiuser.orchestrator.repository.PersonaRepository.class);
        when(personaRepo.findByActiveTrue()).thenReturn(List.of());

        scheduler = new PairedPostScheduler(
                mock(com.againspring.aiuser.orchestrator.repository.PersonaRelationshipRepository.class),
                personaRepo,
                llmClient,
                props,
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard.class),
                generationConfigRepository,
                mock(com.againspring.aiuser.orchestrator.service.DailyPostQuotaService.class),
                mock(com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                planPersonaMapper,
                mock(com.againspring.aiuser.orchestrator.service.threadplan.CandidateScheduleSupport.class),
                mock(com.againspring.aiuser.orchestrator.service.GenerationConfigSupport.class),
                mock(com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService.class),
                mock(com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache.class),
                personaLottery,
                mock(PlanSourceStoryResolver.class),
                mock(com.againspring.aiuser.orchestrator.service.threadplan.AiPostBundleService.class),
                new com.againspring.aiuser.orchestrator.safety.SourceOverlapGuard(),
                mock(com.againspring.aiuser.orchestrator.service.threadplan.SourceReservationSupport.class));
    }

    private Persona author() {
        Persona p = mock(Persona.class);
        when(p.getId()).thenReturn("author-1");
        return p;
    }

    @Test
    void generateCall1_carriesResolvedSkeletonIntoRequest() {
        PlanSourceStoryResolver.ResolvedSource resolvedSource = new PlanSourceStoryResolver.ResolvedSource(
                "팀장이 내 기획안을 가로챔",
                Map.of("incident", "팀장이 내 기획안을 자기 이름으로 임원 보고함",
                        "counterpart_claim", "팀 성과라 대표로 보고했을 뿐이다",
                        "b_side_viable", true),
                true,
                777L,
                "원문 전문 (프롬프트로 나가면 안 됨)",
                "natepan",
                "https://example.invalid/1",
                "원문 제목",
                "",
                List.of());

        scheduler.generateCall1(author(), "WORK", "corr-skeleton", Instant.now(), "natepan",
                "partner-1", resolvedSource);

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(llmClient).generatePairedCall1(captor.capture());
        Map<String, Object> request = captor.getValue();

        assertThat(request.get("sourceContext")).isEqualTo(resolvedSource.sourceContext());
        assertThat(request.get("reconstructMode")).isEqualTo(true);
        assertThat(request.get("sourceExampleId")).isEqualTo(777L);
        assertThat(request.get("topicHint")).isEqualTo("팀장이 내 기획안을 가로챔");
        // 원문 전문(sourceBody)은 프롬프트로 나가는 request에 실리면 안 된다 — SourceOverlapGuard만의 몫.
        assertThat(request.values()).doesNotContain("원문 전문 (프롬프트로 나가면 안 됨)");
    }

    @Test
    void generateCall1_freestyleWhenResolvedSourceNull() {
        scheduler.generateCall1(author(), "WORK", "corr-freestyle", Instant.now(), "natepan",
                "partner-1", null);

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(llmClient).generatePairedCall1(captor.capture());
        Map<String, Object> request = captor.getValue();

        assertThat(request).doesNotContainKey("sourceContext");
        assertThat(request).doesNotContainKey("reconstructMode");
    }
}
