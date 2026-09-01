package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XPersonaExample;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XPersonaExampleRepository;
import com.againspring.safety.KeywordGuard;
import com.againspring.safety.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XCommentComposerTest {

    private static final String DISTINCT_PROFILE =
        "{\"summary\":\"테스트페르소나XYZ 반말 ㅋㅋㅋ\",\"traits\":[\"한 줄\"],\"examples\":[\"힘빠지긴 할듯\"],\"avoid\":[\"습니다체\",\"판결\"]}";

    @Mock
    private SystemSettingRepository systemSettingRepository;
    @Mock
    private LLMProvider llmProvider;
    @Mock
    private PromptSanitizer promptSanitizer;
    @Mock
    private KeywordGuard keywordGuard;
    @Mock
    private PromptLoader promptLoader;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private XPersonaExampleRepository exampleRepository;

    @InjectMocks
    private XCommentComposer composer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(composer, "llmEnabled", true);
        ReflectionTestUtils.setField(composer, "model", "claude-haiku-4-5-20251001");
        when(promptSanitizer.sanitize(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            return arg == null ? "" : arg.toString();
        });
        when(keywordGuard.scanLLMOutput(any())).thenReturn(ScanResult.empty());
        when(keywordGuard.applyOutputFilter(any())).thenAnswer(inv -> inv.getArgument(0));
        when(systemSettingRepository.findById(XPersonaLearnService.KEY_PROFILE))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(XPersonaLearnService.KEY_PROFILE)
                .settingValue(DISTINCT_PROFILE)
                .build()));
        when(exampleRepository.findTop40BySourceOrderByCreatedAtDesc(any()))
            .thenReturn(List.of());
        when(exampleRepository.findTop20BySourceOrderByCreatedAtDesc(any()))
            .thenReturn(List.of());
    }

    @Test
    void composeReply_usesPersonaAndReturnsShortKoreanDraft() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenReturn("힘빠지긴 할듯 ㅋㅋㅋ");

        XCommentComposer.Draft draft = composer.composeReply("퇴근하고 왔는데 진짜 힘드네", "부모님이 또 잔소리");

        assertThat(draft.skip()).isFalse();
        assertThat(draft.body()).isEqualTo("힘빠지긴 할듯 ㅋㅋㅋ");
        assertThat(draft.skipReason()).isNull();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmProvider).invoke(promptCaptor.capture(), eq("claude-haiku-4-5-20251001"));
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("<user_input>");
        assertThat(prompt).contains("테스트페르소나XYZ");
        assertThat(prompt).contains("퇴근하고 왔는데 진짜 힘드네");
    }

    @Test
    void composeReply_blankVoiceMarker_skipsNoVoice() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenReturn("할 말 없음");

        XCommentComposer.Draft draft = composer.composeReply("아무 트윗", null);

        assertThat(draft.skip()).isTrue();
        assertThat(draft.skipReason()).isEqualTo("NO_VOICE");
        assertThat(draft.body()).isNull();
    }

    @Test
    void composeReply_creditBalanceError_skipsLlmErrorWithoutPostingString() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenReturn("Credit balance is too low");

        XCommentComposer.Draft draft = composer.composeReply("아무 트윗", null);

        assertThat(draft.skip()).isTrue();
        assertThat(draft.skipReason()).isEqualTo("LLM_ERROR");
        assertThat(draft.body()).isNull();
        assertThat(draft.body()).isNotEqualTo("Credit balance is too low");
    }

    @Test
    void composeReply_verdictLanguage_skipsSafety() throws Exception {
        when(llmProvider.invoke(anyString(), anyString())).thenReturn("그건 작성자 쪽 판결이야");

        XCommentComposer.Draft draft = composer.composeReply("누가 잘못한 거야?", null);

        assertThat(draft.skip()).isTrue();
        assertThat(draft.skipReason()).isEqualTo("SAFETY");
        assertThat(draft.body()).isNull();
    }

    @Test
    void composeReply_llmDisabled_skipsDevLlmOffWithoutInvoking() throws Exception {
        ReflectionTestUtils.setField(composer, "llmEnabled", false);

        XCommentComposer.Draft draft = composer.composeReply("아무 트윗", null);

        assertThat(draft.skip()).isTrue();
        assertThat(draft.skipReason()).isEqualTo("DEV_LLM_OFF");
        assertThat(draft.body()).isNull();
        verify(llmProvider, never()).invoke(anyString(), anyString());
    }

    @Test
    void composeOutbound_jsonUnsure_skips() throws Exception {
        stubOutboundPrompts();
        when(llmProvider.invoke(anyString(), anyString()))
            .thenReturn("{\"ok\":false,\"reason\":\"UNSURE\"}");

        XCommentComposer.Draft draft = composer.composeOutbound("아무 트윗", List.of("다른댓글"), null);

        assertThat(draft.skip()).isTrue();
        assertThat(draft.skipReason()).isEqualTo("UNSURE");
        assertThat(draft.body()).isNull();
    }

    @Test
    void composeOutbound_parseFailOrEmpty_skipsUnsure() throws Exception {
        stubOutboundPrompts();
        when(llmProvider.invoke(anyString(), anyString())).thenReturn("ㅋㅋㅋㅋ 그냥 텍스트");

        XCommentComposer.Draft notJson = composer.composeOutbound("트윗", List.of(), null);
        assertThat(notJson.skipReason()).isEqualTo("UNSURE");

        when(llmProvider.invoke(anyString(), anyString())).thenReturn("{\"ok\":true,\"body\":\"\"}");
        XCommentComposer.Draft empty = composer.composeOutbound("트윗", List.of(), null);
        assertThat(empty.skipReason()).isEqualTo("UNSURE");
    }

    @Test
    void composeOutbound_photoBytesStayOutOfPrompt() throws Exception {
        stubOutboundPrompts();
        String jpeg = "QUFBQUFBQUE=";
        when(llmProvider.invoke(anyString(), anyString(), anyList()))
            .thenReturn("{\"ok\":true,\"body\":\"사진이쁘다\"}");

        XCommentComposer.Draft draft = composer.composeOutbound("강아지", List.of("귀엽네"), jpeg);

        assertThat(draft.skip()).isFalse();
        assertThat(draft.body()).isEqualTo("사진이쁘다");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmProvider).invoke(promptCaptor.capture(), eq("claude-haiku-4-5-20251001"), anyList());
        assertThat(promptCaptor.getValue()).doesNotContain(jpeg);
        verify(llmProvider, never()).invoke(anyString(), anyString());
    }

    @Test
    void composeOutbound_includesMatchingTimelineFewShot() throws Exception {
        stubOutboundPrompts();
        when(exampleRepository.findTop40BySourceOrderByCreatedAtDesc(XPersonaExample.Source.TIMELINE))
            .thenReturn(List.of(XPersonaExample.builder()
                .source(XPersonaExample.Source.TIMELINE)
                .tweetId("t1")
                .postText("강아지 사진 올렸다")
                .hasPhoto(false)
                .operatorBody("너무귀여움")
                .build()));
        when(llmProvider.invoke(anyString(), anyString()))
            .thenReturn("{\"ok\":true,\"body\":\"귀엽네\"}");

        composer.composeOutbound("고양이도 귀엽다", List.of(), null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmProvider).invoke(promptCaptor.capture(), eq("claude-haiku-4-5-20251001"));
        assertThat(promptCaptor.getValue()).contains("너무귀여움");
        assertThat(promptCaptor.getValue()).contains("강아지 사진 올렸다");
        assertThat(promptCaptor.getValue()).contains("운영자가 같은 종류 글에 직접 단 댓글");
    }

    @Test
    void composeOutbound_includesDeletedAutoAvoid() throws Exception {
        stubOutboundPrompts();
        when(exampleRepository.findTop20BySourceOrderByCreatedAtDesc(XPersonaExample.Source.DELETED_AUTO))
            .thenReturn(List.of(XPersonaExample.builder()
                .source(XPersonaExample.Source.DELETED_AUTO)
                .tweetId("gone")
                .operatorBody("문맥없는말")
                .build()));
        when(llmProvider.invoke(anyString(), anyString()))
            .thenReturn("{\"ok\":true,\"body\":\"귀엽네\"}");

        composer.composeOutbound("고양이도 귀엽다", List.of(), null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmProvider).invoke(promptCaptor.capture(), eq("claude-haiku-4-5-20251001"));
        assertThat(promptCaptor.getValue()).contains("문맥없는말");
        assertThat(promptCaptor.getValue()).contains("운영자가 지운 자동댓글");
    }

    private void stubOutboundPrompts() throws Exception {
        when(promptLoader.get("marketing/x-outbound-reply.md")).thenReturn("JSON only");
        when(promptLoader.get("marketing/x-outbound-donts.md")).thenReturn("- no spam");
    }
}
