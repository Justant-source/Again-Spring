package com.againspring.marketing;

import com.againspring.domain.marketing.XCommentTrace;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.repository.marketing.XCommentTraceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XCommentTraceRecorderTest {

    @Mock
    private XCommentTraceRepository repository;
    @InjectMocks
    private XCommentTraceRecorder recorder;

    @Test
    void record_storesInputPromptRawAndPostedId() {
        XOpsAction action = XOpsAction.builder()
            .id(9L)
            .kind(XOpsAction.Kind.OUTBOUND)
            .targetTweetId("root-1")
            .status(XOpsAction.Status.POSTED)
            .postedTweetId("out-1")
            .build();
        XCommentComposer.Draft draft = XCommentComposer.Draft.of("너무귀여움")
            .withLlmCall(new XCommentComposer.LlmCall(
                "claude-sonnet-5-5", "low", "PROMPT", "{\"ok\":true,\"body\":\"너무귀여움\"}"));
        when(repository.save(org.mockito.ArgumentMatchers.any(XCommentTrace.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        recorder.record(XOpsAction.Kind.OUTBOUND, action, draft, "대상 글", "1. 다른댓글", true);

        ArgumentCaptor<XCommentTrace> captor = ArgumentCaptor.forClass(XCommentTrace.class);
        verify(repository).save(captor.capture());
        XCommentTrace row = captor.getValue();
        assertThat(row.getActionId()).isEqualTo(9L);
        assertThat(row.getKind()).isEqualTo(XOpsAction.Kind.OUTBOUND);
        assertThat(row.getTargetTweetId()).isEqualTo("root-1");
        assertThat(row.getModel()).isEqualTo("claude-sonnet-5-5");
        assertThat(row.getEffort()).isEqualTo("low");
        assertThat(row.getInputText()).isEqualTo("대상 글");
        assertThat(row.getContextText()).isEqualTo("1. 다른댓글");
        assertThat(row.getLlmPrompt()).isEqualTo("PROMPT");
        assertThat(row.getLlmResponse()).contains("너무귀여움");
        assertThat(row.getBody()).isEqualTo("너무귀여움");
        assertThat(row.isHasPhoto()).isTrue();
        assertThat(row.getStatus()).isEqualTo(XOpsAction.Status.POSTED);
        assertThat(row.getPostedTweetId()).isEqualTo("out-1");
    }

    @Test
    void record_keepsRejectedBodyWhenGuardSkips() {
        XOpsAction action = XOpsAction.builder()
            .kind(XOpsAction.Kind.OUTBOUND)
            .targetTweetId("root-2")
            .status(XOpsAction.Status.SKIPPED)
            .skipReason("TOO_LONG")
            .build();
        XCommentComposer.Draft draft = new XCommentComposer.Draft(
            false, "너무 긴 댓글", null,
            new XCommentComposer.LlmCall("claude-sonnet-5-5", "low", "P", "RAW"));

        XCommentTrace row = XCommentTraceRecorder.toRow(
            XOpsAction.Kind.OUTBOUND, action, draft, "대상", "", false);

        assertThat(row.getStatus()).isEqualTo(XOpsAction.Status.SKIPPED);
        assertThat(row.getSkipReason()).isEqualTo("TOO_LONG");
        assertThat(row.getBody()).isEqualTo("너무 긴 댓글");
        assertThat(row.getLlmResponse()).isEqualTo("RAW");
    }

    @Test
    void record_swallowsRepositoryFailure() {
        when(repository.save(org.mockito.ArgumentMatchers.any(XCommentTrace.class)))
            .thenThrow(new RuntimeException("db down"));

        recorder.record(
            XOpsAction.Kind.INBOUND,
            XOpsAction.builder().status(XOpsAction.Status.POSTED).targetTweetId("c1").build(),
            XCommentComposer.Draft.of("답"),
            "받은 댓글",
            "our-1",
            false);
    }
}
