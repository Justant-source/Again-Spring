package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanItemRepository;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreadPlanGenerationServiceTest {

    @Mock
    private AiThreadPlanRepository planRepository;
    @Mock
    private AiThreadPlanItemRepository itemRepository;
    @Mock
    private PersonaRepository personaRepository;
    @Mock
    private ThreadPlanService planService;
    @Mock
    private LlmAiUserClient llmClient;
    @Mock
    private ContentSafetyGuard safetyGuard;
    @Mock
    private OrchestratorProperties properties;
    @Mock
    private AiUserGenerationConfigRepository configRepository;
    @Mock
    private OrchestratorProperties.ThreadPlan threadPlanConfig;

    @Mock
    private PlanPersonaMapper planPersonaMapper;
    @Mock
    private InterestedPersonaSeeder interestedPersonaSeeder;

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
    }

    private Map<Integer, Double> createWeights() {
        Map<Integer, Double> weights = new HashMap<>();
        // Typical Korean community curve: peaks at 22:00, troughs at 03:00-07:00
        weights.put(0, 0.05);   // 00:00 - trough
        weights.put(1, 0.05);   // 01:00 - trough
        weights.put(2, 0.05);   // 02:00 - trough
        weights.put(3, 0.05);   // 03:00 - dead (trough)
        weights.put(4, 0.08);   // 04:00 - dead (trough)
        weights.put(5, 0.08);   // 05:00 - dead (trough)
        weights.put(6, 0.10);   // 06:00 - dead (trough)
        weights.put(7, 0.15);   // 07:00 - trough ending
        weights.put(8, 0.35);   // 08:00 - morning rise
        weights.put(9, 0.50);   // 09:00 - moderate
        weights.put(10, 0.60);  // 10:00 - active
        weights.put(11, 0.70);  // 11:00 - active
        weights.put(12, 0.65);  // 12:00 - lunchtime
        weights.put(13, 0.55);  // 13:00 - post-lunch
        weights.put(14, 0.60);  // 14:00 - afternoon
        weights.put(15, 0.65);  // 15:00 - afternoon
        weights.put(16, 0.70);  // 16:00 - evening
        weights.put(17, 0.80);  // 17:00 - peak starts
        weights.put(18, 0.90);  // 18:00 - peak
        weights.put(19, 0.95);  // 19:00 - peak
        weights.put(20, 0.85);  // 20:00 - strong active
        weights.put(21, 0.95);  // 21:00 - peak
        weights.put(22, 1.00);  // 22:00 - absolute peak
        weights.put(23, 0.80);  // 23:00 - evening
        return weights;
    }

    @Test
    void scheduleSnapsCandidateFromDeadHourToNextActiveHour() {
        // Post published at 03:00 KST (dead hour)
        // Index 0 means 3 minutes offset -> would land at 03:03 KST (still dead)
        // Should snap forward to 08:00 KST (first hour with weight >= 0.2)
        Instant publishedAt = Instant.parse("2026-07-30T18:00:00Z"); // 03:00 KST on 2026-07-31

        when(properties.getThreadPlan()).thenReturn(threadPlanConfig);
        when(threadPlanConfig.getKstHourlyHumanWeights()).thenReturn(createWeights());

        Instant result = service.schedule(publishedAt, 0, false);

        int resultHour = result.atZone(ActivityCurve.KST).getHour();
        assertThat(resultHour).isEqualTo(8); // Snapped to first active hour
        assertThat(result.atZone(ActivityCurve.KST).getMinute()).isEqualTo(0);
        assertThat(result.atZone(ActivityCurve.KST).getSecond()).isEqualTo(0);
    }

    @Test
    void scheduleKeepsAnActiveHourUnchanged() {
        // Post published at 20:00 KST (weight=0.85, active)
        // Index 0 means 3 minutes offset -> 20:03 KST
        // Should remain unchanged because 0.85 >= 0.2
        Instant publishedAt = Instant.parse("2026-07-31T11:00:00Z"); // 20:00 KST on 2026-07-31

        when(properties.getThreadPlan()).thenReturn(threadPlanConfig);
        when(threadPlanConfig.getKstHourlyHumanWeights()).thenReturn(createWeights());

        Instant result = service.schedule(publishedAt, 0, false);

        int resultHour = result.atZone(ActivityCurve.KST).getHour();
        int resultMinute = result.atZone(ActivityCurve.KST).getMinute();
        assertThat(resultHour).isEqualTo(20);
        assertThat(resultMinute).isEqualTo(3); // Unchanged: 0 + 3 minutes
    }

    @Test
    void scheduleReplyOffsetStillAppliesToDeadHourSnapping() {
        // Post published at 03:00 KST (dead)
        // Index 0 with reply=true means 3+7=10 minutes offset -> 03:10 KST (still dead)
        // Should snap to 08:00 KST (first active hour)
        Instant publishedAt = Instant.parse("2026-07-30T18:00:00Z"); // 03:00 KST on 2026-07-31

        when(properties.getThreadPlan()).thenReturn(threadPlanConfig);
        when(threadPlanConfig.getKstHourlyHumanWeights()).thenReturn(createWeights());

        Instant result = service.schedule(publishedAt, 0, true); // reply=true

        int resultHour = result.atZone(ActivityCurve.KST).getHour();
        assertThat(resultHour).isEqualTo(8); // Snapped to first active hour, not 03:10
        assertThat(result.atZone(ActivityCurve.KST).getMinute()).isEqualTo(0);
    }

}
