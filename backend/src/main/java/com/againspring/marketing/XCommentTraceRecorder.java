package com.againspring.marketing;

import com.againspring.domain.marketing.XCommentTrace;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.repository.marketing.XCommentTraceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Persists the model input and result for one Justant-Bot comment attempt.
 * A failed insert must not roll back the ledger row that already committed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XCommentTraceRecorder {

    private final XCommentTraceRepository repository;

    public void record(
            XOpsAction.Kind kind,
            XOpsAction action,
            XCommentComposer.Draft draft,
            String inputText,
            String contextText,
            boolean hasPhoto) {
        if (kind != XOpsAction.Kind.OUTBOUND && kind != XOpsAction.Kind.INBOUND) {
            return;
        }
        try {
            repository.save(toRow(kind, action, draft, inputText, contextText, hasPhoto));
        } catch (Exception e) {
            log.warn("[x-comment-trace] save failed kind={}: {}", kind, e.getMessage());
        }
    }

    static XCommentTrace toRow(
            XOpsAction.Kind kind,
            XOpsAction action,
            XCommentComposer.Draft draft,
            String inputText,
            String contextText,
            boolean hasPhoto) {
        XCommentComposer.LlmCall call = draft == null ? null : draft.llmCall();
        XOpsAction.Status status = action != null && action.getStatus() != null
            ? action.getStatus()
            : (draft != null && draft.skip() ? XOpsAction.Status.SKIPPED : XOpsAction.Status.FAILED);
        return XCommentTrace.builder()
            .actionId(action == null ? null : action.getId())
            .kind(kind)
            .targetTweetId(action == null ? null : action.getTargetTweetId())
            .targetAuthorHandle(action == null ? null : action.getTargetAuthorHandle())
            .model(call == null ? null : call.model())
            .effort(call == null ? null : call.effort())
            .inputText(inputText)
            .contextText(contextText)
            .llmPrompt(call == null ? null : call.prompt())
            .llmResponse(call == null ? null : call.rawResponse())
            .body(draft == null ? null : draft.body())
            .hasPhoto(hasPhoto)
            .status(status)
            .skipReason(action != null && action.getSkipReason() != null
                ? action.getSkipReason()
                : (draft == null ? null : draft.skipReason()))
            .postedTweetId(action == null ? null : action.getPostedTweetId())
            .build();
    }
}
