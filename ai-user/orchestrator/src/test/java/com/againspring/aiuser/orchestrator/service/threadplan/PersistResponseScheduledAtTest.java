package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem;
import com.againspring.aiuser.orchestrator.domain.Persona;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PersistResponseScheduledAtTest {

    @Mock private AiThreadPlanRepository planRepository;
    @Mock private AiThreadPlanItemRepository itemRepository;
    @Mock private PersonaRepository personaRepository;
    @Mock private ThreadPlanService planService;
    @Mock private com.againspring.aiuser.orchestrator.client.LlmAiUserClient llmClient;
    @Mock private ContentSafetyGuard safetyGuard;
    @Mock private OrchestratorProperties properties;
    @Mock private AiUserGenerationConfigRepository configRepository;
    @Mock private OrchestratorProperties.ThreadPlan threadPlanConfig;
    @Mock private PlanPersonaMapper planPersonaMapper;
    @Mock private InterestedPersonaSeeder interestedPersonaSeeder;

    private ThreadPlanGenerationService service;
    private CandidateScheduleSupport scheduleSupport;

    @BeforeEach
    void setUp() {
        scheduleSupport = new CandidateScheduleSupport(properties);
        ThreadQualityGate qualityGate = new ThreadQualityGate(safetyGuard);
        service = new ThreadPlanGenerationService(
                planRepository, itemRepository, personaRepository,
                planService, llmClient, qualityGate, properties, configRepository,
                scheduleSupport, planPersonaMapper, interestedPersonaSeeder,
                mock(com.againspring.aiuser.orchestrator.client.BackendBotClient.class),
                new com.againspring.aiuser.orchestrator.service.GenerationConfigSupport(configRepository, properties),
                mock(com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache.class));
        when(properties.getThreadPlan()).thenReturn(threadPlanConfig);
        when(threadPlanConfig.getReadyMinTopLevel()).thenReturn(1);
        when(threadPlanConfig.getReadyMinItems()).thenReturn(1);
        when(threadPlanConfig.getStanceShareMax()).thenReturn(0.80);
    }

    @Test
    void persistResponseHonorsStoredScheduledAt() {
        Instant publishedAt = Instant.parse("2026-08-01T11:00:00Z");
        Instant stored = Instant.parse("2026-08-01T15:30:00Z");
        AiThreadPlan plan = AiThreadPlan.builder()
                .id("plan-1")
                .postId("post-1")
                .publishedAt(publishedAt)
                .build();
        when(planRepository.findById("plan-1")).thenReturn(Optional.of(plan));
        when(personaRepository.existsById("p1")).thenReturn(true);
        when(safetyGuard.check(any(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ref", "c1");
        item.put("personaId", "p1");
        item.put("body", "미리 지정된 시각 댓글입니다 충분");
        item.put("scheduledAt", stored.toString());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", List.of(item));

        ThreadQualityGate.QualityResult result = service.persistResponse("plan-1", response, Set.of("p1"));

        assertThat(result.passedOperationalMin()).isTrue();
        ArgumentCaptor<AiThreadPlanItem> captor = ArgumentCaptor.forClass(AiThreadPlanItem.class);
        verify(itemRepository).save(captor.capture());
        assertThat(captor.getValue().getScheduledAt()).isEqualTo(stored);
    }

    @Test
    void persistResponseDropsPersonaOutsideCast() {
        AiThreadPlan plan = AiThreadPlan.builder()
                .id("plan-2")
                .postId("post-2")
                .publishedAt(Instant.parse("2026-08-01T11:00:00Z"))
                .build();
        when(planRepository.findById("plan-2")).thenReturn(Optional.of(plan));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ref", "c1");
        item.put("personaId", "outsider");
        item.put("body", "캐스트 밖 페르소나 댓글입니다");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", List.of(item));

        ThreadQualityGate.QualityResult result =
                service.persistResponse("plan-2", response, Set.of("p1", "p2"));

        assertThat(result.passedOperationalMin()).isFalse();
        assertThat(result.keptItems()).isEmpty();
        assertThat(result.reasons()).anyMatch(r -> r.startsWith("CAST:"));
        verify(itemRepository, never()).save(any());
    }

    @Test
    void persistAndFinalizeThinReadyWhenBelowReadyMinAndRegenUnavailable() {
        when(threadPlanConfig.getReadyMinTopLevel()).thenReturn(3);
        when(threadPlanConfig.getReadyMinItems()).thenReturn(6);

        AiThreadPlan plan = AiThreadPlan.builder()
                .id("plan-3")
                .postId("post-3")
                .publishedAt(Instant.parse("2026-08-01T11:00:00Z"))
                .build();
        when(planRepository.findById("plan-3")).thenReturn(Optional.of(plan));
        when(personaRepository.existsById("p1")).thenReturn(true);
        when(safetyGuard.check(any(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ref", "c1");
        item.put("personaId", "p1");
        item.put("body", "하나뿐인 댓글이라 운영 하한 미달");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", List.of(item));

        ThreadQualityGate.QualityResult result =
                service.persistAndFinalize("plan-3", response, Set.of("p1"));

        assertThat(result.passedOperationalMin()).isFalse();
        assertThat(result.keptItems()).hasSize(1);
        verify(planService, never()).markFailed(any(), any());
        verify(planService).markReady("plan-3");
        verify(planService).activate("plan-3");
        verify(itemRepository).save(any());
        verify(llmClient, never()).generateThreadPlan(any());
        verify(interestedPersonaSeeder).seedFromPlanCast(eq("post-3"), argThat(ids ->
                ids != null && ids.contains("p1")));
    }

    @Test
    void persistAndFinalizeRegensOnceAndReadyWhenSecondPassMeetsMin() {
        when(threadPlanConfig.getReadyMinTopLevel()).thenReturn(3);
        when(threadPlanConfig.getReadyMinItems()).thenReturn(6);
        when(threadPlanConfig.getHumanPlanProvider()).thenReturn("CLAUDE");
        when(threadPlanConfig.getHumanPlanModel()).thenReturn("claude-haiku");
        when(threadPlanConfig.getBundleTimeoutMs()).thenReturn(60_000L);

        AiThreadPlan plan = AiThreadPlan.builder()
                .id("plan-5")
                .postId("post-5")
                .publishedAt(Instant.parse("2026-08-01T11:00:00Z"))
                .sourceTitle("제목입니다")
                .sourceBody("본문이 충분히 길어서 재생성이 가능합니다")
                .build();
        when(planRepository.findById("plan-5")).thenReturn(Optional.of(plan));
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(configRepository.findById(1)).thenReturn(Optional.empty());
        when(personaRepository.existsById("p1")).thenReturn(true);
        when(personaRepository.findById("p1")).thenReturn(Optional.of(Persona.builder()
                .id("p1").active(true).build()));
        when(safetyGuard.check(any(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(planPersonaMapper.mapCast(any())).thenReturn(List.of(Map.of("id", "p1")));
        when(planPersonaMapper.castIds(any())).thenReturn(Set.of("p1"));

        Map<String, Object> thin = new LinkedHashMap<>();
        thin.put("items", List.of(commentRow("c1", "첫 패스 하한 미달 댓글")));

        List<Map<String, Object>> richItems = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            richItems.add(commentRow("r" + i, "재생성 후 충분한 댓글 본문 " + i));
        }
        Map<String, Object> rich = new LinkedHashMap<>();
        rich.put("items", richItems);
        when(llmClient.generateThreadPlan(any())).thenReturn(Optional.of(rich));

        ThreadQualityGate.QualityResult result =
                service.persistAndFinalize("plan-5", thin, Set.of("p1"));

        assertThat(result.passedOperationalMin()).isTrue();
        assertThat(result.keptItems()).hasSize(6);
        verify(llmClient, times(1)).generateThreadPlan(any());
        verify(planService, never()).markFailed(any(), any());
        verify(planService).markReady("plan-5");
        verify(planService).activate("plan-5");
        verify(itemRepository, times(6)).save(any());
    }

    @Test
    void persistAndFinalizeThinReadyAfterRegenStillBelowMin() {
        when(threadPlanConfig.getReadyMinTopLevel()).thenReturn(3);
        when(threadPlanConfig.getReadyMinItems()).thenReturn(6);
        when(threadPlanConfig.getHumanPlanProvider()).thenReturn("CLAUDE");
        when(threadPlanConfig.getHumanPlanModel()).thenReturn("claude-haiku");
        when(threadPlanConfig.getBundleTimeoutMs()).thenReturn(60_000L);

        AiThreadPlan plan = AiThreadPlan.builder()
                .id("plan-6")
                .postId("post-6")
                .publishedAt(Instant.parse("2026-08-01T11:00:00Z"))
                .sourceTitle("제목입니다")
                .sourceBody("본문이 충분히 길어서 재생성이 가능합니다")
                .build();
        when(planRepository.findById("plan-6")).thenReturn(Optional.of(plan));
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(configRepository.findById(1)).thenReturn(Optional.empty());
        when(personaRepository.existsById("p1")).thenReturn(true);
        when(personaRepository.findById("p1")).thenReturn(Optional.of(Persona.builder()
                .id("p1").active(true).build()));
        when(safetyGuard.check(any(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(planPersonaMapper.mapCast(any())).thenReturn(List.of(Map.of("id", "p1")));
        when(planPersonaMapper.castIds(any())).thenReturn(Set.of("p1"));

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("items", List.of(commentRow("c1", "첫 패스 하한 미달 댓글")));

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("items", List.of(
                commentRow("r1", "재생성도 여전히 부족한 댓글 하나"),
                commentRow("r2", "재생성도 여전히 부족한 댓글 둘")));
        when(llmClient.generateThreadPlan(any())).thenReturn(Optional.of(second));

        ThreadQualityGate.QualityResult result =
                service.persistAndFinalize("plan-6", first, Set.of("p1"));

        assertThat(result.passedOperationalMin()).isFalse();
        assertThat(result.keptItems()).hasSize(2);
        verify(llmClient, times(1)).generateThreadPlan(any());
        verify(planService, never()).markFailed(any(), any());
        verify(planService).markReady("plan-6");
        verify(planService).activate("plan-6");
        verify(itemRepository, times(2)).save(any());
    }

    @Test
    void persistAndFinalizeSeedsInterestedPersonasOnReady() {
        AiThreadPlan plan = AiThreadPlan.builder()
                .id("plan-4")
                .postId("post-4")
                .publishedAt(Instant.parse("2026-08-01T11:00:00Z"))
                .build();
        when(planRepository.findById("plan-4")).thenReturn(Optional.of(plan));
        when(personaRepository.existsById("p1")).thenReturn(true);
        when(safetyGuard.check(any(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ref", "c1");
        item.put("personaId", "p1");
        item.put("body", "READY 하한을 넘는 충분한 댓글 본문");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", List.of(item));

        ThreadQualityGate.QualityResult result =
                service.persistAndFinalize("plan-4", response, Set.of("p1"));

        assertThat(result.passedOperationalMin()).isTrue();
        verify(planService).markReady("plan-4");
        verify(planService).activate("plan-4");
        verify(interestedPersonaSeeder).seedFromPlanCast(eq("post-4"), argThat(ids ->
                ids != null && ids.contains("p1")));
    }

    private static Map<String, Object> commentRow(String ref, String body) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ref", ref);
        item.put("personaId", "p1");
        item.put("body", body);
        return item;
    }
}
