package com.againspring.aiuser.orchestrator.task;

import com.againspring.aiuser.orchestrator.auth.BotTokenCache;
import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.client.AiUserMlClient;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.CreatePostDto;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaActionLog;
import com.againspring.aiuser.orchestrator.engine.ArchetypeCatalog;
import com.againspring.aiuser.orchestrator.engine.PlannedAction;
import com.againspring.aiuser.orchestrator.repository.AiGlobalRuleRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaActionLogRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaSeenPostRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.safety.SourceOverlapGuard;
import com.againspring.aiuser.orchestrator.service.PersonaHistoryStore;
import com.againspring.aiuser.orchestrator.service.llm.LlmCircuitBreaker;
import com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache;
import com.againspring.aiuser.orchestrator.service.threadplan.AiPostBundleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * persona-diversity-v4 WP2 item5 — 레거시 {@code /generate/post} 경로({@link ActionExecutor#execute})의
 * 재구성 분기가 {@link SourceOverlapGuard}를 실제로 거친다는 것을 확인한다.
 * solo 경로({@code AiPostBundleServiceTest})와 대칭되는 테스트: 원문을 그대로 복붙한 생성 결과는
 * 게시가 거부되고(BackendBotClient#createPost 호출 없음), 정상 재서술은 게시된다.
 *
 * <p>이 클래스의 {@code executePost}는 R9 Track B(CASUAL/CONFLICT) 확률 분기를 private static
 * {@code RNG}로 직접 굴린다 — 필드 주입 지점이 없어 시드 고정이 불가능하다. 재구성 분기는
 * CONFLICT(비casual)일 때만 {@code AiLearningClient#findSimilar}를 호출하므로, 매 시도마다
 * 새 mock 세트로 {@code execute()}를 돌려 그 호출이 실제로 일어난 시도만 채택한다
 * (기본 CASUAL 확률 25% → 300회 중 한 번도 비casual이 안 나올 확률은 사실상 0).</p>
 */
class ActionExecutorSourceOverlapGuardTest {

    private static final int MAX_ATTEMPTS = 300;

    /** 크롤 원문 — 검사 후 버려질 뿐, 어떤 assertion에서도 로그·DB 저장 문자열로 등장하면 안 된다. */
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

    private record Harness(
            ActionExecutor executor,
            BackendBotClient backendBot,
            PersonaActionLogRepository actionLogRepo,
            AtomicBoolean reconstructionAttempted) {
    }

    private Harness buildHarness(String generatedBody, String primarySourceContent) {
        BotTokenCache tokenCache = mock(BotTokenCache.class);
        BackendBotClient backendBot = mock(BackendBotClient.class);
        LlmAiUserClient llmClient = mock(LlmAiUserClient.class);
        ContentSafetyGuard safetyGuard = mock(ContentSafetyGuard.class);
        PersonaSeenPostRepository seenPostRepo = mock(PersonaSeenPostRepository.class);
        PersonaActionLogRepository actionLogRepo = mock(PersonaActionLogRepository.class);
        OrchestratorProperties props = mock(OrchestratorProperties.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ArchetypeCatalog archetypeCatalog = mock(ArchetypeCatalog.class);
        AiLearningClient aiLearningClient = mock(AiLearningClient.class);
        AiUserMlClient aiUserMlClient = mock(AiUserMlClient.class);
        AiGlobalRuleRepository aiGlobalRuleRepository = mock(AiGlobalRuleRepository.class);
        AiUserGenerationConfigRepository generationConfigRepository = mock(AiUserGenerationConfigRepository.class);
        PersonaHistoryStore personaHistoryStore = mock(PersonaHistoryStore.class);
        AiPostBundleService aiPostBundleService = mock(AiPostBundleService.class);
        LlmCircuitBreaker circuitBreaker = mock(LlmCircuitBreaker.class);
        PromptTemplateCache promptTemplateCache = mock(PromptTemplateCache.class);
        SourceOverlapGuard sourceOverlapGuard = new SourceOverlapGuard();

        // no_jwt 조기 종료 방지 — botEmail()은 jdbcTemplate mock 기본값(null)으로도 폴백되어 안전.
        when(tokenCache.getToken(anyString(), any(), any())).thenReturn(Optional.of("jwt-token"));

        // AiPostBundleService.ownsPostGeneration() 기본 false(Mockito 기본 boolean)로 legacy 분기 진입.

        // 재구성 원본 — source_url 보유(hasSourceProvenance=true)로 findSimilar 1순위 결과가 됨.
        AiLearningClient.ExampleItem primarySource = new AiLearningClient.ExampleItem();
        primarySource.setId(4242L);
        primarySource.setTitle("원본 제목");
        primarySource.setContent(primarySourceContent);
        primarySource.setSourceUrl("https://example-crawl.invalid/post/4242");
        primarySource.setSource("NATEPAN");
        primarySource.setCategory("WORK");

        AtomicBoolean reconstructionAttempted = new AtomicBoolean(false);
        when(aiLearningClient.findSimilar(any(), anyString(), anyString(), anyInt(), any()))
                .thenAnswer(inv -> {
                    reconstructionAttempted.set(true);
                    return List.of(primarySource);
                });

        when(llmClient.extractSkeleton(eq(primarySource.getId()), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(Map.of("incident", "직장 상사에게 공개적으로 질책당한 일", "category", "WORK")));

        when(llmClient.generatePost(any())).thenReturn(Optional.of(generatedBody));

        when(safetyGuard.check(anyString(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());

        when(backendBot.createPost(anyString(), any(CreatePostDto.class)))
                .thenReturn(Optional.of(postDto("post-1")));

        ActionExecutor executor = new ActionExecutor(
                tokenCache, backendBot, llmClient, safetyGuard, seenPostRepo, actionLogRepo,
                props, new ObjectMapper(), jdbcTemplate, archetypeCatalog, aiLearningClient,
                aiUserMlClient, aiGlobalRuleRepository, generationConfigRepository,
                personaHistoryStore, aiPostBundleService, circuitBreaker, promptTemplateCache,
                sourceOverlapGuard);

        return new Harness(executor, backendBot, actionLogRepo, reconstructionAttempted);
    }

    private static PostDto postDto(String id) {
        PostDto dto = new PostDto();
        dto.setId(id);
        return dto;
    }

    private static Persona persona() {
        return Persona.builder()
                .id("ai-user-overlap-test")
                .archetype("VENT_HEAVY")
                .tier("HEAVY")
                .voiceProfile(Map.of("formality", "casual", "voice_type", "NATEPAN"))
                .interests(Map.of("WORK", 1.0))
                .build();
    }

    /** eq() 매처 — extractSkeleton의 Long 인자용. */
    private static Long eq(Long value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    @Test
    void verbatimCopyIsRejectedAndNeverPublished() {
        Harness harness = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            harness = buildHarness(makeGenerated(VERBATIM_COPY_BODY), RAW_SOURCE);
            harness.executor().execute(persona(), PlannedAction.newPost());
            if (harness.reconstructionAttempted().get()) {
                break;
            }
        }
        assertThat(harness).isNotNull();
        assertThat(harness.reconstructionAttempted().get())
                .as("재구성(비-casual) 분기가 %d회 시도 안에 한 번도 발동하지 않음 — RNG 분포 이상 의심", MAX_ATTEMPTS)
                .isTrue();

        verify(harness.backendBot(), never()).createPost(anyString(), any(CreatePostDto.class));
        verify(harness.actionLogRepo()).save(argThatStatusIs("FAILED"));
    }

    @Test
    void paraphrasedRewritePassesAndIsPublished() {
        Harness harness = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            harness = buildHarness(makeGenerated(PARAPHRASED_BODY), RAW_SOURCE);
            harness.executor().execute(persona(), PlannedAction.newPost());
            if (harness.reconstructionAttempted().get()) {
                break;
            }
        }
        assertThat(harness).isNotNull();
        assertThat(harness.reconstructionAttempted().get())
                .as("재구성(비-casual) 분기가 %d회 시도 안에 한 번도 발동하지 않음 — RNG 분포 이상 의심", MAX_ATTEMPTS)
                .isTrue();

        verify(harness.backendBot()).createPost(anyString(), any(CreatePostDto.class));
    }

    /** 제목 라인 + 본문 — executePost의 extractTitle이 첫 줄을 제목으로 뽑는다. */
    private static String makeGenerated(String body) {
        return "공개하고 싶은 이야기\n" + body;
    }

    private static PersonaActionLog argThatStatusIs(String status) {
        return org.mockito.ArgumentMatchers.argThat(log ->
                log != null && status.equals(log.getStatus()));
    }
}
