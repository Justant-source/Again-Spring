package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private ThreadPlanGenerationService service;
    private CandidateScheduleSupport scheduleSupport;

    @BeforeEach
    void setUp() {
        scheduleSupport = new CandidateScheduleSupport(properties);
        service = new ThreadPlanGenerationService(
                planRepository, itemRepository, personaRepository,
                planService, llmClient, safetyGuard, properties, configRepository,
                scheduleSupport);
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
        item.put("body", "미리 지정된 시각 댓글");
        item.put("scheduledAt", stored.toString());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", List.of(item));

        service.persistResponse("plan-1", response);

        ArgumentCaptor<AiThreadPlanItem> captor = ArgumentCaptor.forClass(AiThreadPlanItem.class);
        verify(itemRepository).save(captor.capture());
        assertThat(captor.getValue().getScheduledAt()).isEqualTo(stored);
    }
}
