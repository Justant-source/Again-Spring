package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.service.PersonaHistoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanSourceStoryResolverTest {

    @Mock private AiLearningClient aiLearningClient;
    @Mock private PersonaHistoryStore personaHistoryStore;
    @InjectMocks private PlanSourceStoryResolver resolver;

    @Test
    void claimAndResolveEmptyWhenClaimMisses() {
        when(aiLearningClient.claimPopularSource(eq("blind"), eq("res-1"), any(Instant.class)))
                .thenReturn(Optional.empty());

        Optional<PlanSourceStoryResolver.ResolvedSource> resolved =
                resolver.claimAndResolve(null, "blind", "res-1", Instant.now().plus(1, ChronoUnit.HOURS), "FAMILY");

        assertThat(resolved).isEmpty();
        verify(aiLearningClient, never()).findSimilar(anyString(), anyString(), anyString(), anyInt());
        verify(aiLearningClient, never()).findSimilar(anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void claimAndResolveSetsReconstructFieldsAndSkipsFindSimilar() {
        Persona author = persona("ai-user-1", "BLIND", "polite");

        AiLearningClient.ExampleItem claimed = new AiLearningClient.ExampleItem();
        claimed.setId(42L);
        claimed.setContent("시어머니 갈등이 심하다 장문의 본문입니다");
        claimed.setSource("blind");
        claimed.setSourceUrl("https://blind.example/post/1");
        claimed.setTitle("시어머니");

        Instant until = Instant.now().plus(24, ChronoUnit.HOURS);
        when(aiLearningClient.claimPopularSource(eq("blind"), eq("sched-uuid"), eq(until)))
                .thenReturn(Optional.of(claimed));
        when(aiLearningClient.styleSample(eq("blind"), eq("POST"), eq("polite"), eq(2), eq(350)))
                .thenReturn(List.of());
        when(personaHistoryStore.loadRecentPosts(eq("ai-user-1"), anyInt()))
                .thenReturn(List.of("예전 글 본문"));

        Optional<PlanSourceStoryResolver.ResolvedSource> opt =
                resolver.claimAndResolve(author, "blind", "sched-uuid", until, "FAMILY");

        assertThat(opt).isPresent();
        PlanSourceStoryResolver.ResolvedSource resolved = opt.get();
        assertThat(resolved.reconstructMode()).isTrue();
        assertThat(resolved.sourceExampleId()).isEqualTo(42L);
        assertThat(resolved.sourceBody()).isEqualTo("시어머니 갈등이 심하다 장문의 본문입니다");
        assertThat(resolved.sourceUrl()).isEqualTo("https://blind.example/post/1");
        assertThat(resolved.sourceTitle()).isEqualTo("시어머니");
        assertThat(resolved.sourceCommunity()).isEqualTo("blind");
        assertThat(resolved.topicSeed()).isEqualTo("시어머니 갈등이 심하다 장문의 본문입니다");
        assertThat(resolved.sourceContext().get("reconstructMode")).isEqualTo(true);
        assertThat(resolved.sourceContext().get("sourceUrl")).isEqualTo("https://blind.example/post/1");
        assertThat(resolved.recentBodies()).containsExactly("예전 글 본문");

        verify(aiLearningClient).claimPopularSource("blind", "sched-uuid", until);
        verify(aiLearningClient, never()).findSimilar(anyString(), anyString(), anyString(), anyInt());
        verify(aiLearningClient, never()).findSimilar(anyString(), anyString(), anyString(), anyInt(), anyString());
        verify(aiLearningClient, never()).fetchDailyTopics(anyString(), anyInt());
    }

    @Test
    void claimAndResolveAllowsNullAuthor() {
        AiLearningClient.ExampleItem claimed = new AiLearningClient.ExampleItem();
        claimed.setId(7L);
        claimed.setContent("직장 갈등");
        claimed.setSource("natepan");
        claimed.setSourceUrl("https://nate.example/2");
        claimed.setTitle("직장");

        Instant until = Instant.now().plus(2, ChronoUnit.HOURS);
        when(aiLearningClient.claimPopularSource(eq("natepan"), eq("k"), eq(until)))
                .thenReturn(Optional.of(claimed));
        when(aiLearningClient.styleSample(eq("natepan"), eq("POST"), eq("casual"), eq(2), eq(350)))
                .thenReturn(List.of());

        Optional<PlanSourceStoryResolver.ResolvedSource> opt =
                resolver.claimAndResolve(null, "natepan", "k", until, null);

        assertThat(opt).isPresent();
        assertThat(opt.get().recentBodies()).isEmpty();
        assertThat(opt.get().reconstructMode()).isTrue();
        verify(personaHistoryStore, never()).loadRecentPosts(anyString(), anyInt());
        verify(aiLearningClient, never()).findSimilar(anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void legacyResolveMapsBlindVoiceAndClaims() {
        Persona author = persona("ai-user-blind", "BLIND", "casual");

        AiLearningClient.ExampleItem claimed = new AiLearningClient.ExampleItem();
        claimed.setId(99L);
        claimed.setContent("blind seed body");
        claimed.setSource("blind");
        claimed.setSourceUrl("https://blind.example/x");
        claimed.setTitle("t");

        when(aiLearningClient.claimPopularSource(eq("blind"), anyString(), any(Instant.class)))
                .thenReturn(Optional.of(claimed));
        when(aiLearningClient.styleSample(eq("blind"), eq("POST"), eq("casual"), eq(2), eq(350)))
                .thenReturn(List.of());
        when(personaHistoryStore.loadRecentPosts(eq("ai-user-blind"), anyInt()))
                .thenReturn(List.of());

        PlanSourceStoryResolver.ResolvedSource resolved = resolver.resolve(author, "WORK", null);

        assertThat(resolved.reconstructMode()).isTrue();
        assertThat(resolved.sourceExampleId()).isEqualTo(99L);
        verify(aiLearningClient).claimPopularSource(eq("blind"), anyString(), any(Instant.class));
        verify(aiLearningClient, never()).findSimilar(anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void legacyResolveThrowsWhenClaimEmpty_noFreestyle() {
        Persona author = persona("ai-user-2", "NATEPAN", "casual");
        when(aiLearningClient.claimPopularSource(eq("natepan"), anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(author, "WORK", "   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("natepan");

        verify(aiLearningClient, never()).findSimilar(anyString(), anyString(), anyString(), anyInt(), anyString());
        verify(aiLearningClient, never()).fetchDailyTopics(anyString(), anyInt());
    }

    @Test
    void preferredSourceFromVoiceMapsBlindAndDefaultsNatepan() {
        assertThat(PlanSourceStoryResolver.preferredSourceFromVoice(persona("a", "BLIND", "casual")))
                .isEqualTo("blind");
        assertThat(PlanSourceStoryResolver.preferredSourceFromVoice(persona("b", "NATEPAN", "casual")))
                .isEqualTo("natepan");
        assertThat(PlanSourceStoryResolver.preferredSourceFromVoice(null)).isEqualTo("natepan");
    }

    private static Persona persona(String id, String voiceType, String formality) {
        return Persona.builder()
                .id(id)
                .archetype("FAMILY_BOUND")
                .tier("HEAVY")
                .voiceProfile(new LinkedHashMap<>(Map.of("formality", formality, "voice_type", voiceType)))
                .interests(Map.of("FAMILY", 1.0))
                .biasProfile(Map.of())
                .circadian(List.of())
                .slangLevel(new BigDecimal("0.2"))
                .active(true)
                .createdAt(Instant.now())
                .build();
    }
}
