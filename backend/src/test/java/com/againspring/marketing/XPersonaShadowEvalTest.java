package com.againspring.marketing;

import com.againspring.domain.marketing.XOpsAction;
import com.againspring.domain.marketing.XPersonaEval;
import com.againspring.domain.marketing.XPersonaExample;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.marketing.XOpsActionRepository;
import com.againspring.repository.marketing.XPersonaEvalRepository;
import com.againspring.repository.marketing.XPersonaExampleRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XPersonaShadowEvalTest {

    @Mock
    private XCommentComposer commentComposer;
    @Mock
    private LLMProvider llmProvider;
    @Mock
    private PromptLoader promptLoader;
    @Mock
    private PromptSanitizer promptSanitizer;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private XPersonaEvalRepository evalRepository;
    @Mock
    private XPersonaExampleRepository exampleRepository;
    @Mock
    private XOpsActionRepository xOpsActionRepository;

    @InjectMocks
    private XPersonaShadowEval service;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "llmEnabled", true);
        ReflectionTestUtils.setField(service, "model", "claude-sonnet-5");
        when(promptSanitizer.sanitize(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            return arg == null ? "" : arg.toString();
        });
        when(promptLoader.get(XPersonaShadowEval.JUDGE_PROMPT)).thenReturn("채점 지시");
        when(evalRepository.existsByExampleId(any())).thenReturn(false);
        when(evalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(commentComposer.composeOutbound(any(), any(), any(), any()))
            .thenReturn(XCommentComposer.Draft.of("봇 한 줄"));
        when(llmProvider.invoke(anyString(), anyString())).thenReturn("""
            {"overall":96,"tone":97,"length":94,"texture":95,"content":93,"note":"말투와 결이 가깝다"}
            """);
    }

    @Test
    void parseJudgeJson_readsAxesAndNote() {
        XPersonaShadowEval.JudgeScores scores = XPersonaShadowEval.parseJudgeJson(objectMapper, """
            ```json
            {"overall":88,"tone":90,"length":80,"texture":85,"content":70,"note":"길이가 좀 김"}
            ```
            """);
        assertThat(scores).isNotNull();
        assertThat(scores.overall()).isEqualTo(88);
        assertThat(scores.tone()).isEqualTo(90);
        assertThat(scores.length()).isEqualTo(80);
        assertThat(scores.texture()).isEqualTo(85);
        assertThat(scores.content()).isEqualTo(70);
        assertThat(scores.note()).isEqualTo("길이가 좀 김");
    }

    @Test
    void parseJudgeJson_clampsAndRejectsIncomplete() {
        assertThat(XPersonaShadowEval.parseJudgeJson(objectMapper,
            "{\"overall\":120,\"tone\":-3,\"length\":50,\"texture\":50,\"content\":50,\"note\":\"x\"}"))
            .isEqualTo(new XPersonaShadowEval.JudgeScores(100, 0, 50, 50, 50, "x"));
        assertThat(XPersonaShadowEval.parseJudgeJson(objectMapper, "{\"overall\":90}")).isNull();
        assertThat(XPersonaShadowEval.parseJudgeJson(objectMapper, "")).isNull();
    }

    @Test
    void runForNewGold_capsAt10() throws Exception {
        List<XPersonaExample> gold = new ArrayList<>();
        for (long i = 1; i <= 15; i++) {
            gold.add(timeline(i, "상황 " + i, false));
        }
        service.runForNewGold(gold);
        verify(commentComposer, times(10)).composeOutbound(anyString(), eq(List.of()), isNull(), any());
        verify(llmProvider, times(10)).invoke(anyString(), eq("claude-sonnet-5"));
        verify(evalRepository, times(10)).save(any());
    }

    @Test
    void runForNewGold_skipsHasPhoto() throws Exception {
        service.runForNewGold(List.of(
            timeline(1L, "사진 상황", true),
            timeline(2L, "글만", false)));
        verify(commentComposer, times(1)).composeOutbound(eq("글만"), eq(List.of()), isNull(), eq("tw-2"));
        verify(evalRepository, times(1)).save(any());
        ArgumentCaptor<XPersonaEval> captor = ArgumentCaptor.forClass(XPersonaEval.class);
        verify(evalRepository).save(captor.capture());
        assertThat(captor.getValue().getExampleId()).isEqualTo(2L);
    }

    @Test
    void runForNewGold_skipsNullPostText() throws Exception {
        XPersonaExample noSit = timeline(1L, null, false);
        XPersonaExample ok = timeline(2L, "상황", false);
        service.runForNewGold(List.of(noSit, ok));
        verify(commentComposer, never()).composeOutbound(isNull(), any(), any(), any());
        verify(commentComposer, times(1)).composeOutbound(eq("상황"), eq(List.of()), isNull(), eq("tw-2"));
    }

    @Test
    void metrics_deleteRateZeroWhenPostedDenomIsZero() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        when(evalRepository.findByCreatedAtGreaterThanEqual(any())).thenReturn(List.of());
        when(exampleRepository.countBySourceAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            eq(XPersonaExample.Source.DELETED_AUTO), any(), any())).thenReturn(4L);
        when(xOpsActionRepository.countByKindAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            eq(XOpsAction.Kind.OUTBOUND), eq(XOpsAction.Status.POSTED), any(), any())).thenReturn(0L);
        when(xOpsActionRepository.countByKindAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            eq(XOpsAction.Kind.INBOUND), eq(XOpsAction.Status.POSTED), any(), any())).thenReturn(0L);

        XPersonaShadowEval.MimicryMetrics m = service.metrics(now);
        assertThat(m.deleteRate28d()).isNull();
        assertThat(m.sampleInsufficient()).isTrue();
        assertThat(m.gatePassed()).isFalse();
        assertThat(m.sampleCount()).isZero();
    }

    @Test
    void runForNewGold_llmOff_doesNotInvoke() throws Exception {
        ReflectionTestUtils.setField(service, "llmEnabled", false);
        service.runForNewGold(List.of(timeline(1L, "상황", false)));
        service.runAfterLearn(Instant.now());
        verify(commentComposer, never()).composeOutbound(any(), any(), any(), any());
        verify(llmProvider, never()).invoke(anyString(), anyString());
        verify(evalRepository, never()).save(any());
    }

    private static XPersonaExample timeline(long id, String postText, boolean hasPhoto) {
        return XPersonaExample.builder()
            .id(id)
            .source(XPersonaExample.Source.TIMELINE)
            .tweetId("tw-" + id)
            .postText(postText)
            .hasPhoto(hasPhoto)
            .operatorBody("운영자 한 줄")
            .createdAt(Instant.parse("2026-08-01T00:00:00Z"))
            .build();
    }
}
