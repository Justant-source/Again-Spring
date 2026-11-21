package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanSourceStoryResolverTest {

    @Mock private AiLearningClient aiLearningClient;
    @Mock private PersonaHistoryStore personaHistoryStore;
    @Mock private LlmAiUserClient llmAiUserClient;
    @InjectMocks private PlanSourceStoryResolver resolver;

    /** persona-diversity-v4 계약7 — 유효한 골격 픽스처(ok는 llm 워커 응답 map에만 있고 resolver는 무시). */
    private static Map<String, Object> skeletonFixture(String category, String incident, boolean bSideViable) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("category", category);
        m.put("author_role", "작성자");
        m.put("counterpart_role", "상대방");
        m.put("relationship", "가족");
        m.put("incident", incident);
        m.put("sequence", List.of("사건1", "사건2", "사건3"));
        m.put("stakes", "관계 유지 여부");
        m.put("author_claim", "내 입장");
        m.put("counterpart_claim", "상대 입장");
        m.put("emotion", "답답함");
        m.put("gray_zone", "작성자도 애매한 지점 있음");
        m.put("b_side_viable", bSideViable);
        return m;
    }

    @Test
    void claimAndResolveEmptyWhenClaimMisses() {
        when(aiLearningClient.claimPopularSource(eq("blind"), eq("res-1"), any(Instant.class), eq("FAMILY"), any()))
                .thenReturn(Optional.empty());

        Optional<PlanSourceStoryResolver.ResolvedSource> resolved =
                resolver.claimAndResolve(null, "blind", "res-1", Instant.now().plus(1, ChronoUnit.HOURS), "FAMILY");

        assertThat(resolved).isEmpty();
        verify(aiLearningClient).claimPopularSource(eq("blind"), eq("res-1"), any(Instant.class), eq("FAMILY"), any());
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
        when(aiLearningClient.claimPopularSource(eq("blind"), eq("sched-uuid"), eq(until), eq("FAMILY"), any()))
                .thenReturn(Optional.of(claimed));
        when(aiLearningClient.styleSample(eq("blind"), eq("POST"), eq("polite"), eq(2), eq(350)))
                .thenReturn(List.of());
        when(personaHistoryStore.loadRecentPosts(eq("ai-user-1"), anyInt()))
                .thenReturn(List.of("예전 글 본문"));
        when(llmAiUserClient.extractSkeleton(eq(42L), eq("FAMILY"), eq("시어머니"),
                eq("시어머니 갈등이 심하다 장문의 본문입니다"), eq("sched-uuid")))
                .thenReturn(Optional.of(skeletonFixture("FAMILY", "시어머니가 반복해서 사생활에 간섭함", false)));

        Optional<PlanSourceStoryResolver.ResolvedSource> opt =
                resolver.claimAndResolve(author, "blind", "sched-uuid", until, "FAMILY");

        assertThat(opt).isPresent();
        PlanSourceStoryResolver.ResolvedSource resolved = opt.get();
        assertThat(resolved.reconstructMode()).isTrue();
        assertThat(resolved.sourceExampleId()).isEqualTo(42L);
        // sourceBody(원문)는 provenance/StoryProfile 용으로 그대로 유지된다 — 프롬프트로는 더 이상 나가지 않는다.
        assertThat(resolved.sourceBody()).isEqualTo("시어머니 갈등이 심하다 장문의 본문입니다");
        assertThat(resolved.sourceUrl()).isEqualTo("https://blind.example/post/1");
        assertThat(resolved.sourceTitle()).isEqualTo("시어머니");
        assertThat(resolved.sourceCommunity()).isEqualTo("blind");
        // item3: topicSeed는 원문이 아니라 골격 incident에서 일반화된 문자열이다.
        assertThat(resolved.topicSeed()).isEqualTo("시어머니가 반복해서 사생활에 간섭함");
        // item3: sourceContext에는 골격 JSON만 담긴다 — 원문 필드(body/sourceUrl/reconstructMode)는 없다.
        assertThat(resolved.sourceContext().get("category")).isEqualTo("FAMILY");
        assertThat(resolved.sourceContext().get("incident")).isEqualTo("시어머니가 반복해서 사생활에 간섭함");
        assertThat(resolved.sourceContext().get("b_side_viable")).isEqualTo(false);
        assertThat(resolved.sourceContext()).doesNotContainKey("body");
        assertThat(resolved.sourceContext()).doesNotContainKey("sourceUrl");
        assertThat(resolved.sourceContext()).doesNotContainKey("reconstructMode");
        assertThat(resolved.sourceContext().values()).noneMatch(v ->
                String.valueOf(v).contains("시어머니 갈등이 심하다 장문의 본문입니다"));
        assertThat(resolved.recentBodies()).containsExactly("예전 글 본문");

        verify(aiLearningClient).claimPopularSource(eq("blind"), eq("sched-uuid"), eq(until), eq("FAMILY"), any());
        verify(aiLearningClient, never()).findSimilar(anyString(), anyString(), anyString(), anyInt());
        verify(aiLearningClient, never()).findSimilar(anyString(), anyString(), anyString(), anyInt(), anyString());
        verify(aiLearningClient, never()).fetchDailyTopics(anyString(), anyInt());
        verify(aiLearningClient, never()).releaseSource(anyLong(), anyString());
    }

    @Test
    void claimAndResolveRetriesNextSourceWhenSkeletonExtractionFails() {
        AiLearningClient.ExampleItem broken = new AiLearningClient.ExampleItem();
        broken.setId(1L);
        broken.setContent("깨진 원본");
        broken.setSource("blind");
        broken.setTitle("t1");

        AiLearningClient.ExampleItem ok = new AiLearningClient.ExampleItem();
        ok.setId(2L);
        ok.setContent("두번째 원본");
        ok.setSource("blind");
        ok.setTitle("t2");

        Instant until = Instant.now().plus(1, ChronoUnit.HOURS);
        when(aiLearningClient.claimPopularSource(eq("blind"), eq("k"), eq(until), eq("FAMILY"), eq(java.util.Set.of())))
                .thenReturn(Optional.of(broken));
        when(aiLearningClient.claimPopularSource(eq("blind"), eq("k"), eq(until), eq("FAMILY"), eq(java.util.Set.of(1L))))
                .thenReturn(Optional.of(ok));
        when(llmAiUserClient.extractSkeleton(eq(1L), eq("FAMILY"), eq("t1"), eq("깨진 원본"), eq("k")))
                .thenReturn(Optional.empty());
        when(llmAiUserClient.extractSkeleton(eq(2L), eq("FAMILY"), eq("t2"), eq("두번째 원본"), eq("k")))
                .thenReturn(Optional.of(skeletonFixture("FAMILY", "가족 갈등 사건", false)));

        Optional<PlanSourceStoryResolver.ResolvedSource> opt =
                resolver.claimAndResolve(null, "blind", "k", until, "FAMILY");

        assertThat(opt).isPresent();
        assertThat(opt.get().sourceExampleId()).isEqualTo(2L);
        verify(aiLearningClient).releaseSource(eq(1L), eq("k"));
    }

    @Test
    void claimAndResolveGivesUpAfterMaxSkeletonAttempts() {
        Instant until = Instant.now().plus(1, ChronoUnit.HOURS);
        java.util.Set<Long> excludedSoFar = new java.util.LinkedHashSet<>();
        for (int i = 0; i < PlanSourceStoryResolver.MAX_SKELETON_ATTEMPTS; i++) {
            long id = 100 + i;
            AiLearningClient.ExampleItem item = new AiLearningClient.ExampleItem();
            item.setId(id);
            item.setContent("본문" + i);
            item.setSource("natepan");
            item.setTitle("t" + i);
            when(aiLearningClient.claimPopularSource(
                    eq("natepan"), eq("k2"), eq(until), eq("WORK"), eq(java.util.Set.copyOf(excludedSoFar))))
                    .thenReturn(Optional.of(item));
            when(llmAiUserClient.extractSkeleton(eq(id), eq("WORK"), eq("t" + i), eq("본문" + i), eq("k2")))
                    .thenReturn(Optional.empty());
            excludedSoFar.add(id);
        }

        Optional<PlanSourceStoryResolver.ResolvedSource> opt =
                resolver.claimAndResolve(null, "natepan", "k2", until, "WORK");

        assertThat(opt).isEmpty();
        verify(aiLearningClient, org.mockito.Mockito.times(PlanSourceStoryResolver.MAX_SKELETON_ATTEMPTS))
                .claimPopularSource(eq("natepan"), eq("k2"), eq(until), eq("WORK"), any());
        verify(aiLearningClient, org.mockito.Mockito.times(PlanSourceStoryResolver.MAX_SKELETON_ATTEMPTS))
                .releaseSource(anyLong(), eq("k2"));
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
        when(aiLearningClient.claimPopularSource(eq("natepan"), eq("k"), eq(until), eq("WORK"), any()))
                .thenReturn(Optional.of(claimed));
        when(aiLearningClient.styleSample(eq("natepan"), eq("POST"), eq("casual"), eq(2), eq(350)))
                .thenReturn(List.of());
        when(llmAiUserClient.extractSkeleton(eq(7L), eq("WORK"), eq("직장"), eq("직장 갈등"), eq("k")))
                .thenReturn(Optional.of(skeletonFixture("WORK", "직장 상사와의 반복된 마찰", false)));

        Optional<PlanSourceStoryResolver.ResolvedSource> opt =
                resolver.claimAndResolve(null, "natepan", "k", until, "WORK");

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

        when(aiLearningClient.claimPopularSource(eq("blind"), anyString(), any(Instant.class), eq("WORK"), any()))
                .thenReturn(Optional.of(claimed));
        when(aiLearningClient.styleSample(eq("blind"), eq("POST"), eq("casual"), eq(2), eq(350)))
                .thenReturn(List.of());
        when(personaHistoryStore.loadRecentPosts(eq("ai-user-blind"), anyInt()))
                .thenReturn(List.of());
        when(llmAiUserClient.extractSkeleton(eq(99L), eq("WORK"), eq("t"), eq("blind seed body"), anyString()))
                .thenReturn(Optional.of(skeletonFixture("WORK", "직장 내 반복된 갈등", false)));

        PlanSourceStoryResolver.ResolvedSource resolved = resolver.resolve(author, "WORK", null);

        assertThat(resolved.reconstructMode()).isTrue();
        assertThat(resolved.sourceExampleId()).isEqualTo(99L);
        verify(aiLearningClient).claimPopularSource(eq("blind"), anyString(), any(Instant.class), eq("WORK"), any());
        verify(aiLearningClient, never()).findSimilar(anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void legacyResolveThrowsWhenClaimEmpty_noFreestyle() {
        Persona author = persona("ai-user-2", "NATEPAN", "casual");
        when(aiLearningClient.claimPopularSource(eq("natepan"), anyString(), any(Instant.class), eq("WORK"), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(author, "WORK", "   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("natepan");

        verify(aiLearningClient, never()).findSimilar(anyString(), anyString(), anyString(), anyInt(), anyString());
        verify(aiLearningClient, never()).fetchDailyTopics(anyString(), anyInt());
    }

    @Test
    void claimAndResolvePassesPlazaCategoryToClaim() {
        // Regression: FRIEND plaza must not claim unfiltered marriage stories.
        when(aiLearningClient.claimPopularSource(eq("blind"), eq("rk"), any(Instant.class), eq("FRIEND"), any()))
                .thenReturn(Optional.empty());

        assertThat(resolver.claimAndResolve(null, "blind", "rk",
                Instant.now().plusSeconds(60), "friend")).isEmpty();

        verify(aiLearningClient).claimPopularSource(eq("blind"), eq("rk"), any(Instant.class), eq("FRIEND"), any());
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

    @Test
    void putStyleReference_truncatesTo1500CodePointsAndReportsFullLength() {
        String body = "가".repeat(1400) + "😀".repeat(300); // 1700 코드포인트, 서러게이트 포함
        Map<String, Object> req = new java.util.LinkedHashMap<>();
        PlanSourceStoryResolver.putStyleReference(req, body);
        String ref = (String) req.get("styleReference");
        assertThat(ref.codePointCount(0, ref.length())).isEqualTo(1500);
        assertThat(req.get("sourceLength")).isEqualTo(1700);
    }

    @Test
    void putStyleReference_shortBodyKeptWhole() {
        Map<String, Object> req = new java.util.LinkedHashMap<>();
        PlanSourceStoryResolver.putStyleReference(req, "짧은 원문");
        assertThat(req.get("styleReference")).isEqualTo("짧은 원문");
        assertThat(req.get("sourceLength")).isEqualTo(5);
    }

    @Test
    void putStyleReference_omittedWhenNullOrBlank() {
        Map<String, Object> req = new java.util.LinkedHashMap<>();
        PlanSourceStoryResolver.putStyleReference(req, null);
        PlanSourceStoryResolver.putStyleReference(req, "  \n\t ");
        assertThat(req).doesNotContainKeys("styleReference", "sourceLength");
    }

    private static AiLearningClient.ExampleItem example(long id, String title) {
        AiLearningClient.ExampleItem e = new AiLearningClient.ExampleItem();
        e.setId(id);
        e.setContent("본문 " + id);
        e.setSource("blind");
        e.setTitle(title);
        return e;
    }

    @Test
    void claimAndResolveRejectsNoConflictSourceAndClaimsNext() {
        Instant until = Instant.now().plus(1, ChronoUnit.HOURS);
        when(aiLearningClient.claimPopularSource(eq("blind"), eq("k"), eq(until), eq("FAMILY"), eq(java.util.Set.of())))
                .thenReturn(Optional.of(example(1L, "t1")));
        when(aiLearningClient.claimPopularSource(eq("blind"), eq("k"), eq(until), eq("FAMILY"), eq(java.util.Set.of(1L))))
                .thenReturn(Optional.of(example(2L, "t2")));
        Map<String, Object> noConflict = skeletonFixture("FAMILY", "정보글", true);
        noConflict.put("has_conflict", false);
        Map<String, Object> good = skeletonFixture("FAMILY", "갈등 사건", true);
        good.put("has_conflict", true);
        good.put("verdict_lean", "PARTNER");
        when(llmAiUserClient.extractSkeleton(eq(1L), any(), any(), any(), any())).thenReturn(Optional.of(noConflict));
        when(llmAiUserClient.extractSkeleton(eq(2L), any(), any(), any(), any())).thenReturn(Optional.of(good));
        when(aiLearningClient.sourceComments(2L, 12)).thenReturn(List.of("댓글1", "댓글2"));

        Optional<PlanSourceStoryResolver.ResolvedSource> opt =
                resolver.claimAndResolve(null, "blind", "k", until, "FAMILY");

        assertThat(opt).isPresent();
        assertThat(opt.get().sourceExampleId()).isEqualTo(2L);
        assertThat(opt.get().commentStyleReference()).containsExactly("댓글1", "댓글2");
        assertThat(opt.get().sourceContext().get("verdict_lean")).isEqualTo("PARTNER");
        assertThat(opt.get().provenanceForTrace()).containsEntry("verdictLean", "PARTNER");
        verify(aiLearningClient).rejectSource(1L, "k", "no_conflict");
        verify(aiLearningClient, never()).releaseSource(eq(1L), anyString());
        verify(aiLearningClient, never()).sourceComments(eq(1L), anyInt());
    }

    @Test
    void claimAndResolveGivesUpAfterThreeNoConflictSources() {
        Instant until = Instant.now().plus(1, ChronoUnit.HOURS);
        for (long id = 1; id <= 3; id++) {
            java.util.Set<Long> excluded = new java.util.HashSet<>();
            for (long j = 1; j < id; j++) excluded.add(j);
            when(aiLearningClient.claimPopularSource(eq("blind"), eq("k"), eq(until), eq("FAMILY"), eq(excluded)))
                    .thenReturn(Optional.of(example(id, "t" + id)));
            Map<String, Object> nc = skeletonFixture("FAMILY", "정보글", true);
            nc.put("has_conflict", false);
            when(llmAiUserClient.extractSkeleton(eq(id), any(), any(), any(), any())).thenReturn(Optional.of(nc));
        }

        Optional<PlanSourceStoryResolver.ResolvedSource> opt =
                resolver.claimAndResolve(null, "blind", "k", until, "FAMILY");

        assertThat(opt).isEmpty();
        verify(aiLearningClient, org.mockito.Mockito.times(3)).rejectSource(anyLong(), eq("k"), eq("no_conflict"));
    }

    @Test
    void hasConflictDefaultsToTrueWhenMissing() {
        assertThat(PlanSourceStoryResolver.hasConflict(Map.of())).isTrue();
        assertThat(PlanSourceStoryResolver.hasConflict(Map.of("has_conflict", "false"))).isFalse();
        assertThat(PlanSourceStoryResolver.hasConflict(Map.of("has_conflict", true))).isTrue();
    }

    @Test
    void putCommentReference_capsAt12AndAddsTitle() {
        List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) many.add("댓글" + i);
        Map<String, Object> req = new LinkedHashMap<>();
        PlanSourceStoryResolver.putCommentReference(req, many, " 원문 제목 ");
        assertThat((List<?>) req.get("commentStyleReference")).hasSize(12);
        assertThat(req.get("sourceTitle")).isEqualTo("원문 제목");
        Map<String, Object> empty = new LinkedHashMap<>();
        PlanSourceStoryResolver.putCommentReference(empty, List.of(), null);
        assertThat(empty).isEmpty();
    }
}
