package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.service.PersonaHistoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanSourceStoryResolverTest {

    @Mock private AiLearningClient aiLearningClient;
    @Mock private PersonaHistoryStore personaHistoryStore;
    @InjectMocks private PlanSourceStoryResolver resolver;

    @Test
    void resolveUsesFindSimilarWithRegisterAndExcludesSelfGeneratedPath() {
        Persona author = Persona.builder()
                .id("ai-user-1")
                .archetype("FAMILY_BOUND")
                .tier("HEAVY")
                .voiceProfile(new LinkedHashMap<>(Map.of("formality", "polite")))
                .interests(Map.of("FAMILY", 1.0))
                .biasProfile(Map.of())
                .circadian(List.of())
                .slangLevel(new BigDecimal("0.2"))
                .active(true)
                .createdAt(Instant.now())
                .build();

        AiLearningClient.ExampleItem withUrl = new AiLearningClient.ExampleItem();
        withUrl.setId(42L);
        withUrl.setContent("시어머니 갈등이 심하다");
        withUrl.setSource("natepan");
        withUrl.setSourceUrl("https://nate.example/post/1");
        withUrl.setTitle("시어머니");

        when(aiLearningClient.fetchDailyTopics("FAMILY", 5)).thenReturn(List.of());
        when(aiLearningClient.findSimilar(anyString(), eq("POST"), eq("FAMILY"), eq(3), eq("polite")))
                .thenReturn(List.of(withUrl));
        when(personaHistoryStore.loadRecentPosts(eq("ai-user-1"), anyInt()))
                .thenReturn(List.of("예전 글 본문"));

        PlanSourceStoryResolver.ResolvedSource resolved = resolver.resolve(author, "FAMILY", null);

        assertThat(resolved.reconstructMode()).isTrue();
        assertThat(resolved.sourceExampleId()).isEqualTo(42L);
        assertThat(resolved.sourceContext().get("sourceUrl")).isEqualTo("https://nate.example/post/1");
        assertThat(resolved.recentBodies()).containsExactly("예전 글 본문");
        assertThat(resolved.topicSeed()).isNotBlank();

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        verify(aiLearningClient).findSimilar(queryCap.capture(), eq("POST"), eq("FAMILY"), eq(3), eq("polite"));
        // register overload sets excludeSelfGenerated=true on SearchRequest
        assertThat(queryCap.getValue()).contains("FAMILY");
    }

    @Test
    void blankTopicHintStillBuildsSeedNotEmptyOnlyPath() {
        Persona author = Persona.builder()
                .id("ai-user-2")
                .archetype("WORK_PRESSURE")
                .tier("REGULAR")
                .voiceProfile(Map.of("formality", "casual"))
                .interests(Map.of())
                .biasProfile(Map.of())
                .circadian(List.of())
                .slangLevel(new BigDecimal("0.5"))
                .active(true)
                .createdAt(Instant.now())
                .build();
        when(aiLearningClient.fetchDailyTopics("WORK", 5)).thenReturn(List.of());
        when(aiLearningClient.findSimilar(anyString(), eq("POST"), eq("WORK"), eq(3), eq("casual")))
                .thenReturn(List.of());
        when(personaHistoryStore.loadRecentPosts(anyString(), anyInt())).thenReturn(List.of());

        PlanSourceStoryResolver.ResolvedSource resolved = resolver.resolve(author, "WORK", "   ");
        assertThat(resolved.topicSeed()).isNotBlank();
        assertThat(resolved.topicSeed()).contains("WORK");
    }
}
