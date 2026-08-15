package com.againspring.service.community;

import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoVariantServiceTest {

    @Mock
    LLMProvider llmProvider;

    @Mock
    PromptSanitizer promptSanitizer;

    VideoVariantService service;

    @BeforeEach
    void setUp() {
        service = new VideoVariantService(llmProvider, promptSanitizer, new ObjectMapper());
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "enabled", true);
        lenient().when(promptSanitizer.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void generate_parsesLlmJson_forBothPlatforms() throws Exception {
        when(llmProvider.invoke(anyString(), anyString()))
                .thenReturn("""
                    {"hook_reels":"릴스만의훅","script_reels":"짧은요약. 공감 비율은?","sibom_plan":[
                      {"role":"intro","image_id":"side-glance"},{"role":"peak","image_id":"stunned"},
                      {"role":"punch","image_id":"drained"},{"role":"punch","image_id":"indignant"}]}
                    """)
                .thenReturn("""
                    {"hook_shorts":"쇼츠훅","script_shorts":"조금긴요약. 댓글로","sibom_plan":[
                      {"role":"intro","image_id":"side-glance"},{"role":"peak","image_id":"stunned"},
                      {"role":"punch","image_id":"drained"},{"role":"punch","image_id":"indignant"},
                      {"role":"punch","image_id":"relieved"}]}
                    """);

        VideoVariantService.Variants v = service.generate(
                "마스터훅", "shock", "제목", "본문이 길어요 갈등 이야기", true, true);

        assertThat(v.hookReels()).isEqualTo("릴스만의훅");
        assertThat(v.hookShorts()).isEqualTo("쇼츠훅");
        assertThat(v.scriptReels()).contains("공감");
        assertThat(v.scriptShorts()).contains("댓글");
        assertThat(v.maxDurationReelsSec()).isEqualTo(30);
        assertThat(v.maxDurationShortsSec()).isEqualTo(45);
        verify(llmProvider, times(2)).invoke(anyString(), anyString());
    }

    @Test
    void generate_emptyPlan_correctsOnceAndRecordsSafeAttemptFacts() throws Exception {
        when(llmProvider.invoke(anyString(), anyString()))
                .thenReturn("{\"hook_reels\":\"첫훅\",\"script_reels\":\"첫대본\",\"sibom_plan\":[]}")
                .thenReturn("""
                    {"hook_reels":"보정훅","script_reels":"보정대본 공감","sibom_plan":[
                      {"role":"intro","image_id":"side-glance"},{"role":"peak","image_id":"stunned"},
                      {"role":"punch","image_id":"drained"},{"role":"punch","image_id":"indignant"}]}
                    """);

        VideoVariantService.Variants variants = service.generate(
                "마스터", "shock", "제목", "본문", true, false, List.of("side-glance"));

        assertThat(variants.sibomPlanReels()).hasSize(4);
        assertThat(variants.generationDiagnostics())
                .containsKey("instagram_reels");
        @SuppressWarnings("unchecked")
        var channelDiagnostics = (java.util.Map<String, Object>) variants.generationDiagnostics().get("instagram_reels");
        assertThat(channelDiagnostics).containsEntry("guarded_plan_count", 4);
        assertThat((List<?>) channelDiagnostics.get("attempts")).hasSize(2);
        verify(llmProvider, times(2)).invoke(anyString(), anyString());
    }

    @Test
    void generate_transientLlmFailure_correctsOnce() throws Exception {
        when(llmProvider.invoke(anyString(), anyString()))
                .thenThrow(new RuntimeException("upstream timeout"))
                .thenReturn("""
                    {"hook_reels":"보정훅","script_reels":"보정대본 공감","sibom_plan":[
                      {"role":"intro","image_id":"side-glance"},{"role":"peak","image_id":"stunned"},
                      {"role":"punch","image_id":"drained"},{"role":"punch","image_id":"indignant"}]}
                    """);

        VideoVariantService.Variants variants = service.generate(
                "마스터", "shock", "제목", "본문", true, false, List.of("side-glance"));

        assertThat(variants.sibomPlanReels()).hasSize(4);
        verify(llmProvider, times(2)).invoke(anyString(), anyString());
    }

    @Test
    void generate_parsesSibomPlan_andGuards() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenReturn("""
            {
              "hook_reels":"훅",
              "script_reels":"요약 공감",
              "sibom_plan":[
                {"role":"intro","image_id":"side-glance","caption":"눈치","beat_index":0,"size":"large","dwell":"hold"},
                {"role":"peak","image_id":"stunned","caption":"말문이 막혔다","beat_index":3,"size":"large","dwell":"hold"},
                {"role":"soft_fill","image_id":"drained","caption":"이제 지쳤다","beat_index":5,"size":"small","dwell":"punch"},
                {"role":"punch","image_id":"unknown-xx","caption":"드롭","beat_index":6,"size":"small","dwell":"punch"}
              ]
            }
            """);

        VideoVariantService.Variants v = service.generate(
                "마스터", "shock", "제목", "본문", true, false,
                List.of("side-glance", "stunned", "drained", "waiting-reply"));

        assertThat(v.sibomPlan()).extracting(SibomPlanItem::imageId)
                .containsExactly("side-glance", "stunned", "drained");
        assertThat(v.sibomPlanReels()).isEqualTo(v.sibomPlan());
        assertThat(v.sibomPlanShorts()).isEmpty();
    }

    @Test
    void generate_promptInjectsCardsAndSoftFill_notFullCatalog() throws Exception {
        ArgumentCaptor<String> promptCap = ArgumentCaptor.forClass(String.class);
        when(llmProvider.invoke(promptCap.capture(), anyString())).thenReturn("""
            {"hook_reels":"h","script_reels":"s 공감","sibom_plan":[]}
            """);

        service.generate(
                "훅", "tension", "제목", "본문 갈등", true, false,
                List.of("waiting-reply", "stunned", "drained"));

        String prompt = promptCap.getValue();
        assertThat(prompt).contains("waiting-reply|");
        assertThat(prompt).contains("soft_fill 풀");
        assertThat(prompt).contains("최대 10자(maxChars=10)");
        assertThat(prompt).contains("drained");
        assertThat(prompt).contains("curled-up");
        // must not dump a third unrelated catalog id that wasn't a candidate
        assertThat(prompt).doesNotContain("solo-parenting|");
        assertThat(prompt).doesNotContain("in-law-conflict|");
        assertThat(prompt.split("waiting-reply\\|", -1).length - 1).isEqualTo(1);
    }

    @Test
    void generateForChannel_shortsOnly() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenReturn("""
            {"hook_shorts":"쇼츠","script_shorts":"대본 댓글","sibom_plan":[
              {"role":"intro","image_id":"side-glance","caption":"눈치","beat_index":0,"size":"large","dwell":"hold"}
            ]}
            """);

        VideoVariantService.Variants v = service.generateForChannel(
                "마스터", "sad", "제목", "본문", "shorts", List.of("side-glance"));

        assertThat(v.hookShorts()).isEqualTo("쇼츠");
        assertThat(v.hookReels()).isNull();
        assertThat(v.sibomPlan()).hasSize(1);
        assertThat(v.maxDurationShortsSec()).isEqualTo(45);
    }

    @Test
    void generate_llmFail_fallsBackToHeuristic() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenThrow(new RuntimeException("down"));

        VideoVariantService.Variants v = service.generate(
                "마스터", "anger", "제목", "본문요약용텍스트입니다", true, false);

        assertThat(v.hookReels()).isEqualTo("마스터");
        assertThat(v.scriptReels()).contains("공감");
        assertThat(v.hookShorts()).isNull();
        assertThat(v.maxDurationReelsSec()).isEqualTo(30);
        assertThat(v.maxDurationShortsSec()).isNull();
        assertThat(v.sibomPlan()).isEmpty();
        assertThat(VideoVariantService.validateRequiredSibomPlans(v, true, false).failureCode())
            .isEqualTo("VARIANT_LLM_ERROR");
    }

    @Test
    void qualityGate_rejectsPlanBelowChannelMinimum() {
        VideoVariantService.Variants variants = new VideoVariantService.Variants(
            "h", "s", 30, null, null, null,
            List.of(new SibomPlanItem("intro", "side-glance", "", 0, "large", "hold")), List.of());

        VideoVariantService.QualityGateResult result =
            VideoVariantService.validateRequiredSibomPlans(variants, true, false);

        assertThat(result.failureCode()).isEqualTo("SIBOM_PLAN_TOO_SHORT");
        assertThat(result.diagnostics()).containsEntry("reels_guarded_plan_count", 1);
    }

    @Test
    void generate_nonVideo_returnsEmpty() {
        VideoVariantService.Variants v = service.generate("h", "sad", "t", "b", false, false);
        assertThat(v.hookReels()).isNull();
        assertThat(v.scriptShorts()).isNull();
        assertThat(v.sibomPlan()).isEmpty();
    }

    @Test
    void generate_stripsForbiddenTerms() throws Exception {
        when(llmProvider.invoke(anyString(), anyString()))
                .thenReturn("""
                    {"hook_reels":"이건판결이다","script_reels":"배심원이 처방한다 공감","sibom_plan":[]}
                    """)
                .thenReturn("""
                    {"hook_shorts":"ok","script_shorts":"요약 댓글","sibom_plan":[]}
                    """);

        VideoVariantService.Variants v = service.generate(
                "마스터", "tension", "제목", "본문", true, true);

        assertThat(v.hookReels()).doesNotContain("판결");
        assertThat(v.scriptReels()).doesNotContain("배심원");
        assertThat(v.scriptReels()).doesNotContain("처방");
    }

    @Test
    void buildSibomCards_capsAtTen() {
        List<String> many = List.of(
                "two-cold-backs", "two-argue", "waiting-reply", "swallow-words", "stunned",
                "side-glance", "drained", "indignant", "relieved", "money-trouble",
                "left-out", "nagging");
        String cards = VideoVariantService.buildSibomCards(many);
        assertThat(cards.lines().count()).isEqualTo(10);
        assertThat(cards).doesNotContain("left-out|");
    }
}
