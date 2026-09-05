package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaRelationship;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRelationshipRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.safety.SourceOverlapGuard;
import com.againspring.aiuser.orchestrator.service.DailyPostQuotaService;
import com.againspring.aiuser.orchestrator.service.GenerationConfigSupport;
import com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService;
import com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache;
import com.againspring.aiuser.orchestrator.service.persona.PersonaLottery;
import com.againspring.aiuser.orchestrator.service.threadplan.AiPostBundleService;
import com.againspring.aiuser.orchestrator.service.threadplan.CandidateScheduleSupport;
import com.againspring.aiuser.orchestrator.service.threadplan.PlanPersonaMapper;
import com.againspring.aiuser.orchestrator.service.threadplan.PlanSourceStoryResolver;
import com.againspring.aiuser.orchestrator.service.threadplan.SourceReservationSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 코드리뷰 #2 — {@code PairedPostScheduler.holdPair}가 claim한 popular source 예약을, claim 이후
 * 구간(예: {@code generateCall1} 내부)에서 unchecked 예외가 나도 정확히 한 번 해제하는지 검증한다.
 * 수정 전에는 release 체크포인트가 전부 정상 반환 경로에만 있어, 예외가 나면 전부 건너뛰고
 * {@code runPairedPosts}의 바깥 catch로 곧장 전파돼 예약이 새지 않고 잠긴 채로 남았다(24h TTL로
 * 영구 누수는 아니지만 그 사이 소스가 묶임).
 *
 * <p>{@code holdPair}는 private이라 리플렉션으로 직접 호출한다 — 나머지 nightly 파이프라인
 * (관계 선택·일일 쿼터·카테고리 큐)까지 전부 구동하는 것보다 이 메서드 하나의 계약을 정확히
 * 잠그는 편이 이 리뷰 항목의 의도에 더 가깝다.</p>
 */
class PairedPostSourceReleaseOnExceptionTest {

    private PairedPostScheduler scheduler;
    private PlanSourceStoryResolver sourceStoryResolver;
    private LlmAiUserClient llmClient;
    private AiScheduledPostRepository scheduledPostRepository;
    private Method holdPair;

    private static final Long SOURCE_EXAMPLE_ID = 555L;
    private static final PersonaRelationship REL = PersonaRelationship.builder()
            .id(1L).personaId("author-1").otherId("partner-1").relationType("COUPLE").status("ACTIVE").build();

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        OrchestratorProperties props = new OrchestratorProperties();
        props.setEnabled(true);
        props.getPairedPost().setEnabled(true);

        AiUserGenerationConfigRepository generationConfigRepository = mock(AiUserGenerationConfigRepository.class);
        AiUserGenerationConfig config = mock(AiUserGenerationConfig.class);
        when(config.isAiUserKillSwitch()).thenReturn(false);
        when(config.getProviderAiPostBundle()).thenReturn("CLAUDE");
        when(generationConfigRepository.findById(1)).thenReturn(Optional.of(config));

        PersonaRepository personaRepo = mock(PersonaRepository.class);
        Persona author = Persona.builder().id("author-1").build();
        Persona partner = Persona.builder().id("partner-1").build();
        when(personaRepo.findById("author-1")).thenReturn(Optional.of(author));
        when(personaRepo.findById("partner-1")).thenReturn(Optional.of(partner));
        when(personaRepo.findByActiveTrue()).thenReturn(List.of());

        llmClient = mock(LlmAiUserClient.class);

        PersonaLottery personaLottery = mock(PersonaLottery.class);
        when(personaLottery.drawCommenters(any(), any(), any(), anyInt(), any())).thenReturn(List.of());

        PlanPersonaMapper planPersonaMapper = mock(PlanPersonaMapper.class);
        when(planPersonaMapper.mapAuthor(any())).thenReturn(Map.of("personaId", "author-1"));
        when(planPersonaMapper.mapCast(any())).thenReturn(List.of());

        sourceStoryResolver = mock(PlanSourceStoryResolver.class);
        scheduledPostRepository = mock(AiScheduledPostRepository.class);

        ContentSafetyGuard safetyGuard = mock(ContentSafetyGuard.class);
        when(safetyGuard.check(anyString(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());

        scheduler = new PairedPostScheduler(
                mock(PersonaRelationshipRepository.class),
                personaRepo,
                llmClient,
                props,
                mock(JdbcTemplate.class),
                safetyGuard,
                generationConfigRepository,
                mock(DailyPostQuotaService.class),
                scheduledPostRepository,
                new ObjectMapper(),
                planPersonaMapper,
                mock(CandidateScheduleSupport.class),
                mock(GenerationConfigSupport.class),
                mock(LlmGenerationGateService.class),
                mock(PromptTemplateCache.class),
                personaLottery,
                sourceStoryResolver,
                mock(AiPostBundleService.class),
                new SourceOverlapGuard(),
                mock(SourceReservationSupport.class));

        holdPair = PairedPostScheduler.class.getDeclaredMethod(
                "holdPair", PersonaRelationship.class, Instant.class, String.class,
                com.againspring.aiuser.orchestrator.service.threadplan.LlmCallBudget.class);
        holdPair.setAccessible(true);
    }

    private PlanSourceStoryResolver.ResolvedSource claimedSource() {
        return new PlanSourceStoryResolver.ResolvedSource(
                "팀장이 기획안을 가로챔",
                Map.of("incident", "팀장이 기획안을 자기 이름으로 보고함", "b_side_viable", true),
                true,
                SOURCE_EXAMPLE_ID,
                null, // sourceBody: overlap 체크는 이 테스트의 관심사가 아니므로 null(자동 통과)
                "natepan",
                "https://example.invalid/1",
                "원문 제목",
                "",
                List.of());
    }

    private void stubClaim() {
        when(sourceStoryResolver.claimAndResolve(any(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Optional.of(claimedSource()));
    }

    @Test
    void exceptionInGenerateCall1StillReleasesTheClaimedSourceExactlyOnce() {
        stubClaim();
        when(llmClient.generatePairedCall1(any()))
                .thenThrow(new RuntimeException("boom: unchecked failure inside generateCall1"));

        assertThat(assertInvocationThrows(REL, Instant.now(), "natepan", null))
                .hasMessageContaining("boom");

        verify(sourceStoryResolver, times(1)).release(eq(SOURCE_EXAMPLE_ID), anyString());
    }

    @Test
    void successfulHoldDoesNotReleaseTheClaimedSource() throws Exception {
        stubClaim();
        Map<String, Object> post = new LinkedHashMap<>();
        post.put("title", "제목");
        post.put("body", "본문 내용입니다");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("post", post);
        response.put("items", List.of());
        when(llmClient.generatePairedCall1(any())).thenReturn(Optional.of(response));

        Object result = holdPair.invoke(scheduler, REL, Instant.now(), "natepan", null);

        Method saved = result.getClass().getDeclaredMethod("saved");
        saved.setAccessible(true);
        assertThat((boolean) saved.invoke(result)).isTrue();
        verify(sourceStoryResolver, never()).release(anyLong(), anyString());
    }

    /** Invokes holdPair and unwraps the InvocationTargetException, returning the real cause. */
    private Throwable assertInvocationThrows(PersonaRelationship rel, Instant slot, String preferredSource,
                                             com.againspring.aiuser.orchestrator.service.threadplan.LlmCallBudget budget) {
        try {
            holdPair.invoke(scheduler, rel, slot, preferredSource, budget);
            throw new AssertionError("expected holdPair to propagate the unchecked exception");
        } catch (InvocationTargetException e) {
            return e.getCause();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
