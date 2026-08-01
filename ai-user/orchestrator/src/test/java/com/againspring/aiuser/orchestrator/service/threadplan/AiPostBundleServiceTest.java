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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        when(threadPlan.getBundleTimeoutMs()).thenReturn(240_000L);
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
    void generateBundleIncludesFullActivePoolWithoutLimit24() {
        Persona author = persona("ai-user-1", "casual", null);
        List<Persona> pool = new java.util.ArrayList<>();
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
                pool.stream().map(Persona::getId).collect(java.util.stream.Collectors.toSet()));
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
    void validateCastRejectsPersonaOutsideRequestedCast() {
        Map<String, Object> response = Map.of("items", List.of(
                Map.of("ref", "c1", "personaId", "outsider", "body", "x")));
        assertThatThrownBy(() -> AiPostBundleService.validateCast(response, Set.of("ai-user-1", "ai-user-2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in requested cast");
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
}
