package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.service.threadplan.QuietHours;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Author-slot sampling must never land in KST 02–06 (hard ban).
 * Uses a partially constructed scheduler — only {@link PairedPostScheduler#sampleAuthorSlots} is exercised.
 */
class PairedPostAuthorSlotTest {

    private PairedPostScheduler scheduler;
    private OrchestratorProperties props;

    @BeforeEach
    void setUp() {
        props = new OrchestratorProperties();
        props.setEnabled(true);
        props.getPairedPost().setEnabled(true);
        props.getPairedPost().setAuthorSlotFromHour(0); // include overnight so quiet hours are in the window
        props.getPairedPost().setAuthorSlotToHour(23);
        props.getThreadPlan().setPostSlotMinSpacingMinutes(5);

        // Dependencies unused by sampleAuthorSlots — nulls / mocks are fine.
        scheduler = new PairedPostScheduler(
                mock(com.againspring.aiuser.orchestrator.repository.PersonaRelationshipRepository.class),
                mock(com.againspring.aiuser.orchestrator.repository.PersonaRepository.class),
                mock(com.againspring.aiuser.orchestrator.client.LlmAiUserClient.class),
                props,
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard.class),
                mock(com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository.class),
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

    @Test
    void sampleAuthorSlots_neverInQuietHours() {
        List<Instant> slots = scheduler.sampleAuthorSlots(12, props.getPairedPost());
        assertThat(slots).hasSize(12);
        for (Instant slot : slots) {
            assertThat(QuietHours.isQuiet(slot))
                    .as("slot %s hour=%s", slot, slot.atZone(ZoneId.of("Asia/Seoul")).getHour())
                    .isFalse();
        }
    }

    @Test
    void enforceAuthorSlot_rejectsSyntheticQuietCandidates() {
        Instant quiet = LocalDate.of(2026, 8, 4).atTime(2, 30).atZone(ZoneId.of("Asia/Seoul")).toInstant();
        Instant enforced = QuietHours.enforceAuthorSlot(quiet);
        assertThat(QuietHours.isQuiet(enforced)).isFalse();
        assertThat(enforced).isAfter(quiet);
    }

    /** paired guard용 source claim은 이 테스트들의 관심사가 아니다 — 항상 빈 결과로 fail-open 시킨다. */
    private static com.againspring.aiuser.orchestrator.service.threadplan.PlanSourceStoryResolver pairedSourceStoryResolverStub() {
        var resolver = mock(com.againspring.aiuser.orchestrator.service.threadplan.PlanSourceStoryResolver.class);
        when(resolver.claimAndResolve(any(), any(), any(), any(), any())).thenReturn(java.util.Optional.empty());
        return resolver;
    }
}
