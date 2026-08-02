package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanStatus;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanItemRepository;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PairedPostCommentPlanTest {

    @Mock private AiThreadPlanRepository planRepository;
    @Mock private AiThreadPlanItemRepository itemRepository;
    @Mock private PersonaRepository personaRepository;
    @Mock private ThreadPlanService planService;
    @Mock private LlmAiUserClient llmClient;
    @Mock private ContentSafetyGuard safetyGuard;
    @Mock private OrchestratorProperties properties;
    @Mock private AiUserGenerationConfigRepository configRepository;
    @Mock private OrchestratorProperties.ThreadPlan threadPlanConfig;
    @Mock private PlanPersonaMapper planPersonaMapper;
    @Mock private InterestedPersonaSeeder interestedPersonaSeeder;
    @Mock private AiUserGenerationConfig generationConfig;

    private ThreadPlanGenerationService service;

    @BeforeEach
    void setUp() {
        CandidateScheduleSupport scheduleSupport = new CandidateScheduleSupport(properties);
        ThreadQualityGate qualityGate = new ThreadQualityGate(safetyGuard);
        service = new ThreadPlanGenerationService(
                planRepository, itemRepository, personaRepository,
                planService, llmClient, qualityGate, properties, configRepository,
                scheduleSupport, planPersonaMapper, interestedPersonaSeeder
        );
        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.getThreadPlan()).thenReturn(threadPlanConfig);
        lenient().when(threadPlanConfig.isEnabled()).thenReturn(true);
        lenient().when(configRepository.findById(1)).thenReturn(Optional.of(generationConfig));
        lenient().when(generationConfig.isAiUserKillSwitch()).thenReturn(false);
    }

    @Test
    void combinePairedSourceBodyIncludesBothSides() {
        String combined = ThreadPlanGenerationService.combinePairedSourceBody(
                "작성자 본문", "상대방 본문");
        assertThat(combined).contains("[작성자]", "작성자 본문", "[상대방]", "상대방 본문");
    }

    @Test
    void ensureCommentPlanUsesYmlProviderWhenDbProvidersAreOff() {
        AiThreadPlan plan = AiThreadPlan.builder()
                .id("plan-paired-1")
                .postId("post_paired")
                .postRevision(2)
                .sourceType("HUMAN_POST")
                .status(ThreadPlanStatus.REQUESTED)
                .absoluteExpiresAt(Instant.now().plusSeconds(3600))
                .publishedAt(Instant.parse("2026-08-02T18:27:00Z"))
                .build();
        when(planService.requestPlan(eq("post_paired"), eq(2), eq("AI_POST"), any(),
                eq("제목"), anyString(), eq("MARRIED")))
                .thenReturn(plan);
        when(planRepository.save(plan)).thenReturn(plan);
        when(generationConfig.getProviderAiPostBundle()).thenReturn("OFF");
        when(generationConfig.getCandidatePoolSize()).thenReturn(8);
        when(threadPlanConfig.getAiPostProvider()).thenReturn("CLAUDE");
        when(threadPlanConfig.getAiPostModel()).thenReturn("claude-test");
        when(threadPlanConfig.getPlanPersonaCastMax()).thenReturn(12);
        when(threadPlanConfig.getBundleTimeoutMs()).thenReturn(60_000L);
        when(threadPlanConfig.getReadyMinTopLevel()).thenReturn(1);
        when(threadPlanConfig.getReadyMinItems()).thenReturn(1);
        when(threadPlanConfig.getStanceShareMax()).thenReturn(0.8);
        when(personaRepository.findByActiveTrue()).thenReturn(List.of(persona("p1")));
        when(planPersonaMapper.mapCast(anyList())).thenReturn(List.of(Map.of("id", "p1")));
        when(planPersonaMapper.castIds(anyList())).thenReturn(Set.of("p1"));
        when(personaRepository.existsById("p1")).thenReturn(true);
        when(safetyGuard.check(anyString(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Instant scheduled = Instant.parse("2026-08-02T19:00:00Z");
        when(llmClient.generateThreadPlan(any())).thenReturn(Optional.of(Map.of(
                "items", List.of(Map.of(
                        "ref", "c1",
                        "personaId", "p1",
                        "body", "양쪽 입장 다 이해돼요 충분함",
                        "parentRef", "",
                        "scheduledAt", scheduled.toString()
                ))
        )));

        AtomicInteger finds = new AtomicInteger();
        when(planRepository.findById("plan-paired-1")).thenAnswer(inv -> {
            if (finds.getAndIncrement() < 3) {
                return Optional.of(plan);
            }
            plan.setStatus(ThreadPlanStatus.ACTIVE);
            return Optional.of(plan);
        });
        doAnswer(inv -> {
            plan.setStatus(ThreadPlanStatus.READY);
            return null;
        }).when(planService).markReady("plan-paired-1");
        doAnswer(inv -> {
            plan.setStatus(ThreadPlanStatus.ACTIVE);
            return null;
        }).when(planService).activate("plan-paired-1");

        boolean ok = service.ensureCommentPlanForPairedPost(
                "post_paired", 2, "제목", "작성자 본문", "상대방 본문", "MARRIED");

        assertThat(ok).isTrue();
        assertThat(plan.getSourceType()).isEqualTo("AI_POST");
        assertThat(plan.getSourceBody()).contains("[작성자]", "[상대방]");
        ArgumentCaptor<Map<String, Object>> req = ArgumentCaptor.forClass(Map.class);
        verify(llmClient, atLeastOnce()).generateThreadPlan(req.capture());
        assertThat(req.getValue().get("provider")).isEqualTo("CLAUDE");
        assertThat(req.getValue().get("kind")).isEqualTo("AI_POST");
        verify(planService).markGenerating("plan-paired-1", "CLAUDE", "claude-test");
    }

    @Test
    void generateOneWithoutFallbackSkipsWhenProviderOff() {
        AiThreadPlan plan = AiThreadPlan.builder()
                .id("plan-stuck")
                .postId("post_x")
                .postRevision(2)
                .sourceType("HUMAN_POST")
                .status(ThreadPlanStatus.REQUESTED)
                .absoluteExpiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(planRepository.findById("plan-stuck")).thenReturn(Optional.of(plan));
        when(generationConfig.getProviderHumanPostPlan()).thenReturn("OFF");

        service.generateOne("plan-stuck");

        verify(llmClient, never()).generateThreadPlan(any());
        verify(planService, never()).markGenerating(any(), any(), any());
    }

    private static Persona persona(String id) {
        return Persona.builder().id(id).active(true).archetype("x").tier("REGULAR")
                .voiceProfile(Map.of()).interests(Map.of()).biasProfile(Map.of())
                .circadian(java.util.Collections.nCopies(24, 0.5))
                .build();
    }
}
