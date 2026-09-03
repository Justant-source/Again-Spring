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

    @Mock
    com.againspring.marketing.MarketingLlmAuthGuard llmAuthGuard;

    @BeforeEach
    void setUp() {
        service = new VideoVariantService(llmProvider, promptSanitizer, new ObjectMapper(), llmAuthGuard);
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "enabled", true);
        lenient().when(promptSanitizer.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(llmAuthGuard.isCircuitOpen()).thenReturn(false);
        lenient().when(llmAuthGuard.isAuthenticationError(anyString())).thenReturn(false);
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
    void generate_sessionLimitResponse_classifiedAsTransientLlmError() throws Exception {
        when(llmProvider.invoke(anyString(), anyString()))
                .thenReturn("You've hit your session limit · resets 12:40pm (UTC)");

        VideoVariantService.Variants variants = service.generate(
                "마스터", "shock", "제목", "본문", true, false, List.of("side-glance"));

        assertThat(variants.sibomPlanReels()).isEmpty();
        assertThat(variants.channelGenerationStatus().get("instagram_reels")).isEqualTo("LLM_TRANSIENT_ERROR");
        verify(llmProvider, times(1)).invoke(anyString(), anyString());
    }

    @Test
    void generate_circuitOpen_skipsLlm() throws Exception {
        when(llmAuthGuard.isCircuitOpen()).thenReturn(true);

        VideoVariantService.Variants variants = service.generate(
                "마스터", "shock", "제목", "본문", true, true, List.of("side-glance"));

        assertThat(variants.channelGenerationStatus().get("instagram_reels")).isEqualTo("LLM_AUTH_CIRCUIT_OPEN");
        assertThat(variants.channelGenerationStatus().get("youtube_shorts")).isEqualTo("LLM_AUTH_CIRCUIT_OPEN");
        verify(llmProvider, org.mockito.Mockito.never()).invoke(anyString(), anyString());
    }

    @Test
    void generate_transientLlmFailure_doesNotCorrectInProcess() throws Exception {
        // Transient LLM failures do NOT trigger in-process correction (only OK/PARSE_ERROR do)
        when(llmProvider.invoke(anyString(), anyString()))
                .thenThrow(new RuntimeException("upstream timeout"));

        VideoVariantService.Variants variants = service.generate(
                "마스터", "shock", "제목", "본문", true, false, List.of("side-glance"));

        // Should call LLM only once (transient, no in-process retry)
        verify(llmProvider, times(1)).invoke(anyString(), anyString());
        assertThat(variants.sibomPlanReels()).isEmpty();
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

        // After guard (dedupe + soft-fill topup), should contain the valid items
        assertThat(v.sibomPlan()).extracting(SibomPlanItem::imageId)
                .contains("side-glance", "stunned", "drained");
        // unknown-xx should be dropped
        assertThat(v.sibomPlan()).extracting(SibomPlanItem::imageId).doesNotContain("unknown-xx");
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
    void generate_promptForbidsSlashSeparators() throws Exception {
        ArgumentCaptor<String> promptCap = ArgumentCaptor.forClass(String.class);
        when(llmProvider.invoke(promptCap.capture(), anyString())).thenReturn("""
            {"hook_shorts":"쇼츠","script_shorts":"대본 댓글","sibom_plan":[
              {"role":"intro","image_id":"side-glance"},{"role":"peak","image_id":"stunned"},
              {"role":"punch","image_id":"drained"},{"role":"punch","image_id":"indignant"},
              {"role":"punch","image_id":"relieved"}]}
            """);

        service.generate("훅", "tension", "제목", "본문", false, true, List.of("side-glance"));

        assertThat(promptCap.getValue()).contains("슬래시(/, ／) 금지");
    }

    @Test
    void generate_stripsSlashSeparatorsFromHookAndScript() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenReturn("""
            {"hook_shorts":"연애 3개월에 8개월 동거를 들었다 / 지금의 연애가 뭐인지 모르겠어","script_shorts":"말해준 건 고마운데／왜 굳이 지금이야. 댓글로","sibom_plan":[
              {"role":"intro","image_id":"side-glance"},{"role":"peak","image_id":"stunned"},
              {"role":"punch","image_id":"drained"},{"role":"punch","image_id":"indignant"},
              {"role":"punch","image_id":"relieved"}]}
            """);

        VideoVariantService.Variants v = service.generate(
                "마스터훅", "tension", "제목", "본문 갈등", false, true);

        assertThat(v.hookShorts()).doesNotContain("/").doesNotContain("／");
        assertThat(v.hookShorts()).isEqualTo("연애 3개월에 8개월 동거를 들었다 지금의 연애가 뭐인지 모르겠어");
        assertThat(v.scriptShorts()).doesNotContain("/").doesNotContain("／");
        assertThat(v.scriptShorts()).contains("말해준 건 고마운데 왜 굳이 지금이야");
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
        // After soft-fill topup, should be >= MIN_SHORTS
        assertThat(v.sibomPlan().size()).isGreaterThanOrEqualTo(SibomPlanGuard.MIN_SHORTS);
        assertThat(v.maxDurationShortsSec()).isEqualTo(45);
    }

    @Test
    void looksLikeLlmError_doesNotTreatSibomCatalogOverloadedIdAsProviderError() {
        String reelsJson = """
            ```json
            {
              "hook_reels": "책임질수록 밀려나고 책임 안 질수록 올라간다",
              "script_reels": "15년 다니며 본 것. 책임 앞에 먼저 비틀어 아래 사람들을 지킨 선배들은 만신창이였다.",
              "sibom_plan": [
                {"role": "peak", "image_id": "overloaded", "caption": "짓눌림"}
              ]
            }
            ```
            """;
        assertThat(VideoVariantService.looksLikeLlmError(reelsJson)).isFalse();
        assertThat(VideoVariantService.looksLikeLlmError("Overloaded")).isTrue();
        assertThat(VideoVariantService.looksLikeLlmError("Anthropic API overloaded (529)")).isTrue();
        assertThat(VideoVariantService.looksLikeLlmError("{\"type\":\"overloaded_error\"}")).isTrue();
        assertThat(VideoVariantService.looksLikeLlmError("Credit balance is too low")).isTrue();
    }

    @Test
    void looksLikeLlmError_apiErrorWithSpace_classifiedAsError_catalogOverloadedStillNot() {
        // "api error" (space) is the SSOT JSON signature restored in Phase 2 fix round 1 —
        // the catalog image_id "overloaded" exemption must keep passing alongside it.
        assertThat(VideoVariantService.looksLikeLlmError("API error: quota")).isTrue();
        assertThat(VideoVariantService.looksLikeLlmError(
                "{\"sibom_plan\":[{\"image_id\": \"overloaded\"}]}")).isFalse();
    }

    @Test
    void generate_acceptsSibomPlanWithCatalogIdOverloaded() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenReturn("""
            {"hook_reels":"책임질수록 밀려난다","script_reels":"15년 다니며 본 것. 책임 앞에 먼저 비틀어 지킨 선배들은 만신창이였다.","sibom_plan":[
              {"role":"intro","image_id":"no-apology","caption":"책임회피","beat_index":0,"size":"large","dwell":"hold"},
              {"role":"peak","image_id":"overloaded","caption":"짓눌림","beat_index":1,"size":"large","dwell":"hold"},
              {"role":"punch","image_id":"caught-lying","caption":"거짓말","beat_index":2,"size":"small","dwell":"punch"},
              {"role":"soft_fill","image_id":"swallow-words","caption":"말못함","beat_index":3,"size":"small","dwell":"punch"}
            ]}
            """);

        VideoVariantService.Variants v = service.generate(
                "마스터", "anger", "제목", "본문요약용텍스트입니다. 두 번째 문장입니다.", true, false,
                List.of("no-apology", "overloaded", "caught-lying", "swallow-words"));

        assertThat(v.channelGenerationStatus().get("instagram_reels")).isEqualTo("OK");
        assertThat(v.sibomPlanReels().stream().map(SibomPlanItem::imageId))
                .contains("overloaded");
        assertThat(VideoVariantService.validateRequiredSibomPlans(v, true, false).isValid()).isTrue();
    }

    @Test
    void generate_llmFail_fallsBackToHeuristic() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenThrow(new RuntimeException("down"));

        VideoVariantService.Variants v = service.generate(
                "마스터", "anger", "제목", "본문요약용텍스트입니다", true, false);

        assertThat(v.hookReels()).isEqualTo("마스터");
        assertThat(v.scriptReels()).contains("본문");
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

    @Test
    void generate_transientError_doesNotCorrectInProcess() throws Exception {
        // Transient failure should not trigger in-process correction (no 2nd call in sequence)
        when(llmProvider.invoke(anyString(), anyString()))
                .thenThrow(new RuntimeException("503 Service Unavailable"));

        VideoVariantService.Variants variants = service.generate(
                "마스터", "shock", "제목", "본문", true, false, List.of("side-glance"));

        // Should call LLM only once (transient error, no in-process retry)
        verify(llmProvider, times(1)).invoke(anyString(), anyString());
        assertThat(variants.sibomPlanReels()).isEmpty();
    }

    @Test
    void generate_okButShortPlan_autoTopUpWithSoftFill() throws Exception {
        // 1 LLM result gets auto top-up via soft-fill, no 2nd call needed
        when(llmProvider.invoke(anyString(), anyString()))
                .thenReturn("""
                    {"hook_reels":"훅","script_reels":"대본 공감","sibom_plan":[
                      {"role":"intro","image_id":"side-glance"}
                    ]}
                    """);

        VideoVariantService.Variants variants = service.generate(
                "마스터", "shock", "제목", "본문", true, false, List.of("side-glance"));

        // Should call LLM only once (soft-fill auto top-up handles it)
        verify(llmProvider, times(1)).invoke(anyString(), anyString());
        // After soft-fill, should be >= MIN_REELS
        assertThat(variants.sibomPlanReels().size()).isGreaterThanOrEqualTo(SibomPlanGuard.MIN_REELS);
    }

    @Test
    void generate_okButShortPlanReallyInsufficient_correctsInProcess() throws Exception {
        // If LLM result is parse error (empty), in-process correction should happen
        when(llmProvider.invoke(anyString(), anyString()))
                .thenReturn("""
                    {"hook_reels":"훅","script_reels":"대본 공감","sibom_plan":[]}
                    """)
                .thenReturn("""
                    {"hook_reels":"보정훅","script_reels":"보정대본 공감","sibom_plan":[
                      {"role":"intro","image_id":"side-glance"},
                      {"role":"peak","image_id":"stunned"},
                      {"role":"punch","image_id":"drained"},
                      {"role":"punch","image_id":"indignant"}
                    ]}
                    """);

        VideoVariantService.Variants variants = service.generate(
                "마스터", "shock", "제목", "본문", true, false, List.of("side-glance"));

        // Empty sibom_plan after soft-fill topup should trigger 2nd call
        verify(llmProvider, times(2)).invoke(anyString(), anyString());
        assertThat(variants.sibomPlanReels().size()).isGreaterThanOrEqualTo(SibomPlanGuard.MIN_REELS);
    }

    @Test
    void generate_promptContainsSoftTargetValues_forReels() throws Exception {
        ArgumentCaptor<String> promptCap = ArgumentCaptor.forClass(String.class);
        when(llmProvider.invoke(promptCap.capture(), anyString())).thenReturn("""
            {"hook_reels":"h","script_reels":"s 공감","sibom_plan":[]}
            """);

        service.generate("훅", "tension", "제목", "본문", true, false, List.of());

        String prompt = promptCap.getValue();
        // Reels: softTargetLo=5, softTargetHi=5
        assertThat(prompt).contains("5~5");
        assertThat(prompt).contains("최소 4장 필수(절대 하한");
    }

    @Test
    void generate_promptContainsSoftTargetValues_forShorts() throws Exception {
        ArgumentCaptor<String> promptCap = ArgumentCaptor.forClass(String.class);
        when(llmProvider.invoke(promptCap.capture(), anyString())).thenReturn("""
            {"hook_shorts":"h","script_shorts":"s 댓글","sibom_plan":[]}
            """);

        service.generate("훅", "tension", "제목", "본문", false, true, List.of());

        String prompt = promptCap.getValue();
        // Shorts: softTargetLo=6, softTargetHi=7
        assertThat(prompt).contains("6~7");
        assertThat(prompt).contains("최소 4장 필수(절대 하한");
    }

    @Test
    void generate_promptContainsDupeDuplicatePreventionInFirstAttempt() throws Exception {
        ArgumentCaptor<String> promptCap = ArgumentCaptor.forClass(String.class);
        when(llmProvider.invoke(promptCap.capture(), anyString())).thenReturn("""
            {"hook_reels":"h","script_reels":"s 공감","sibom_plan":[]}
            """);

        service.generate("훅", "tension", "제목", "본문", true, false, List.of());

        String prompt = promptCap.getValue();
        // 1차 프롬프트에 중복 금지가 있어야 함
        assertThat(prompt).contains("image_id와 swap_group은 전 항목에서 서로 달라야");
        assertThat(prompt).contains("중복은 자동으로 제거되어");
    }

    @Test
    void generate_attemptsRecordStartedAtDurationAndError() throws Exception {
        when(llmProvider.invoke(anyString(), anyString()))
                .thenReturn("""
                    {"hook_reels":"첫훅","script_reels":"첫대본 공감","sibom_plan":[]}
                    """)
                .thenReturn("""
                    {"hook_reels":"보정훅","script_reels":"보정대본 공감","sibom_plan":[
                      {"role":"intro","image_id":"side-glance"},
                      {"role":"peak","image_id":"stunned"},
                      {"role":"punch","image_id":"drained"},
                      {"role":"punch","image_id":"indignant"}
                    ]}
                    """);

        VideoVariantService.Variants variants = service.generate(
                "마스터", "shock", "제목", "본문", true, false, List.of("side-glance"));

        @SuppressWarnings("unchecked")
        var channelDiagnostics = (java.util.Map<String, Object>) variants.generationDiagnostics().get("instagram_reels");
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> attempts = (List<java.util.Map<String, Object>>) channelDiagnostics.get("attempts");

        assertThat(attempts).hasSize(2);

        // First attempt: empty plan (PARSE_ERROR status)
        var attempt1 = attempts.get(0);
        assertThat(attempt1).containsKeys("attempt", "started_at", "duration_ms", "result", "guarded_plan_count");
        assertThat(attempt1.get("started_at")).isNotNull();
        assertThat(attempt1.get("duration_ms")).isInstanceOf(Long.class);
        assertThat(attempt1.get("result")).isEqualTo("OK"); // LLM returned OK but empty plan

        // Second attempt: successful correction
        var attempt2 = attempts.get(1);
        assertThat(attempt2).containsKeys("attempt", "started_at", "duration_ms", "result", "guarded_plan_count");
        assertThat(attempt2.get("started_at")).isNotNull();
        assertThat(attempt2.get("duration_ms")).isInstanceOf(Long.class);
        assertThat(attempt2.get("result")).isEqualTo("OK");
    }

    @Test
    void qualityGate_fourShortsPlanPasses() {
        VideoVariantService.Variants variants = new VideoVariantService.Variants(
            "h", "s", 30, null, "숏츠 대본", null,
            List.of(),
            List.of(
                new SibomPlanItem("intro", "side-glance", "", 0, "large", "hold"),
                new SibomPlanItem("peak", "stunned", "", 2, "large", "hold"),
                new SibomPlanItem("punch", "drained", "", 4, "small", "punch"),
                new SibomPlanItem("punch", "curled-up", "", 5, "small", "punch")
            ));

        VideoVariantService.QualityGateResult result =
            VideoVariantService.validateRequiredSibomPlans(variants, false, true);

        assertThat(result.isValid()).isTrue();
        assertThat(result.failureCode()).isNull();
    }

    @Test
    void generate_sibomCaptionLeakedFromBody_replacedWithCatalogDefault() throws Exception {
        // Reproduces job 01M13K1KH1SYEMYSH5PCFFJP9N (marketing_generation_trace id=11):
        // the LLM copied "상의없이" / "오백만원" straight out of the title/body into
        // sibom_plan captions instead of writing an emotion/situation label.
        String title = "아내가 상의없이 오백만원 빌려준 걸 알았다";
        String body = "언제 갚는지 나랑 상의도 없이 그냥 빌려줬다는 게\n"
                + "결혼하고 처음으로 진짜 낯설게 느껴졌어\n"
                + "오백만원이 빠져나간 걸 봤어";

        when(llmProvider.invoke(anyString(), anyString())).thenReturn("""
            {"hook_shorts":"h","script_shorts":"요약 댓글","sibom_plan":[
              {"role":"intro","image_id":"decision-announced","caption":"상의없이","beat_index":0,"size":"large","dwell":"hold"},
              {"role":"peak","image_id":"money-trouble","caption":"오백만원","beat_index":1,"size":"large","dwell":"hold"},
              {"role":"punch","image_id":"different-values","caption":"다른선택","beat_index":2,"size":"small","dwell":"punch"},
              {"role":"soft_fill","image_id":"swallow-words","caption":"말못함","beat_index":2,"size":"small","dwell":"punch"}
            ]}
            """);

        VideoVariantService.Variants v = service.generate(
                null, "shock", title, body, false, true,
                List.of("decision-announced", "money-trouble", "different-values", "swallow-words"));

        SibomPlanItem intro = v.sibomPlanShorts().stream()
                .filter(i -> i.imageId().equals("decision-announced")).findFirst().orElseThrow();
        SibomPlanItem peak = v.sibomPlanShorts().stream()
                .filter(i -> i.imageId().equals("money-trouble")).findFirst().orElseThrow();

        assertThat(intro.caption()).doesNotContain("상의없이");
        assertThat(peak.caption()).doesNotContain("오백만원");
        // Legitimate labels untouched.
        assertThat(v.sibomPlanShorts().stream().anyMatch(i -> "다른선택".equals(i.caption()))).isTrue();
        assertThat(v.sibomPlanShorts().stream().anyMatch(i -> "말못함".equals(i.caption()))).isTrue();
    }

    @Test
    void qualityGate_threeShortsPlanFails() {
        VideoVariantService.Variants variants = new VideoVariantService.Variants(
            "h", "s", 30, null, "숏츠 대본", null,
            List.of(),
            List.of(
                new SibomPlanItem("intro", "side-glance", "", 0, "large", "hold"),
                new SibomPlanItem("peak", "stunned", "", 2, "large", "hold"),
                new SibomPlanItem("punch", "drained", "", 4, "small", "punch")
            ));

        VideoVariantService.QualityGateResult result =
            VideoVariantService.validateRequiredSibomPlans(variants, false, true);

        assertThat(result.isValid()).isFalse();
        assertThat(result.failureCode()).isEqualTo("SIBOM_PLAN_TOO_SHORT");
    }
}
