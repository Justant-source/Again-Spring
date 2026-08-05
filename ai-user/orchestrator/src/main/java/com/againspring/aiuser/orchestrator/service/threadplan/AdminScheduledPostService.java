package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.util.LiteralNewlineNormalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin CRUD for held posts in {@code ai_scheduled_posts}. Edits are only allowed while
 * status is {@link ScheduledPostStatus#SCHEDULED} so they never race a live publish lease.
 */
@Service
@RequiredArgsConstructor
public class AdminScheduledPostService {
    private final AiScheduledPostRepository repository;
    private final PersonaRepository personaRepository;
    private final ContentSafetyGuard safetyGuard;
    private final CandidateScheduleSupport scheduleSupport;
    private final ObjectMapper objectMapper;
    private final SourceReservationSupport sourceReservationSupport;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(List<ScheduledPostStatus> statuses) {
        List<ScheduledPostStatus> filter = (statuses == null || statuses.isEmpty())
                ? List.of(ScheduledPostStatus.SCHEDULED)
                : statuses;
        return repository.findByStatusInOrderByScheduledPublishAtAsc(filter).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String id) {
        return toDetail(require(id));
    }

    @Transactional
    public Map<String, Object> patch(String id, Map<String, Object> body) {
        AiScheduledPost row = require(id);
        if (row.getStatus() != ScheduledPostStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "only SCHEDULED holdings can be edited (status=" + row.getStatus() + ")");
        }

        Instant oldSlot = row.getScheduledPublishAt();
        Instant newSlot = oldSlot;
        if (body.containsKey("scheduledPublishAt") && body.get("scheduledPublishAt") != null) {
            Instant parsed = scheduleSupport.parseScheduledAt(body.get("scheduledPublishAt"));
            if (parsed == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid scheduledPublishAt");
            }
            newSlot = parsed;
            row.setScheduledPublishAt(newSlot);
        }

        Map<String, Object> candidates = readCandidates(row);
        scheduleSupport.enrichMissingScheduledAts(candidates, oldSlot);

        boolean itemsProvided = body.containsKey("items");
        if (itemsProvided) {
            candidates.put("items", validateAndNormalizeItems(body.get("items")));
        } else if (!newSlot.equals(oldSlot)) {
            scheduleSupport.shiftScheduledAts(candidates, Duration.between(oldSlot, newSlot));
        }

        if (body.containsKey("title") && body.get("title") != null) {
            String title = text(body.get("title"));
            if (title.isBlank() || title.length() > 200) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid title");
            }
            row.setTitle(title);
        }
        if (body.containsKey("body") && body.get("body") != null) {
            String postBody = text(body.get("body"));
            if (postBody.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid body");
            }
            ContentSafetyGuard.GuardResult guard = safetyGuard.check(postBody, ContentSafetyGuard.ContentType.POST);
            if (!guard.passed()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsafe body: " + guard.reason());
            }
            row.setBody(postBody);
        }
        if (body.containsKey("category") && body.get("category") != null) {
            row.setCategory(text(body.get("category")));
        }

        // Keep nested post blob in sync with row columns (publisher uses row; JSON post is for admin preview).
        syncPostBlob(candidates, row);

        try {
            row.setCandidatesJson(objectMapper.writeValueAsString(candidates));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to serialize candidates");
        }
        return toDetail(repository.save(row));
    }

    @Transactional
    public Map<String, Object> cancel(String id) {
        AiScheduledPost row = require(id);
        if (row.getStatus() != ScheduledPostStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "only SCHEDULED holdings can be cancelled (status=" + row.getStatus() + ")");
        }
        sourceReservationSupport.releaseFromCandidatesJson(row.getCandidatesJson());
        row.setStatus(ScheduledPostStatus.CANCELLED);
        return toSummary(repository.save(row));
    }

    private AiScheduledPost require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "scheduled post not found"));
    }

    private Map<String, Object> toSummary(AiScheduledPost row) {
        Map<String, Object> candidates = readCandidates(row);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("personaId", row.getPersonaId());
        m.put("title", row.getTitle());
        m.put("category", row.getCategory());
        m.put("status", row.getStatus().name());
        m.put("scheduledPublishAt", row.getScheduledPublishAt() != null ? row.getScheduledPublishAt().toString() : null);
        m.put("itemCount", scheduleSupport.countItems(candidates));
        m.put("origin", row.getOrigin());
        m.put("createdAt", row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
        m.put("failureCode", row.getFailureCode());
        return m;
    }

    private Map<String, Object> toDetail(AiScheduledPost row) {
        Map<String, Object> candidates = readCandidates(row);
        scheduleSupport.enrichMissingScheduledAts(candidates, row.getScheduledPublishAt());

        Map<String, Object> m = toSummary(row);
        m.put("body", row.getBody());
        m.put("provider", row.getProvider());
        m.put("model", row.getModel());
        m.put("publishedPostId", row.getPublishedPostId());
        m.put("items", toItemViews(candidates));
        return m;
    }

    private List<Map<String, Object>> toItemViews(Map<String, Object> candidates) {
        List<Map<String, Object>> items = scheduleSupport.mutableItems(candidates);
        List<Map<String, Object>> views = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            Map<String, Object> view = new LinkedHashMap<>();
            String parentRef = text(item.get("parentRef"));
            view.put("ref", text(item.get("ref")));
            view.put("parentRef", parentRef.isBlank() ? null : parentRef);
            view.put("personaId", text(item.get("personaId")));
            view.put("body", text(item.get("body")));
            view.put("type", parentRef.isBlank() ? "COMMENT" : "REPLY");
            Instant at = scheduleSupport.parseScheduledAt(item.get("scheduledAt"));
            view.put("scheduledAt", at != null ? at.toString() : null);
            if (item.get("stance") != null) view.put("stance", item.get("stance"));
            if (item.get("priority") != null) view.put("priority", item.get("priority"));
            views.add(view);
        }
        return views;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> validateAndNormalizeItems(Object rawItems) {
        if (!(rawItems instanceof List<?> rows)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items must be an array");
        }
        if (rows.size() > 24) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "too many items");
        }
        Set<String> seenRefs = new java.util.HashSet<>();
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object raw : rows) {
            if (!(raw instanceof Map<?, ?> row)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid item");
            }
            String ref = text(row.get("ref"));
            String parentRef = text(row.get("parentRef"));
            String body = text(row.get("body"));
            String personaId = text(row.get("personaId"));
            Instant scheduledAt = scheduleSupport.parseScheduledAt(row.get("scheduledAt"));
            if (ref.isBlank() || body.isBlank() || personaId.isBlank() || body.length() > 2000 || !seenRefs.add(ref)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid candidate fields");
            }
            if (!parentRef.isBlank() && !seenRefs.contains(parentRef)) {
                // parent must appear earlier — seenRefs already has current ref, so check excluding it
                boolean parentEarlier = normalized.stream().anyMatch(i -> parentRef.equals(text(i.get("ref"))));
                if (!parentEarlier) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown or out-of-order parentRef: " + parentRef);
                }
            }
            if (!personaRepository.existsById(personaId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown personaId: " + personaId);
            }
            if (!safetyGuard.check(body, ContentSafetyGuard.ContentType.COMMENT).passed()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsafe candidate body");
            }
            if (scheduledAt == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledAt required for each item");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ref", ref);
            if (!parentRef.isBlank()) item.put("parentRef", parentRef);
            item.put("personaId", personaId);
            item.put("body", body);
            item.put("scheduledAt", scheduledAt.toString());
            if (row.get("stance") != null) item.put("stance", row.get("stance"));
            if (row.get("priority") != null) item.put("priority", row.get("priority"));
            normalized.add(item);
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private void syncPostBlob(Map<String, Object> candidates, AiScheduledPost row) {
        Object postRaw = candidates.get("post");
        Map<String, Object> postMap = postRaw instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
        postMap.put("title", row.getTitle());
        postMap.put("body", row.getBody());
        candidates.put("post", postMap);
    }

    private Map<String, Object> readCandidates(AiScheduledPost row) {
        if (row.getCandidatesJson() == null || row.getCandidatesJson().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(row.getCandidatesJson(), new TypeReference<>() { });
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "corrupt candidates_json");
        }
    }

    private static String text(Object value) {
        if (value == null) return "";
        return LiteralNewlineNormalizer.normalize(String.valueOf(value)).trim();
    }
}
