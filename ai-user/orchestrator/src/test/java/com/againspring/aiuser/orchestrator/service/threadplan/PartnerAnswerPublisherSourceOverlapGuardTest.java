package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPartnerAnswer;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPartnerAnswerStatus;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.safety.SourceOverlapGuard;
import com.againspring.aiuser.orchestrator.service.GenerationConfigSupport;
import com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService;
import com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache;
import com.againspring.aiuser.orchestrator.service.persona.PersonaLottery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 코드리뷰 #1 — 상대방(B) 시점 글 재구성 결과도 {@link SourceOverlapGuard}를 거치는지 검증한다.
 * Call2({@code PartnerAnswerPublisher})는 Call1({@code PairedPostScheduler})과 다른 lease/row라
 * claim 시점의 원문이 메모리에 없다 — {@code _pairedSkeleton.sourceExampleId}로
 * {@link AiLearningClient#getExampleById} 재조회해 대조하는 배선(대안 a)을 확인한다.
 *
 * <p>solo/paired-A 경로 테스트({@code AiPostBundleServiceTest}·
 * {@code ActionExecutorSourceOverlapGuardTest})와 동일한 RAW_SOURCE/VERBATIM/PARAPHRASED 3종
 * 텍스트를 재사용해 대칭성을 유지한다.</p>
 */
class PartnerAnswerPublisherSourceOverlapGuardTest {

    private static final String WORKER = "partner-answer-publisher";
    private static final Long SOURCE_EXAMPLE_ID = 4242L;

    /** 크롤 원문 — 검사 후 버려질 뿐, 로그·DB 저장 문자열로 등장하면 안 된다. */
    private static final String RAW_SOURCE =
            "어제 회사에서 팀장이 갑자기 나한테 화를 내면서 다른 사람들이 다 보는 앞에서 대놓고 창피를 줬다. "
                    + "나는 정말 억울했고 화가 나서 참을 수가 없었는데 아무 말도 못 하고 그냥 서 있기만 했다. "
                    + "이런 일이 자꾸 반복되면 진짜 이 회사 그만두고 싶다는 생각까지 든다.";

    /** RAW_SOURCE를 거의 그대로 복붙한 "생성" 결과 — 12-gram overlap이 임계(0.20)를 크게 넘는다. */
    private static final String VERBATIM_COPY_BODY = RAW_SOURCE;

    /** 같은 소재를 완전히 다른 어휘·문장구조로 재서술한 결과 — overlap이 임계 이하다. */
    private static final String PARAPHRASED_BODY =
            "오늘 낮에 사무실에서 부장님이 별일도 아닌 걸로 갑자기 언성을 높이더니 동료들 다 있는 자리에서 "
                    + "저를 심하게 몰아붙이셨어요. 너무 당황스럽고 속상해서 그 자리에서는 그냥 아무 반응도 못 했는데 "
                    + "집에 와서 생각해보니 계속 눈물만 나네요. 이직을 진지하게 고민하게 됩니다.";

    private PartnerAnswerLeaseService leases;
    private PersonaRepository personas;
    private LlmAiUserClient llmClient;
    private BackendBotClient backend;
    private AiLearningClient aiLearningClient;
    private JdbcTemplate jdbcTemplate;
    private AiUserGenerationConfigRepository generationConfigRepository;
    private PartnerAnswerPublisher publisher;

    private static final String ROW_ID = "row-1";
    private static final String SCHEDULED_POST_ID = "sched-1";

    @BeforeEach
    void setUp() {
        leases = mock(PartnerAnswerLeaseService.class);
        personas = mock(PersonaRepository.class);
        llmClient = mock(LlmAiUserClient.class);
        backend = mock(BackendBotClient.class);
        ContentSafetyGuard safetyGuard = mock(ContentSafetyGuard.class);
        SourceOverlapGuard sourceOverlapGuard = new SourceOverlapGuard();
        aiLearningClient = mock(AiLearningClient.class);
        ThreadPlanGenerationService threadPlanGenerationService = mock(ThreadPlanGenerationService.class);
        generationConfigRepository = mock(AiUserGenerationConfigRepository.class);
        PlanPersonaMapper planPersonaMapper = mock(PlanPersonaMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        GenerationConfigSupport generationConfigSupport = mock(GenerationConfigSupport.class);
        LlmGenerationGateService llmGenerationGateService = mock(LlmGenerationGateService.class);
        PromptTemplateCache promptTemplateCache = mock(PromptTemplateCache.class);
        PersonaLottery personaLottery = mock(PersonaLottery.class);

        OrchestratorProperties props = new OrchestratorProperties();
        props.setEnabled(true);
        props.getPairedPost().setEnabled(true);
        props.getPairedPost().setPartnerPublisherEnabled(true);
        props.getPairedPost().setPartnerPublishBatchSize(5);

        AiUserGenerationConfig config = mock(AiUserGenerationConfig.class);
        when(config.isAiUserKillSwitch()).thenReturn(false);
        when(config.isScheduleExecutionPaused()).thenReturn(false);
        when(config.getProviderAiPostBundle()).thenReturn("CLAUDE");
        when(generationConfigRepository.findById(1)).thenReturn(Optional.of(config));

        Persona partner = Persona.builder().id("partner-1").build();
        when(personas.findById("partner-1")).thenReturn(Optional.of(partner));
        when(personas.findByActiveTrue()).thenReturn(List.of());
        when(personaLottery.drawCommenters(any(), any(), any(), anyInt(), any())).thenReturn(List.of());
        when(planPersonaMapper.mapAuthor(any())).thenReturn(Map.of());
        when(planPersonaMapper.mapCast(any())).thenReturn(List.of());
        when(promptTemplateCache.overrides()).thenReturn(Map.of());
        when(generationConfigSupport.bundleTimeoutMs()).thenReturn(10_000L);
        when(llmGenerationGateService.isHeld()).thenReturn(false);
        when(threadPlanGenerationService.loadLatestPublishedTopLevelComments(anyString(), anyInt()))
                .thenReturn(List.of());
        when(safetyGuard.check(anyString(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(backend.submitPartnerAnswer(anyString(), any(), anyString(), any())).thenReturn(true);

        // Call1 hold row(candidates_json)에 실린 계약7 골격 — sourceExampleId만 있으면 충분.
        String candidatesJson = "{\"_pairedSkeleton\":{\"sourceExampleId\":" + SOURCE_EXAMPLE_ID + "}}";
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any()))
                .thenReturn(candidatesJson);

        publisher = new PartnerAnswerPublisher(
                leases, personas, llmClient, backend, safetyGuard, sourceOverlapGuard, aiLearningClient,
                threadPlanGenerationService, props, generationConfigRepository, planPersonaMapper,
                jdbcTemplate, generationConfigSupport, llmGenerationGateService, promptTemplateCache,
                personaLottery);
    }

    private static AiScheduledPartnerAnswer row(String partnerBodyIrrelevant) {
        return AiScheduledPartnerAnswer.builder()
                .id(ROW_ID)
                .postId("post-1")
                .inviteToken("invite-1")
                .authorPersonaId("author-1")
                .partnerPersonaId("partner-1")
                .category("COUPLE")
                .authorTitle("갈등 사연")
                .authorBody("작성자 본문")
                .correlationId("corr-1")
                .scheduledPostId(SCHEDULED_POST_ID)
                .scheduledPartnerAt(Instant.now())
                .status(ScheduledPartnerAnswerStatus.PUBLISHING)
                .leaseOwner(WORKER)
                .attemptCount(0)
                .build();
    }

    private void stubCall2Response(String partnerBody) {
        Map<String, Object> partnerPost = Map.of("body", partnerBody);
        Map<String, Object> response = Map.of("partner_post", partnerPost);
        when(llmClient.generatePairedCall2(any())).thenReturn(Optional.of(response));
    }

    private void stubLeaseClaim(AiScheduledPartnerAnswer r) {
        when(leases.claimDue(eq(WORKER), anyInt(), any(Duration.class), any(Instant.class)))
                .thenReturn(List.of(r));
    }

    @Test
    void verbatimPartnerBodyIsRejectedAndNeverSubmitted() {
        AiScheduledPartnerAnswer r = row(VERBATIM_COPY_BODY);
        stubLeaseClaim(r);
        stubCall2Response(VERBATIM_COPY_BODY);
        AiLearningClient.ExampleItem original = new AiLearningClient.ExampleItem();
        original.setId(SOURCE_EXAMPLE_ID);
        original.setContent(RAW_SOURCE);
        original.setSource("natepan");
        when(aiLearningClient.getExampleById(SOURCE_EXAMPLE_ID)).thenReturn(Optional.of(original));

        publisher.publishDue();

        verify(backend, never()).submitPartnerAnswer(anyString(), any(), anyString(), any());
        verify(leases).releaseFailed(ROW_ID, WORKER, "CALL2_SOURCE_OVERLAP", false);
        verify(leases, never()).complete(anyString(), anyString());
    }

    @Test
    void paraphrasedPartnerBodyPassesAndIsSubmitted() {
        AiScheduledPartnerAnswer r = row(PARAPHRASED_BODY);
        stubLeaseClaim(r);
        stubCall2Response(PARAPHRASED_BODY);
        AiLearningClient.ExampleItem original = new AiLearningClient.ExampleItem();
        original.setId(SOURCE_EXAMPLE_ID);
        original.setContent(RAW_SOURCE);
        original.setSource("natepan");
        when(aiLearningClient.getExampleById(SOURCE_EXAMPLE_ID)).thenReturn(Optional.of(original));

        publisher.publishDue();

        verify(backend).submitPartnerAnswer(eq("invite-1"), any(), eq(PARAPHRASED_BODY), any());
        verify(leases, never()).releaseFailed(anyString(), anyString(), eq("CALL2_SOURCE_OVERLAP"), org.mockito.ArgumentMatchers.anyBoolean());
        verify(leases).complete(ROW_ID, WORKER);
    }

    /**
     * example_bank 원문 재조회가 실패(비활성·네트워크 오류·행 삭제)하면 fail-open — 대조 없이
     * 통과한다. 이 배선 이전(대조 자체가 없던 상태)과 같은 위험 수준일 뿐 더 나쁘게 만들지 않는다.
     */
    @Test
    void sourceRefetchFailureFailsOpenAndStillSubmits() {
        AiScheduledPartnerAnswer r = row(VERBATIM_COPY_BODY);
        stubLeaseClaim(r);
        stubCall2Response(VERBATIM_COPY_BODY);
        when(aiLearningClient.getExampleById(SOURCE_EXAMPLE_ID)).thenReturn(Optional.empty());

        publisher.publishDue();

        verify(backend).submitPartnerAnswer(eq("invite-1"), any(), eq(VERBATIM_COPY_BODY), any());
        verify(leases, never()).releaseFailed(anyString(), anyString(), eq("CALL2_SOURCE_OVERLAP"), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void missingSkeletonSkipsOverlapCheckEntirely() {
        AiScheduledPartnerAnswer r = row(VERBATIM_COPY_BODY);
        stubLeaseClaim(r);
        stubCall2Response(VERBATIM_COPY_BODY);
        // candidates_json 조회 자체가 골격 없이 비어있는 상황(claim 실패·freestyle).
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any())).thenReturn(null);

        publisher.publishDue();

        verify(aiLearningClient, never()).getExampleById(org.mockito.ArgumentMatchers.anyLong());
        verify(backend).submitPartnerAnswer(eq("invite-1"), any(), eq(VERBATIM_COPY_BODY), any());
    }

    @Test
    void sanityRawOverlapAssumptionsHold() {
        // 테스트 상수 자체가 12-gram overlap 임계를 실제로 넘고/못 넘는지 회귀 확인.
        SourceOverlapGuard guard = new SourceOverlapGuard();
        assertThat(guard.check(VERBATIM_COPY_BODY, RAW_SOURCE).passed()).isFalse();
        assertThat(guard.check(PARAPHRASED_BODY, RAW_SOURCE).passed()).isTrue();
    }
}
