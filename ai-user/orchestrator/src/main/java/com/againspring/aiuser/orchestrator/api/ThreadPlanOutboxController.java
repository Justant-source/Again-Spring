package com.againspring.aiuser.orchestrator.api;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.service.threadplan.HumanInteractionInboxService;
import com.againspring.aiuser.orchestrator.service.threadplan.ThreadPlanGenerationService;
import com.againspring.aiuser.orchestrator.service.threadplan.ThreadPlanService;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Backend outbox delivery adapter. Duplicate delivery is harmless at both plan and inbox boundaries. */
@RestController
@RequestMapping("/internal/ai-user/outbox")
@RequiredArgsConstructor
public class ThreadPlanOutboxController {
    private final ThreadPlanService planService;
    private final HumanInteractionInboxService inboxService;
    private final BackendBotClient backend;
    private final OrchestratorProperties properties;
    private final ThreadPlanGenerationService threadPlanGenerationService;

    @PostMapping
    public ResponseEntity<Void> accept(@RequestBody Event event) {
        if (event == null || event.type == null || event.postId == null) return ResponseEntity.badRequest().build();
        Instant occurred = event.occurredAt == null ? Instant.now() : event.occurredAt;
        int revision = event.postRevision == null ? 1 : Math.max(1, event.postRevision);
        String type = event.type.trim().toUpperCase();
        if (type.equals("PARTNER_ANSWER_ADDED")) {
            // Revision bump via requestPlan cancels unfinished phase1 items; POSTED phase1 stays.
            enrichPostSnapshot(event);
            String authorBody = authorBodyFromCombined(event.body);
            String partnerBody = partnerBodyFromCombinedOrPayload(event);
            planService.requestPlan(event.postId, revision, event.syntheticPost ? "AI_POST" : "HUMAN_POST",
                    occurred, event.title, event.body, event.category);
            threadPlanGenerationService.ensureCommentPlanForPairedPost(
                    event.postId, revision, event.title, authorBody, partnerBody, event.category, occurred);
        } else if (type.equals("POST_PUBLISHED") || type.equals("PUBLISHED") || type.equals("POST_REVISED")
                || type.equals("POST_UPDATED") || type.equals("REVISED")) {
            enrichPostSnapshot(event);
            planService.requestPlan(event.postId, revision, event.syntheticPost ? "AI_POST" : "HUMAN_POST", occurred,
                    event.title, event.body, event.category);
        } else if (type.equals("COMMENT_CREATED") || type.equals("COMMENT") || type.equals("REPLY_CREATED") || type.equals("REPLY")) {
            // Backend must mark synthetic authors. Missing/true means never treat the action as human input.
            if (!event.syntheticAuthor && event.commentId != null) {
                int ttlDays = Math.max(1, properties.getHumanReply().getInboxTtlDays());
                inboxService.observe(event.postId, event.commentId, event.parentCommentId, safe(event.authorId),
                        (type.equals("REPLY_CREATED") || type.equals("REPLY")) ? "REPLY" : "COMMENT",
                        occurred, occurred.plus(ttlDays, ChronoUnit.DAYS));
            }
        } else if (type.equals("POST_BLOCKED") || type.equals("POST_DELETED") || type.equals("POST_PRIVATE")) {
            planService.cancelPlanAndUnpublishedItemsForPost(event.postId);
        }
        return ResponseEntity.accepted().build();
    }
    private static String safe(String value) { return value == null ? "unknown" : value; }
    private void enrichPostSnapshot(Event event) {
        backend.getPost(event.postId).ifPresent(post -> {
            event.title = string(post.get("userTitle"), string(post.get("title"), event.title));
            String authorBody = string(post.get("bodyPublished"), string(post.get("body"), event.body));
            String partnerBody = string(post.get("partnerBodyPublished"), "");
            if (event.body == null || event.body.isBlank()) {
                event.body = authorBody;
            }
            // Paired posts: ground comment plans on both sides.
            if (partnerBody != null && !partnerBody.isBlank()) {
                String author = event.body == null || event.body.isBlank() ? authorBody : event.body;
                if (author == null || author.isBlank() || !author.contains(partnerBody)) {
                    event.body = ThreadPlanGenerationService.combinePairedSourceBody(author, partnerBody);
                }
            }
            event.category = string(post.get("category"), event.category);
        });
    }

    /** Prefer payload.partnerBody; else split combined [작성자]/[상대방] snapshot. */
    private static String partnerBodyFromCombinedOrPayload(Event event) {
        if (event.payload != null) {
            Object p = event.payload.get("partnerBody");
            if (p == null) p = event.payload.get("partnerBodyPublished");
            if (p != null && !String.valueOf(p).isBlank()) return String.valueOf(p).strip();
        }
        String combined = event.body == null ? "" : event.body;
        int idx = combined.indexOf("[상대방]");
        if (idx >= 0) {
            return combined.substring(idx + "[상대방]".length()).strip();
        }
        return "";
    }

    private static String authorBodyFromCombined(String combined) {
        if (combined == null || combined.isBlank()) return "";
        int partnerIdx = combined.indexOf("[상대방]");
        String authorSection = partnerIdx >= 0 ? combined.substring(0, partnerIdx) : combined;
        int authorIdx = authorSection.indexOf("[작성자]");
        if (authorIdx >= 0) {
            return authorSection.substring(authorIdx + "[작성자]".length()).strip();
        }
        return authorSection.strip();
    }

    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }

    @Data
    public static class Event {
        private String eventId, type, postId, commentId, parentCommentId, authorId, title, body, category;
        private Integer postRevision;
        private boolean syntheticPost;
        private boolean syntheticAuthor;
        private Instant occurredAt;
        private Map<String, Object> payload;
    }
}
