package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.CommentThreadDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemStatus;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
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
    @Mock private BackendBotClient backendBotClient;

    private ThreadPlanGenerationService service;
    private CandidateScheduleSupport scheduleSupport;

    @BeforeEach
    void setUp() {
        scheduleSupport = new CandidateScheduleSupport(properties);
        ThreadQualityGate qualityGate = new ThreadQualityGate(safetyGuard);
        service = new ThreadPlanGenerationService(
                planRepository, itemRepository, personaRepository,
                planService, llmClient, qualityGate, properties, configRepository,
                scheduleSupport, planPersonaMapper, interestedPersonaSeeder, backendBotClient,
                new com.againspring.aiuser.orchestrator.service.GenerationConfigSupport(configRepository, properties),
                mock(com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache.class)
        );
        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.getThreadPlan()).thenReturn(threadPlanConfig);
        lenient().when(threadPlanConfig.isEnabled()).thenReturn(true);
        lenient().when(configRepository.findById(1)).thenReturn(Optional.of(generationConfig));
        lenient().when(generationConfig.isAiUserKillSwitch()).thenReturn(false);
        lenient().when(threadPlanConfig.getKstHourlyHumanWeights()).thenReturn(flatWeights());
    }

    @Test
    void combinePairedSourceBodyIncludesBothSides() {
        String combined = ThreadPlanGenerationService.combinePairedSourceBody(
                "작성자 본문", "상대방 본문");
        assertThat(combined).contains("[작성자]", "작성자 본문", "[상대방]", "상대방 본문");
    }

    @Test
    void combinePairedSourceBodyAuthorOnlyWhenPartnerBlank() {
        String authorOnly = ThreadPlanGenerationService.combinePairedSourceBody("작성자만", null);
        assertThat(authorOnly).isEqualTo("작성자만");
        assertThat(authorOnly).doesNotContain("[상대방]");
    }

    @Test
    void clampScheduledAtsBeforePartnerIsStrictlyBeforePartnerAt() {
        Instant publishedAt = Instant.parse("2026-08-04T10:00:00Z");
        Instant partnerAt = Instant.parse("2026-08-04T11:00:00Z");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", List.of(
                item("c1", "p1", "초반 댓글입니다 충분함", "2026-08-04T12:00:00Z"), // after partner
                item("c2", "p1", "두번째 댓글입니다 충분", (String) null)
        ));

        scheduleSupport.clampScheduledAtsBefore(response, publishedAt, partnerAt);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        for (Map<String, Object> row : items) {
            Instant at = Instant.parse(String.valueOf(row.get("scheduledAt")));
            assertThat(at).isBefore(partnerAt);
            assertThat(at).isAfter(publishedAt);
        }
    }

    @Test
    void ensureAuthorPhase1GroundsOnAuthorOnlyAndRequestsSmallPool() {
        Instant publishedAt = Instant.parse("2026-08-04T10:00:00Z");
        Instant partnerAt = Instant.parse("2026-08-04T11:00:00Z");
        AiThreadPlan plan = AiThreadPlan.builder()
                .id("plan-phase1")
                .postId("post_p1")
                .postRevision(1)
                .sourceType("AI_POST")
                .status(ThreadPlanStatus.REQUESTED)
                .absoluteExpiresAt(Instant.now().plusSeconds(3600))
                .publishedAt(publishedAt)
                .build();
        when(planService.requestPlan(eq("post_p1"), eq(1), eq("AI_POST"), eq(publishedAt),
                eq("제목"), eq("작성자 본문"), eq("MARRIED")))
                .thenReturn(plan);
        when(planRepository.save(plan)).thenReturn(plan);
        stubPhaseGeneration(plan, "plan-phase1");
        when(llmClient.generateThreadPlan(any())).thenReturn(Optional.of(Map.of(
                "items", List.of(
                        item("c1", "p1", "작성자만 본 댓글입니다 충분", publishedAt.plusSeconds(180).toString()),
                        item("c2", "p1", "초반 공감 댓글입니다 충분", publishedAt.plusSeconds(480).toString())
                )
        )));

        boolean ok = service.ensureAuthorPhase1CommentPlan(
                "post_p1", 1, "제목", "작성자 본문", "MARRIED", publishedAt, partnerAt);

        assertThat(ok).isTrue();
        assertThat(plan.getSourceBody()).isEqualTo("작성자 본문");
        ArgumentCaptor<Map<String, Object>> req = ArgumentCaptor.forClass(Map.class);
        verify(llmClient, atLeastOnce()).generateThreadPlan(req.capture());
        assertThat(req.getValue().get("maxTopLevel"))
                .isEqualTo(ThreadPlanGenerationService.PHASE1_MAX_TOP_LEVEL);
        assertThat(req.getValue().get("maxReplies"))
                .isEqualTo(ThreadPlanGenerationService.PHASE1_MAX_REPLIES);
        assertThat(req.getValue().get("existingBody")).isEqualTo("작성자 본문");
    }

    /**
     * DB provider is SSOT (Task 4.2): OFF must stay OFF and never fall back to the yml default,
     * even for paired-post Phase2 generation. Generation is skipped entirely — plan stays REQUESTED.
     */
    @Test
    void ensureCommentPlanSkipsGenerationWhenDbProviderOff() {
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
        when(planRepository.findById("plan-paired-1")).thenReturn(Optional.of(plan));

        boolean ok = service.ensureCommentPlanForPairedPost(
                "post_paired", 2, "제목", "작성자 본문", "상대방 본문", "MARRIED");

        assertThat(ok).isFalse();
        assertThat(plan.getStatus()).isEqualTo(ThreadPlanStatus.REQUESTED);
        verify(llmClient, never()).generateThreadPlan(any());
        verify(planService, never()).markGenerating(anyString(), anyString(), anyString());
    }

    @Test
    void partnerArrivalRequestPlanCancelsUnpublishedOnOlderRevision() {
        // Existing replan contract: new revision cancels unfinished from older plans.
        AiThreadPlanRepository realPlans = mock(AiThreadPlanRepository.class);
        AiThreadPlanItemRepository realItems = mock(AiThreadPlanItemRepository.class);
        ThreadPlanService realService = new ThreadPlanService(realPlans, realItems);

        AiThreadPlan older = AiThreadPlan.builder()
                .id("plan-rev1")
                .postId("post_x")
                .postRevision(1)
                .sourceType("AI_POST")
                .status(ThreadPlanStatus.ACTIVE)
                .publishedAt(Instant.parse("2026-08-04T10:00:00Z"))
                .absoluteExpiresAt(Instant.parse("2026-08-05T10:00:00Z"))
                .build();
        when(realPlans.findByPostIdAndPostRevision("post_x", 2)).thenReturn(Optional.empty());
        when(realPlans.findByPostIdAndPostRevisionLessThanAndStatusIn(eq("post_x"), eq(2), any()))
                .thenReturn(List.of(older));
        when(realPlans.saveAndFlush(any())).thenAnswer(inv -> {
            AiThreadPlan p = inv.getArgument(0);
            p.setId("plan-rev2");
            return p;
        });

        AiThreadPlan created = realService.requestPlan(
                "post_x", 2, "AI_POST", Instant.parse("2026-08-04T11:00:00Z"),
                "제목", "[작성자]\na\n\n[상대방]\nb", "MARRIED");

        assertThat(created.getId()).isEqualTo("plan-rev2");
        verify(realItems).cancelUnfinishedByPlanId(
                eq("plan-rev1"),
                eq(ThreadPlanItemStatus.CANCELLED),
                eq(EnumSet.of(
                        ThreadPlanItemStatus.RESERVED, ThreadPlanItemStatus.SCHEDULED,
                        ThreadPlanItemStatus.PROCESSING, ThreadPlanItemStatus.FAILED)));
        verify(realPlans).cancelOlderActivePlans(eq("post_x"), eq(2),
                eq(ThreadPlanStatus.CANCELLED), any());
    }

    @Test
    void loadLatestPublishedTopLevelCommentsCapsAtEightAndAllowsFewer() {
        when(backendBotClient.getComments("post_c", 0, 8)).thenReturn(List.of(
                comment(1L, "첫번째 공개 댓글"),
                comment(2L, "두번째 공개 댓글")
        ));

        List<Map<String, Object>> loaded = service.loadLatestPublishedTopLevelComments("post_c", 8);

        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0).get("body")).isEqualTo("첫번째 공개 댓글");
        assertThat(loaded.get(1).get("id")).isEqualTo(2L);
    }

    @Test
    void loadLatestPublishedTopLevelCommentsEmptyIsOk() {
        when(backendBotClient.getComments("post_empty", 0, 8)).thenReturn(List.of());
        assertThat(service.loadLatestPublishedTopLevelComments("post_empty", 8)).isEmpty();
    }

    @Test
    void attachPhase2FromCall2PersistsItemsOnOrAfterPartner() {
        Instant partnerAt = Instant.parse("2026-08-04T11:00:00Z");
        AiThreadPlan plan = AiThreadPlan.builder()
                .id("plan-call2")
                .postId("post_c2")
                .postRevision(2)
                .sourceType("AI_POST")
                .status(ThreadPlanStatus.REQUESTED)
                .absoluteExpiresAt(Instant.now().plusSeconds(3600))
                .publishedAt(partnerAt)
                .build();
        when(planService.requestPlan(eq("post_c2"), eq(2), eq("AI_POST"), eq(partnerAt),
                eq("제목"), anyString(), eq("WORK")))
                .thenReturn(plan);
        when(planRepository.save(plan)).thenReturn(plan);
        when(threadPlanConfig.getAiPostProvider()).thenReturn("CLAUDE");
        when(threadPlanConfig.getAiPostModel()).thenReturn("claude-test");
        when(threadPlanConfig.getReadyMinTopLevel()).thenReturn(1);
        when(threadPlanConfig.getReadyMinItems()).thenReturn(1);
        when(threadPlanConfig.getStanceShareMax()).thenReturn(0.8);
        when(personaRepository.findByActiveTrue()).thenReturn(List.of(persona("p1")));
        when(personaRepository.existsById("p1")).thenReturn(true);
        when(safetyGuard.check(anyString(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(planRepository.findById("plan-call2")).thenAnswer(inv -> {
            if (plan.getStatus() == ThreadPlanStatus.REQUESTED
                    || plan.getStatus() == ThreadPlanStatus.GENERATING) {
                return Optional.of(plan);
            }
            return Optional.of(plan);
        });
        doAnswer(inv -> {
            plan.setStatus(ThreadPlanStatus.GENERATING);
            return null;
        }).when(planService).markGenerating(eq("plan-call2"), any(), any());
        doAnswer(inv -> {
            plan.setStatus(ThreadPlanStatus.READY);
            return null;
        }).when(planService).markReady("plan-call2");
        doAnswer(inv -> {
            plan.setStatus(ThreadPlanStatus.ACTIVE);
            return null;
        }).when(planService).activate("plan-call2");

        Map<String, Object> call2 = Map.of(
                "items", List.of(
                        item("p2c1", "p1", "양쪽 보고 쓰는 댓글입니다 충분",
                                "2026-08-04T10:30:00Z") // before partner — must clamp up
                )
        );

        boolean ok = service.attachPhase2FromCall2Response(
                "post_c2", 2, "제목", "작성자", "상대방", "WORK", partnerAt, call2);

        assertThat(ok).isTrue();
        ArgumentCaptor<com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem> cap =
                ArgumentCaptor.forClass(com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem.class);
        verify(itemRepository).save(cap.capture());
        assertThat(cap.getValue().getScheduledAt()).isAfterOrEqualTo(partnerAt);
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

    private void stubPhaseGeneration(AiThreadPlan plan, String planId) {
        when(generationConfig.getProviderAiPostBundle()).thenReturn("CLAUDE");
        when(threadPlanConfig.getAiPostModel()).thenReturn("claude-test");
        when(threadPlanConfig.getPlanPersonaCastMax()).thenReturn(12);
        when(threadPlanConfig.getBundleTimeoutMs()).thenReturn(60_000L);
        when(threadPlanConfig.getStanceShareMax()).thenReturn(0.8);
        when(personaRepository.findByActiveTrue()).thenReturn(List.of(persona("p1")));
        when(planPersonaMapper.mapCast(anyList())).thenReturn(List.of(Map.of("id", "p1")));
        when(planPersonaMapper.castIds(anyList())).thenReturn(Set.of("p1"));
        when(personaRepository.existsById("p1")).thenReturn(true);
        when(safetyGuard.check(anyString(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AtomicInteger finds = new AtomicInteger();
        when(planRepository.findById(planId)).thenAnswer(inv -> {
            if (finds.getAndIncrement() < 3) {
                return Optional.of(plan);
            }
            plan.setStatus(ThreadPlanStatus.ACTIVE);
            return Optional.of(plan);
        });
        doAnswer(inv -> {
            plan.setStatus(ThreadPlanStatus.READY);
            return null;
        }).when(planService).markReady(planId);
        doAnswer(inv -> {
            plan.setStatus(ThreadPlanStatus.ACTIVE);
            return null;
        }).when(planService).activate(planId);
    }

    private static Map<String, Object> item(String ref, String personaId, String body, String scheduledAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ref", ref);
        m.put("personaId", personaId);
        m.put("body", body);
        m.put("parentRef", "");
        if (scheduledAt != null) m.put("scheduledAt", scheduledAt);
        return m;
    }

    private static CommentThreadDto comment(Long id, String body) {
        CommentThreadDto c = new CommentThreadDto();
        c.setId(id);
        c.setBody(body);
        c.setAuthorNickname("n" + id);
        return c;
    }

    private static Map<Integer, Double> flatWeights() {
        Map<Integer, Double> w = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) w.put(h, 1.0);
        return w;
    }

    private static Persona persona(String id) {
        return Persona.builder().id(id).active(true).archetype("x").tier("REGULAR")
                .voiceProfile(Map.of()).interests(Map.of()).biasProfile(Map.of())
                .circadian(java.util.Collections.nCopies(24, 0.5))
                .build();
    }
}
