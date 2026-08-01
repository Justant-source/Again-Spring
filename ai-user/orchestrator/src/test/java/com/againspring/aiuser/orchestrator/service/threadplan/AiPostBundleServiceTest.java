package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.StoryProfile;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.service.match.PersonaMatcherService;
import com.againspring.aiuser.orchestrator.service.storyprofile.StoryProfileAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AiPostBundleServiceTest {

    @Mock private AiUserGenerationConfigRepository configRepository;
    @Mock private OrchestratorProperties properties;
    @Mock private OrchestratorProperties.ThreadPlan threadPlan;
    @Mock private PersonaRepository personaRepository;
    @Mock private LlmAiUserClient llmClient;
    @Mock private BackendBotClient backendBot;
    @Mock private ContentSafetyGuard safetyGuard;
    @Mock private ThreadPlanService planService;
    @Mock private ThreadPlanGenerationService planGenerationService;
    @Mock private AiScheduledPostRepository scheduledPostRepository;
    @Mock private PlanPersonaMapper planPersonaMapper;
    @Mock private PlanSourceStoryResolver sourceStoryResolver;
    @Mock private StoryProfileAnalyzer storyProfileAnalyzer;
    @Mock private PersonaMatcherService personaMatcherService;

    private AiPostBundleService service;
    private CandidateScheduleSupport scheduleSupport;

    @BeforeEach
    void setUp() {
        when(properties.getThreadPlan()).thenReturn(threadPlan);
        when(threadPlan.getAiPostProvider()).thenReturn("CLAUDE");
        when(threadPlan.getAiPostModel()).thenReturn("claude-sonnet-4-6");
        when(threadPlan.getHumanPlanProvider()).thenReturn("CLAUDE");
        when(threadPlan.getHumanPlanModel()).thenReturn("");
        when(threadPlan.getBundleTimeoutMs()).thenReturn(240_000L);
        when(threadPlan.isMicroBatchEnabled()).thenReturn(true);
        when(threadPlan.resolvedMicroBatchSize()).thenReturn(5);
        // Matches the real application.yml default; an unstubbed int mock returns 0, which would
        // wrongly cap every mega-call test down to 1 persona (see capMegaCallCastBoundsSize* below
        // for the dedicated cap-behavior tests instead).
        when(threadPlan.getPlanPersonaCastMax()).thenReturn(40);
        scheduleSupport = new CandidateScheduleSupport(properties);
        when(storyProfileAnalyzer.analyze(any(), any(), any(), any(), any())).thenReturn(
                new StoryProfile("갈등", "OTHER",
                        List.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        "NATEPAN", List.of(), "", ""));
        when(personaMatcherService.matchCommenters(any(), any(Integer.class), anyLong(), anyString()))
                .thenReturn(List.of());
        service = new AiPostBundleService(
                configRepository, properties, personaRepository, llmClient, backendBot,
                safetyGuard, planService, planGenerationService, scheduledPostRepository,
                scheduleSupport, new ObjectMapper(), planPersonaMapper, sourceStoryResolver,
                storyProfileAnalyzer, personaMatcherService);
    }

    @Test
    void generateAndHoldSendsAuthorVoiceAndSourceContextNotEmptyTopicOnly() {
        when(threadPlan.isMicroBatchEnabled()).thenReturn(false);
        Persona author = persona("ai-user-1", "polite", "실닉네임아님");
        Persona other = persona("ai-user-2", "casual", null);
        AiUserGenerationConfig cfg = config("CLAUDE", 16);
        when(configRepository.findById(1)).thenReturn(Optional.of(cfg));
        when(personaRepository.findByActiveTrue()).thenReturn(List.of(author, other));

        Map<String, Object> authorMap = Map.of(
                "personaId", "ai-user-1",
                "nickname", "밤하늘여행",
                "formality", "polite",
                "voiceProfile", Map.of("formality", "polite", "voice_type", "NATEPAN"));
        Map<String, Object> otherMap = Map.of(
                "personaId", "ai-user-2",
                "nickname", "커피중독",
                "formality", "casual",
                "voiceProfile", Map.of("formality", "casual"));
        when(planPersonaMapper.mapCast(any())).thenReturn(List.of(authorMap, otherMap));
        when(planPersonaMapper.castIds(any())).thenReturn(Set.of("ai-user-1", "ai-user-2"));
        when(planPersonaMapper.mapAuthor(author)).thenReturn(authorMap);

        PlanSourceStoryResolver.ResolvedSource source = new PlanSourceStoryResolver.ResolvedSource(
                "시어머니가 육아에 간섭한다",
                Map.of("exampleId", 99L, "body", "원본 사연", "reconstructMode", true, "sourceUrl", "https://nate.example/1"),
                true, 99L, "원본 사연 본문", "natepan", "https://nate.example/1", "원제목",
                "문체앵커", List.of("내가 예전에 쓴 글"));
        when(sourceStoryResolver.resolve(author, "FAMILY", null)).thenReturn(source);

        Map<String, Object> llmResponse = new LinkedHashMap<>();
        llmResponse.put("post", Map.of("title", "시어머니 간섭", "body", "육아 갈등 본문입니다. 충분히 길게."));
        llmResponse.put("items", List.of(Map.of("ref", "c1", "personaId", "ai-user-2", "body", "공감 댓글")));
        when(llmClient.generateThreadPlan(any())).thenReturn(Optional.of(llmResponse));
        when(safetyGuard.check(any(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(scheduledPostRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<AiScheduledPost> held = service.generateAndHold(
                author, "FAMILY", null, "corr-test", Instant.parse("2026-08-01T01:00:00Z"));

        assertThat(held).isPresent();
        ArgumentCaptor<Map<String, Object>> reqCaptor = ArgumentCaptor.forClass(Map.class);
        verify(llmClient).generateThreadPlan(reqCaptor.capture());
        Map<String, Object> req = reqCaptor.getValue();

        assertThat(req.get("author")).isEqualTo(authorMap);
        assertThat(req.get("sourceContext")).isInstanceOf(Map.class);
        assertThat(req.get("topicHint")).isEqualTo("시어머니가 육아에 간섭한다");
        assertThat(req.get("topicHint")).isNotEqualTo("");
        assertThat(req.get("reconstructMode")).isEqualTo(true);
        assertThat(req.get("sourceExampleId")).isEqualTo(99L);
        assertThat(req.get("recentOutputs")).isInstanceOf(List.class);
        assertThat(req.get("storySearchDoc")).isInstanceOf(String.class);
        assertThat(req.get("storyProfile")).isInstanceOf(Map.class);
        assertThat(req.get("minTopLevel")).isEqualTo(1);
        assertThat(req.get("minItems")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> personas = (List<Map<String, Object>>) req.get("personas");
        assertThat(personas).hasSize(2);
        assertThat(personas.get(0).get("nickname")).isEqualTo("밤하늘여행");
        assertThat(personas.get(0).get("formality")).isEqualTo("polite");
        assertThat(personas.get(0).get("voiceProfile")).isInstanceOf(Map.class);
        assertThat(String.valueOf(personas.get(0).get("voiceProfile"))).doesNotContain("String.valueOf");

        assertThat(held.get().getCandidatesJson()).contains("sourceExampleId");
        assertThat(held.get().getCandidatesJson()).contains("99");
    }

    @Test
    void generateBundleIncludesFullActivePoolWithoutLimit24WhenMicroBatchDisabled() {
        when(threadPlan.isMicroBatchEnabled()).thenReturn(false);
        Persona author = persona("ai-user-1", "casual", null);
        List<Persona> pool = new ArrayList<>();
        pool.add(author);
        for (int i = 2; i <= 30; i++) pool.add(persona("ai-user-" + i, "casual", null));

        AiUserGenerationConfig cfg = config("CLAUDE", 16);
        when(configRepository.findById(1)).thenReturn(Optional.of(cfg));
        when(personaRepository.findByActiveTrue()).thenReturn(pool);

        List<Map<String, Object>> cast = pool.stream()
                .<Map<String, Object>>map(p -> Map.of(
                        "personaId", p.getId(),
                        "nickname", "nick-" + p.getId(),
                        "formality", "casual",
                        "voiceProfile", Map.of("formality", "casual")))
                .toList();
        when(planPersonaMapper.mapCast(pool)).thenReturn(cast);
        when(planPersonaMapper.castIds(any())).thenReturn(
                pool.stream().map(Persona::getId).collect(Collectors.toSet()));
        when(planPersonaMapper.mapAuthor(author)).thenReturn(cast.get(0));
        when(sourceStoryResolver.resolve(any(), any(), any())).thenReturn(
                new PlanSourceStoryResolver.ResolvedSource("seed", Map.of("body", "s"), false,
                        1L, null, null, null, null, "", List.of()));

        Map<String, Object> llmResponse = new LinkedHashMap<>();
        llmResponse.put("post", Map.of("title", "제목입니다", "body", "본문입니다. 충분히 길게 작성."));
        llmResponse.put("items", List.of());
        when(llmClient.generateThreadPlan(any())).thenReturn(Optional.of(llmResponse));
        when(safetyGuard.check(any(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(scheduledPostRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.generateAndHold(author, "WORK", null, "corr-pool", Instant.now());

        ArgumentCaptor<Map<String, Object>> reqCaptor = ArgumentCaptor.forClass(Map.class);
        verify(llmClient).generateThreadPlan(reqCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> personas = (List<Map<String, Object>>) reqCaptor.getValue().get("personas");
        assertThat(personas).hasSize(30);
    }

    @Test
    void microBatchIssuesAtLeastTwoLlmCallsWhenCastIsLarge() {
        Persona author = persona("ai-user-1", "casual", null);
        List<Persona> pool = new ArrayList<>();
        pool.add(author);
        for (int i = 2; i <= 12; i++) pool.add(persona("ai-user-" + i, "casual", null));

        AiUserGenerationConfig cfg = config("CLAUDE", 16);
        when(configRepository.findById(1)).thenReturn(Optional.of(cfg));
        when(personaRepository.findByActiveTrue()).thenReturn(pool);

        List<Map<String, Object>> cast = pool.stream()
                .<Map<String, Object>>map(p -> Map.of(
                        "personaId", p.getId(),
                        "nickname", "nick-" + p.getId(),
                        "formality", "casual",
                        "voiceProfile", Map.of("formality", "casual")))
                .toList();
        when(planPersonaMapper.mapCast(pool)).thenReturn(cast);
        when(planPersonaMapper.castIds(any())).thenReturn(
                pool.stream().map(Persona::getId).collect(Collectors.toSet()));
        when(planPersonaMapper.mapAuthor(author)).thenReturn(cast.get(0));
        when(sourceStoryResolver.resolve(any(), any(), any())).thenReturn(
                new PlanSourceStoryResolver.ResolvedSource("seed", Map.of("body", "s"), false,
                        1L, "본문", null, null, null, "", List.of()));

        AtomicInteger call = new AtomicInteger();
        when(llmClient.generateThreadPlan(any())).thenAnswer(inv -> {
            int n = call.getAndIncrement();
            Map<String, Object> resp = new LinkedHashMap<>();
            if (n == 0) {
                resp.put("post", Map.of("title", "제목입니다", "body", "본문입니다. 충분히 길게 작성."));
                resp.put("items", List.of(
                        Map.of("ref", "c1", "personaId", "ai-user-2", "body", "첫 배치 댓글"),
                        Map.of("ref", "c2", "personaId", "ai-user-3", "body", "첫 배치 댓글2")));
            } else {
                resp.put("post", null);
                resp.put("items", List.of(
                        Map.of("ref", "c1", "personaId", "ai-user-7", "body", "둘째 배치 댓글")));
            }
            return Optional.of(resp);
        });
        when(safetyGuard.check(any(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(scheduledPostRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<AiScheduledPost> held = service.generateAndHold(
                author, "WORK", null, "corr-micro", Instant.now());

        assertThat(held).isPresent();
        ArgumentCaptor<Map<String, Object>> reqCaptor = ArgumentCaptor.forClass(Map.class);
        verify(llmClient, atLeast(2)).generateThreadPlan(reqCaptor.capture());
        List<Map<String, Object>> requests = reqCaptor.getAllValues();
        assertThat(requests.size()).isGreaterThanOrEqualTo(2);

        Map<String, Object> first = requests.get(0);
        assertThat(first.get("kind")).isEqualTo("AI_POST");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> firstPersonas = (List<Map<String, Object>>) first.get("personas");
        // author + first micro-batch (5)
        assertThat(firstPersonas).hasSize(6);
        assertThat(first.get("minTopLevel")).isEqualTo(1);
        assertThat(first.get("minItems")).isEqualTo(1);

        Map<String, Object> second = requests.get(1);
        assertThat(second.get("kind")).isEqualTo("HUMAN_POST");
        assertThat(second.get("existingTitle")).isEqualTo("제목입니다");
        assertThat(second.get("existingBody")).isEqualTo("본문입니다. 충분히 길게 작성.");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> secondPersonas = (List<Map<String, Object>>) second.get("personas");
        assertThat(secondPersonas).isNotEmpty();
        assertThat(secondPersonas.size()).isLessThanOrEqualTo(5);
        assertThat(secondPersonas.get(0).get("personaId")).isNotEqualTo("ai-user-1");

        assertThat(held.get().getCandidatesJson()).contains("b0_c1");
        assertThat(held.get().getCandidatesJson()).contains("b1_c1");
        verify(llmClient, times(requests.size())).generateThreadPlan(any());
    }

    @Test
    void countOutOfCastReportsPersonaOutsideRequestedCast() {
        Map<String, Object> response = Map.of("items", List.of(
                Map.of("ref", "c1", "personaId", "outsider", "body", "x"),
                Map.of("ref", "c2", "personaId", "ai-user-1", "body", "y")));
        assertThat(AiPostBundleService.countOutOfCast(response, Set.of("ai-user-1", "ai-user-2")))
                .isEqualTo(1);
        // Soft validateCast must not reject the whole bundle (quality gate drops later).
        AiPostBundleService.validateCast(response, Set.of("ai-user-1", "ai-user-2"));
    }

    @Test
    void sliceCommentersRespectsBatchSizeClamp() {
        List<Map<String, Object>> commenters = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            commenters.add(Map.of("personaId", "p" + i));
        }
        List<List<Map<String, Object>>> slices = AiPostBundleService.sliceCommenters(commenters, 5);
        assertThat(slices).hasSize(3);
        assertThat(slices.get(0)).hasSize(5);
        assertThat(slices.get(1)).hasSize(5);
        assertThat(slices.get(2)).hasSize(1);
    }

    private static Persona persona(String id, String formality, String ignored) {
        return Persona.builder()
                .id(id)
                .archetype("ARCH")
                .tier("HEAVY")
                .voiceProfile(new LinkedHashMap<>(Map.of("formality", formality, "voice_type", "NATEPAN")))
                .interests(Map.of("FAMILY", 0.9))
                .biasProfile(Map.of())
                .circadian(List.of())
                .slangLevel(new BigDecimal("0.40"))
                .active(true)
                .createdAt(Instant.now())
                .build();
    }

    private static AiUserGenerationConfig config(String provider, int pool) {
        try {
            var ctor = AiUserGenerationConfig.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            AiUserGenerationConfig c = ctor.newInstance();
            setField(c, "providerAiPostBundle", provider);
            setField(c, "candidatePoolSize", pool);
            setField(c, "aiUserKillSwitch", false);
            return c;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var f = AiUserGenerationConfig.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /**
     * 2026-08-01 회귀 방지: micro-batch가 비활성일 때 쓰이는 fallback 경로도
     * 전체 cast를 그대로 보내면 같은 토큰 초과를 재현한다. author(index 0)는 유지돼야 한다.
     */
    @Test
    void capMegaCallCastBoundsSizeAndKeepsAuthorFirst() {
        List<Map<String, Object>> personas = new java.util.ArrayList<>();
        Map<String, Object> author = Map.of("personaId", "author-1");
        personas.add(author);
        for (int i = 0; i < 149; i++) personas.add(Map.of("personaId", "p" + i));

        List<Map<String, Object>> capped = AiPostBundleService.capMegaCallCast(personas, 40);

        assertThat(capped).hasSize(40);
        assertThat(capped.get(0)).isEqualTo(author);
    }

    @Test
    void capMegaCallCastReturnsUnchangedWhenAlreadyWithinBound() {
        List<Map<String, Object>> personas = List.of(Map.of("personaId", "a"), Map.of("personaId", "b"));
        assertThat(AiPostBundleService.capMegaCallCast(personas, 40)).hasSize(2);
    }
}
