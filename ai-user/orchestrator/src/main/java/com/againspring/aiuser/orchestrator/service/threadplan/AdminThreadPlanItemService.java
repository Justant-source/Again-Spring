package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemStatus;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemType;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanItemRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.util.LiteralNewlineNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin view/edit of pending COMMENT/REPLY plan items for an already-published post.
 * Engagement items (likes/votes/views) are intentionally excluded.
 */
@Service
@RequiredArgsConstructor
public class AdminThreadPlanItemService {
    private static final Set<ThreadPlanItemType> CONTENT_TYPES = Set.of(
            ThreadPlanItemType.COMMENT, ThreadPlanItemType.REPLY);
    private static final Set<ThreadPlanItemStatus> PENDING = Set.of(
            ThreadPlanItemStatus.RESERVED,
            ThreadPlanItemStatus.SCHEDULED,
            ThreadPlanItemStatus.PROCESSING,
            ThreadPlanItemStatus.FAILED);

    private final AiThreadPlanItemRepository itemRepository;
    private final PersonaRepository personaRepository;
    private final ContentSafetyGuard safetyGuard;
    private final CandidateScheduleSupport scheduleSupport;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPendingContent(String postId) {
        if (postId == null || postId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "postId required");
        }
        return itemRepository.findByPostAndTypesAndStatuses(postId, CONTENT_TYPES, PENDING).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * Upsert-style patch: each row identified by {@code planItemId}. Optional fields:
     * body, personaId, scheduledAt. {@code cancel=true} → CANCELLED.
     * Items not mentioned are left alone (unlike published-comment soft-delete).
     */
    @Transactional
    public List<Map<String, Object>> patchPendingContent(String postId, List<Map<String, Object>> rows) {
        if (postId == null || postId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "postId required");
        }
        if (rows == null) {
            return listPendingContent(postId);
        }
        for (Map<String, Object> row : rows) {
            String id = text(row.get("planItemId"));
            if (id.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "planItemId required");
            }
            AiThreadPlanItem item = itemRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "plan item not found: " + id));
            if (!postId.equals(item.getTargetPostId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "plan item belongs to another post");
            }
            if (!CONTENT_TYPES.contains(item.getItemType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only COMMENT/REPLY editable");
            }
            if (!PENDING.contains(item.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "item not pending (status=" + item.getStatus() + ")");
            }

            if (Boolean.TRUE.equals(row.get("cancel")) || "true".equalsIgnoreCase(text(row.get("cancel")))) {
                item.setStatus(ThreadPlanItemStatus.CANCELLED);
                item.setLeaseOwner(null);
                item.setLeaseUntil(null);
                itemRepository.save(item);
                continue;
            }

            if (row.containsKey("body") && row.get("body") != null) {
                String body = text(row.get("body"));
                if (body.isBlank() || body.length() > 2000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid body");
                }
                if (!safetyGuard.check(body, ContentSafetyGuard.ContentType.COMMENT).passed()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsafe candidate body");
                }
                item.setBody(body);
            }
            if (row.containsKey("personaId") && row.get("personaId") != null) {
                String personaId = text(row.get("personaId"));
                if (personaId.isBlank() || !personaRepository.existsById(personaId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown personaId");
                }
                item.setPersonaId(personaId);
            }
            if (row.containsKey("scheduledAt") && row.get("scheduledAt") != null) {
                Instant at = scheduleSupport.parseScheduledAt(row.get("scheduledAt"));
                if (at == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid scheduledAt");
                }
                item.setScheduledAt(at);
                item.setNotBefore(at);
                // Re-arm if it was stuck PROCESSING without a live lease.
                if (item.getStatus() == ThreadPlanItemStatus.FAILED
                        || item.getStatus() == ThreadPlanItemStatus.PROCESSING) {
                    item.setStatus(ThreadPlanItemStatus.SCHEDULED);
                    item.setLeaseOwner(null);
                    item.setLeaseUntil(null);
                }
            }
            itemRepository.save(item);
        }
        return listPendingContent(postId);
    }

    private Map<String, Object> toView(AiThreadPlanItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planItemId", item.getId());
        m.put("parentPlanItemId", item.getParentItemId());
        m.put("personaId", item.getPersonaId());
        m.put("body", item.getBody());
        m.put("type", item.getItemType() == ThreadPlanItemType.REPLY ? "REPLY" : "COMMENT");
        m.put("status", item.getStatus() != null ? item.getStatus().name() : null);
        m.put("scheduledAt", item.getScheduledAt() != null ? item.getScheduledAt().toString() : null);
        m.put("sequenceNo", item.getSequenceNo());
        m.put("pending", true);
        return m;
    }

    private static String text(Object value) {
        if (value == null) return "";
        return LiteralNewlineNormalizer.normalize(String.valueOf(value)).trim();
    }
}
