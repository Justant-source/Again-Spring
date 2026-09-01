package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.domain.marketing.XPersonaExample;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XOpsActionRepository;
import com.againspring.repository.marketing.XPersonaExampleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XPersonaLearnServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;
    @Mock
    private MarketingXOpsSettingsService xOpsSettingsService;
    @Mock
    private FxTwitterXTimelineClient timelineClient;
    @Mock
    private LLMProvider llmProvider;
    @Mock
    private PromptSanitizer promptSanitizer;
    @Mock
    private PromptLoader promptLoader;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private XPersonaExampleRepository exampleRepository;
    @Mock
    private XOpsActionRepository xOpsActionRepository;
    @Mock
    private XPersonaShadowEval shadowEval;

    @InjectMocks
    private XPersonaLearnService service;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "llmEnabled", false);
        ReflectionTestUtils.setField(service, "model", "claude-sonnet-5");
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());
        when(systemSettingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(xOpsSettingsService.get()).thenReturn(learningOn());
        when(exampleRepository.existsByTweetId(any())).thenReturn(false);
        when(exampleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(exampleRepository.findTop20BySourceOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(exampleRepository.findTop40BySourceOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(exampleRepository.findRandomBySourceExcluding(any(), any(), anyInt())).thenReturn(List.of());
        when(exampleRepository.findByTweetId(any())).thenReturn(Optional.empty());
        when(xOpsActionRepository.findByStatusAndKindInAndCreatedAtGreaterThanEqual(any(), any(), any()))
            .thenReturn(List.of());
        when(promptLoader.get(anyString())).thenThrow(new NoSuchFileException("marketing/x-persona-charter.md"));
        when(promptSanitizer.sanitize(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            return arg == null ? "" : arg.toString();
        });
    }

    @Test
    void runNow_keepsManualReplies_dropsAutoThread_doesNotSaveProfileWhenLlmOff() {
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            XManualStatusClassifier.Status.post(
                "auto-hook", "남친 폰 열자마자 소름 돋았다\n\n#다시봄 #againspring"),
            XManualStatusClassifier.Status.reply(
                "auto-url", "https://againspring.net/community/x", "againspring_net"),
            XManualStatusClassifier.Status.reply(
                "man-1", "@KoreAgenda 너무귀여움 ㅋㅋㅋㅋ", "KoreAgenda")
        ));

        XPersonaLearnService.LearnResult r = service.runNow("admin");

        assertThat(r.status()).isEqualTo("INGESTED_LLM_DISABLED");
        assertThat(r.newManuals()).isEqualTo(1);
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SystemSetting::getSettingKey)
            .contains(XPersonaLearnService.KEY_INGESTED)
            .doesNotContain(XPersonaLearnService.KEY_PROFILE);
        verify(exampleRepository).save(any());
    }

    @Test
    void runNow_skipsLedgerPostedIdsEvenIfTheyLookManual() {
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            XManualStatusClassifier.Status.reply(
                "auto-1", "@KoreAgenda 너무귀여움 ㅋㅋㅋㅋ", "KoreAgenda"),
            XManualStatusClassifier.Status.reply(
                "man-1", "@ceolmh3 힘빠지긴 할듯", "ceolmh3")
        ));
        when(xOpsActionRepository.findByStatusAndKindInAndCreatedAtGreaterThanEqual(any(), any(), any()))
            .thenReturn(List.of(XOpsAction.builder()
                .kind(XOpsAction.Kind.OUTBOUND)
                .status(XOpsAction.Status.POSTED)
                .postedTweetId("auto-1")
                .targetTweetId("root-a")
                .body("너무귀여움 ㅋㅋㅋㅋ")
                .createdAt(Instant.now())
                .build()));

        service.runNow("admin");

        ArgumentCaptor<XPersonaExample> captor = ArgumentCaptor.forClass(XPersonaExample.class);
        verify(exampleRepository).save(captor.capture());
        assertThat(captor.getValue().getTweetId()).isEqualTo("man-1");
        assertThat(captor.getValue().getSource()).isEqualTo(XPersonaExample.Source.TIMELINE);
    }

    @Test
    void runNow_skipsRitualPostedIdsFromGold() {
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            XManualStatusClassifier.Status.post("ritual-1", "벌써자?")
        ));
        when(xOpsActionRepository.findByStatusAndKindInAndCreatedAtGreaterThanEqual(any(), any(), any()))
            .thenReturn(List.of(XOpsAction.builder()
                .kind(XOpsAction.Kind.RITUAL)
                .status(XOpsAction.Status.POSTED)
                .postedTweetId("ritual-1")
                .body("벌써자?")
                .createdAt(Instant.now())
                .build()));

        XPersonaLearnService.LearnResult r = service.runNow("admin");

        assertThat(r.status()).isEqualTo("NO_NEW");
        verify(exampleRepository, never()).save(any());
    }

    @Test
    void runNow_ingestsMissingPostedCommentAsDeletedAuto() {
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of());
        when(xOpsActionRepository.findByStatusAndKindInAndCreatedAtGreaterThanEqual(any(), any(), any()))
            .thenReturn(List.of(XOpsAction.builder()
                .kind(XOpsAction.Kind.OUTBOUND)
                .status(XOpsAction.Status.POSTED)
                .postedTweetId("auto-gone")
                .targetTweetId("root-9")
                .body("문맥없는말")
                .createdAt(Instant.now())
                .build()));

        XPersonaLearnService.LearnResult r = service.runNow("admin");

        assertThat(r.status()).isEqualTo("INGESTED_LLM_DISABLED");
        assertThat(r.newManuals()).isEqualTo(1);
        ArgumentCaptor<XPersonaExample> captor = ArgumentCaptor.forClass(XPersonaExample.class);
        verify(exampleRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(XPersonaExample.Source.DELETED_AUTO);
        assertThat(captor.getValue().getOperatorBody()).isEqualTo("문맥없는말");
        assertThat(captor.getValue().getTweetId()).isEqualTo("auto-gone");
        ArgumentCaptor<SystemSetting> settings = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, org.mockito.Mockito.atLeastOnce()).save(settings.capture());
        assertThat(settings.getAllValues())
            .extracting(SystemSetting::getSettingKey)
            .doesNotContain(XPersonaLearnService.KEY_PROFILE);
    }

    @Test
    void runNow_fillsPostTextFromParentFetch() {
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            XManualStatusClassifier.Status.reply(
                "man-1", "@foo 너무귀여움", "foo", "999", false)
        ));
        when(timelineClient.fetchStatus("999"))
            .thenReturn(new FxTwitterXTimelineClient.ParentStatus("부모 글 본문", true));

        service.runNow("admin");

        ArgumentCaptor<XPersonaExample> captor = ArgumentCaptor.forClass(XPersonaExample.class);
        verify(exampleRepository).save(captor.capture());
        assertThat(captor.getValue().getPostText()).isEqualTo("부모 글 본문");
        assertThat(captor.getValue().isHasPhoto()).isTrue();
        assertThat(captor.getValue().getSource()).isEqualTo(XPersonaExample.Source.TIMELINE);
        verify(timelineClient).fetchStatus("999");
    }

    @Test
    void runNow_parentFetchFailure_leavesPostTextNull() {
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            XManualStatusClassifier.Status.reply(
                "man-1", "@foo 힘빠지긴 할듯", "foo", "999", false)
        ));
        when(timelineClient.fetchStatus("999")).thenReturn(null);

        service.runNow("admin");

        ArgumentCaptor<XPersonaExample> captor = ArgumentCaptor.forClass(XPersonaExample.class);
        verify(exampleRepository).save(captor.capture());
        assertThat(captor.getValue().getPostText()).isNull();
        assertThat(captor.getValue().isHasPhoto()).isFalse();
    }

    @Test
    void runNow_quoteFillsPostTextFromQuoteText() {
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            XManualStatusClassifier.Status.quote("q1", "너무귀여움 ㅋㅋㅋㅋ", "인용된 원글", true)
        ));

        service.runNow("admin");

        ArgumentCaptor<XPersonaExample> captor = ArgumentCaptor.forClass(XPersonaExample.class);
        verify(exampleRepository).save(captor.capture());
        assertThat(captor.getValue().getPostText()).isEqualTo("인용된 원글");
        assertThat(captor.getValue().isHasPhoto()).isTrue();
        assertThat(captor.getValue().getSource()).isEqualTo(XPersonaExample.Source.TIMELINE);
        verify(timelineClient, never()).fetchStatus(any());
    }

    @Test
    void runNow_manualPost_savesTimelinePost() {
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            XManualStatusClassifier.Status.post("p1", "벌써자?", true)
        ));

        service.runNow("admin");

        ArgumentCaptor<XPersonaExample> captor = ArgumentCaptor.forClass(XPersonaExample.class);
        verify(exampleRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(XPersonaExample.Source.TIMELINE_POST);
        assertThat(captor.getValue().getPostText()).isNull();
        assertThat(captor.getValue().isHasPhoto()).isTrue();
        verify(timelineClient, never()).fetchStatus(any());
    }

    @Test
    void runIfDue_doesNotFetchOutsideLearnMinute() {
        Instant noonKst = Instant.parse("2026-08-30T03:00:00Z");
        XPersonaLearnService.LearnResult r = service.runIfDue(noonKst);
        assertThat(r).isNull();
        verify(timelineClient, never()).fetchRecent(any(), anyInt());
    }

    @Test
    void runIfDue_atDawnMinute_runsOnce() {
        Instant dawnKst = Instant.parse("2026-08-29T19:30:00Z");
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of());
        XPersonaLearnService.LearnResult r = service.runIfDue(dawnKst);
        assertThat(r).isNotNull();
        verify(timelineClient).fetchRecent(eq("againspring_net"), anyInt());
    }

    @Test
    void runNow_distillsWithSonnet_backsUpPrevProfile() throws Exception {
        ReflectionTestUtils.setField(service, "llmEnabled", true);
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            XManualStatusClassifier.Status.reply(
                "man-1", "@KoreAgenda 너무귀여움 ㅋㅋㅋㅋ", "KoreAgenda")
        ));
        when(exampleRepository.findTop40BySourceOrderByCreatedAtDesc(XPersonaExample.Source.TIMELINE))
            .thenReturn(List.of(XPersonaExample.builder()
                .source(XPersonaExample.Source.TIMELINE)
                .operatorBody("너무귀여움 ㅋㅋㅋㅋ")
                .build()));
        when(llmProvider.invoke(anyString(), eq("claude-sonnet-5")))
            .thenReturn("{\"summary\":\"한줄 구어체\",\"traits\":[\"ㅋㅋ\"],\"examples\":[\"너무귀여움\"],\"avoid\":[\"습니다체\"],\"situations\":[],\"post_style\":\"짧고 장난\"}");

        XPersonaLearnService.LearnResult r = service.runNow("admin");

        assertThat(r.status()).isEqualTo("OK");
        verify(llmProvider).invoke(anyString(), eq("claude-sonnet-5"));
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SystemSetting::getSettingKey)
            .contains(XPersonaLearnService.KEY_PROFILE, XPersonaLearnService.KEY_PROFILE_PREV);
    }

    @Test
    void runNow_llmSkip_neverSavesProfile() throws Exception {
        ReflectionTestUtils.setField(service, "llmEnabled", true);
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            XManualStatusClassifier.Status.reply(
                "man-1", "@KoreAgenda 너무귀여움 ㅋㅋㅋㅋ", "KoreAgenda")
        ));
        when(exampleRepository.findTop40BySourceOrderByCreatedAtDesc(XPersonaExample.Source.TIMELINE))
            .thenReturn(List.of(XPersonaExample.builder()
                .source(XPersonaExample.Source.TIMELINE)
                .operatorBody("너무귀여움 ㅋㅋㅋㅋ")
                .build()));
        when(llmProvider.invoke(anyString(), eq("claude-sonnet-5")))
            .thenReturn("Credit balance is too low");

        XPersonaLearnService.LearnResult r = service.runNow("admin");

        assertThat(r.status()).isEqualTo("INGESTED_LLM_SKIP");
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SystemSetting::getSettingKey)
            .doesNotContain(XPersonaLearnService.KEY_PROFILE, XPersonaLearnService.KEY_PROFILE_PREV);
    }

    @Test
    void runNow_distillRejected_neverSavesProfile() throws Exception {
        ReflectionTestUtils.setField(service, "llmEnabled", true);
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            XManualStatusClassifier.Status.reply(
                "man-1", "@KoreAgenda 너무귀여움 ㅋㅋㅋㅋ", "KoreAgenda")
        ));
        when(exampleRepository.findTop40BySourceOrderByCreatedAtDesc(XPersonaExample.Source.TIMELINE))
            .thenReturn(List.of(XPersonaExample.builder()
                .source(XPersonaExample.Source.TIMELINE)
                .operatorBody("너무귀여움 ㅋㅋㅋㅋ")
                .build()));
        when(llmProvider.invoke(anyString(), eq("claude-sonnet-5")))
            .thenReturn("{\"summary\":\"Hello this profile is entirely english filler text\",\"traits\":[],\"examples\":[\"hello world entirely english\"],\"avoid\":[]}");

        XPersonaLearnService.LearnResult r = service.runNow("admin");

        assertThat(r.status()).isEqualTo("DISTILL_REJECTED");
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SystemSetting::getSettingKey)
            .doesNotContain(XPersonaLearnService.KEY_PROFILE, XPersonaLearnService.KEY_PROFILE_PREV);
    }

    @Test
    void runNow_charterAppearsInLlmPrompt() throws Exception {
        ReflectionTestUtils.setField(service, "llmEnabled", true);
        org.mockito.Mockito.doReturn("한 줄로 끊는 구어체 유지 원칙 앵커")
            .when(promptLoader).get(XPersonaLearnService.CHARTER_PATH);
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            XManualStatusClassifier.Status.reply(
                "man-1", "@KoreAgenda 너무귀여움 ㅋㅋㅋㅋ", "KoreAgenda")
        ));
        when(exampleRepository.findTop40BySourceOrderByCreatedAtDesc(XPersonaExample.Source.TIMELINE))
            .thenReturn(List.of(XPersonaExample.builder()
                .source(XPersonaExample.Source.TIMELINE)
                .operatorBody("너무귀여움 ㅋㅋㅋㅋ")
                .build()));
        when(exampleRepository.findTop20BySourceOrderByCreatedAtDesc(XPersonaExample.Source.TIMELINE_POST))
            .thenReturn(List.of(XPersonaExample.builder()
                .source(XPersonaExample.Source.TIMELINE_POST)
                .operatorBody("벌써자?")
                .build()));
        when(llmProvider.invoke(anyString(), eq("claude-sonnet-5")))
            .thenReturn("{\"summary\":\"한줄 구어체\",\"traits\":[\"ㅋㅋ\"],\"examples\":[\"너무귀여움\"],\"avoid\":[\"습니다체\"]}");

        service.runNow("admin");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmProvider).invoke(prompt.capture(), eq("claude-sonnet-5"));
        assertThat(prompt.getValue())
            .contains("유지 원칙 — 절대 바꾸지 말 것")
            .contains("한 줄로 끊는 구어체 유지 원칙 앵커")
            .contains("원글 톤")
            .contains("<user_input>");
    }

    @Test
    void formatPair_putsSituationAndOperator() {
        XPersonaExample ex = XPersonaExample.builder()
            .source(XPersonaExample.Source.TIMELINE)
            .postText("사진 글")
            .operatorBody("너무귀여움")
            .build();
        assertThat(XPersonaLearnService.formatPair(ex, 1))
            .contains("가중 1")
            .contains("상황: 사진 글")
            .contains("운영자: 너무귀여움");
        assertThat(XPersonaLearnService.formatDeletedLine(XPersonaExample.builder()
            .source(XPersonaExample.Source.DELETED_AUTO)
            .postText("root-9")
            .operatorBody("문맥없는말")
            .build()))
            .contains("피함")
            .contains("문맥없는말");
    }

    @Test
    void passesSanity_requiresHangulAndExamples() throws Exception {
        ObjectNode ok = (ObjectNode) objectMapper.readTree(
            "{\"summary\":\"한줄 구어체\",\"examples\":[\"너무귀여움\"]}");
        assertThat(XPersonaLearnService.passesSanity(ok)).isTrue();
        ObjectNode noExamples = (ObjectNode) objectMapper.readTree(
            "{\"summary\":\"한줄 구어체\",\"examples\":[]}");
        assertThat(XPersonaLearnService.passesSanity(noExamples)).isFalse();
    }

    private static MarketingXOpsSettingsService.XOpsSettings learningOn() {
        return new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30");
    }
}
