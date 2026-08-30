package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.safety.KeywordGuard;
import com.againspring.safety.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
