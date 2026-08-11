package com.againspring.service.admin;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.enums.PostStatus;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.ai.AiCorrectionService;
import com.againspring.service.ai.AiUserOutboxWriter;
import com.againspring.service.community.PostSearchNgramIndexer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin thread editor for already-published posts — same shape as scheduled-holding detail
 * (post fields + comment/reply timeline with editable timestamps), plus pending AI plan items
 * that have not posted yet.
 */
@Service
@RequiredArgsConstructor
public class AdminPublishedThreadService {

    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserRepository userRepository;
    private final AiCorrectionService aiCorrectionService;
    private final AiUserOutboxWriter aiUserOutboxWriter;
    private final ThreadPlanItemProxyService threadPlanItemProxy;
    private final PostSearchNgramIndexer postSearchNgramIndexer;

    @Transactional(readOnly = true)
    public Map<String, Object> getThread(String postId) {
        Post post = requirePost(postId);
        List<PostComment> comments = postCommentRepository
                .findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId);
        List<Map<String, Object>> pending = threadPlanItemProxy.listPending(postId);
        return toThreadView(post, comments, pending);
    }

    @Transactional
    public Map<String, Object> patchThread(String postId, UpdateThreadRequest req, String adminId) {
        Post post = requirePost(postId);
        String originalBody = post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw();
        boolean contentChanged = false;

        if (req.getTitle() != null) {
            post.setTitle(req.getTitle());
            post.setUserTitle(req.getTitle());
        }
        if (req.getBody() != null) {
            post.setBodyRaw(req.getBody());
            post.setBodyPublished(req.getBody());
            contentChanged = !req.getBody().equals(originalBody);
        }
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            post.setCategory(PostCategory.valueOf(req.getCategory()));
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            // CLOSED는 시한부 투표 레거시 — 저장 시 VOTING으로 정규화
            PostStatus status = PostStatus.valueOf(req.getStatus());
            post.setStatus(status == PostStatus.CLOSED ? PostStatus.VOTING : status);
        }
        if (req.getViewCount() != null) {
            post.setViewCount(req.getViewCount());
        }
        if (req.getCreatedAt() != null) {
            Instant at = parseInstant(req.getCreatedAt());
            if (at == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid createdAt");
            }
            post.setCreatedAt(at);
        }

        if (contentChanged) {
            post.advanceContentRevision();
        }

        Post updated = postRepository.save(post);
        if (req.getTitle() != null || req.getBody() != null) {
            postSearchNgramIndexer.reindex(updated);
        }
        if (contentChanged) {
            aiUserOutboxWriter.postRevised(updated, "ADMIN_CONTENT_UPDATED");
            try {
                aiCorrectionService.captureEdit("POST", postId, originalBody, req.getBody(),
                        adminId != null ? adminId : "admin");
            } catch (Exception ignored) {
            }
        } else if (req.getStatus() != null) {
            String eventType = updated.getStatus() == PostStatus.BLOCKED ? "POST_BLOCKED" : "POST_STATUS_CHANGED";
            aiUserOutboxWriter.postLifecycleChanged(updated, eventType, "ADMIN_STATUS_UPDATED");
        }

        if (req.getItems() != null) {
            applyItems(postId, req.getItems(), adminId);
        }
        if (req.getPendingItems() != null) {
            applyPendingItems(postId, req.getPendingItems());
        }

        List<PostComment> comments = postCommentRepository
                .findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId);
        List<Map<String, Object>> pending = threadPlanItemProxy.listPending(postId);
        return toThreadView(postRepository.findById(postId).orElse(updated), comments, pending);
    }

    /** Keep/update listed pending items; cancel any currently pending plan item not listed. */
    private void applyPendingItems(String postId, List<PendingItemRequest> items) {
        List<Map<String, Object>> current = threadPlanItemProxy.listPending(postId);
        Set<String> keep = items.stream()
                .map(PendingItemRequest::getPlanItemId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());

        List<Map<String, Object>> payload = new ArrayList<>(toPendingPayload(items));
        for (Map<String, Object> row : current) {
            String id = row.get("planItemId") != null ? String.valueOf(row.get("planItemId")) : "";
            if (!id.isBlank() && !keep.contains(id)) {
                payload.add(Map.of("planItemId", id, "cancel", true));
            }
        }
        threadPlanItemProxy.patchPending(postId, payload);
    }

    private void applyItems(String postId, List<ThreadItemRequest> items, String adminId) {
        List<PostComment> existing = postCommentRepository
                .findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId);
        Map<Long, PostComment> byId = existing.stream()
                .collect(Collectors.toMap(PostComment::getId, c -> c, (a, b) -> a, LinkedHashMap::new));

        Set<Long> keep = new HashSet<>();
        for (ThreadItemRequest item : items) {
            if (item.getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "item id required (no create in this API)");
            }
            PostComment comment = byId.get(item.getId());
            if (comment == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown comment id: " + item.getId());
            }
            keep.add(item.getId());

            String original = comment.getBody();
            boolean bodyChanged = false;
            if (item.getBody() != null) {
                bodyChanged = !item.getBody().equals(original);
                comment.setBody(item.getBody());
            }
            if (item.getAuthorId() != null && !item.getAuthorId().isBlank()) {
                comment.setAuthorId(item.getAuthorId());
            }
            if (item.getCreatedAt() != null) {
                Instant at = parseInstant(item.getCreatedAt());
                if (at == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid item createdAt");
                }
                comment.setCreatedAt(at);
            }
            if (bodyChanged) {
                comment.advanceContentRevision();
            }
            PostComment saved = postCommentRepository.save(comment);
            if (bodyChanged) {
                Post post = postRepository.findById(postId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
                aiUserOutboxWriter.commentUpdated(post, saved);
                try {
                    aiCorrectionService.captureEdit("COMMENT", String.valueOf(saved.getId()),
                            original, saved.getBody(), adminId != null ? adminId : "admin");
                } catch (Exception ignored) {
                }
            }
        }

        Instant now = Instant.now();
        String actor = adminId != null ? adminId : "admin";
        for (PostComment comment : existing) {
            if (!keep.contains(comment.getId())) {
                comment.setDeletedAt(now);
                comment.setDeletedByAdminId(actor);
                postCommentRepository.save(comment);
                Post post = postRepository.findById(postId).orElse(null);
                if (post != null) {
                    String event = comment.getParentCommentId() == null ? "COMMENT_DELETED" : "REPLY_DELETED";
                    aiUserOutboxWriter.commentLifecycleChanged(post, comment, event, "ADMIN_THREAD_EDIT");
                }
            }
        }
    }

    private static List<Map<String, Object>> toPendingPayload(List<PendingItemRequest> items) {
        List<Map<String, Object>> out = new ArrayList<>(items.size());
        for (PendingItemRequest item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("planItemId", item.getPlanItemId());
            if (Boolean.TRUE.equals(item.getCancel())) {
                row.put("cancel", true);
            } else {
                if (item.getBody() != null) row.put("body", item.getBody());
                if (item.getPersonaId() != null) row.put("personaId", item.getPersonaId());
                if (item.getScheduledAt() != null) row.put("scheduledAt", item.getScheduledAt());
            }
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> toThreadView(Post post, List<PostComment> comments,
                                             List<Map<String, Object>> pending) {
        Set<String> authorIds = comments.stream()
                .map(PostComment::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (post.getAuthorId() != null) authorIds.add(post.getAuthorId());
        for (Map<String, Object> p : pending) {
            Object persona = p.get("personaId");
            if (persona != null) authorIds.add(String.valueOf(persona));
        }
        Set<String> syntheticIds = authorIds.isEmpty()
                ? Set.of()
                : userRepository.findSyntheticIds(authorIds);

        List<Map<String, Object>> items = new ArrayList<>();
        for (PostComment c : comments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("planItemId", null);
            item.put("pending", false);
            item.put("parentCommentId", c.getParentCommentId());
            item.put("parentPlanItemId", null);
            item.put("authorId", c.getAuthorId());
            item.put("body", c.getBody());
            item.put("type", c.getParentCommentId() == null ? "COMMENT" : "REPLY");
            item.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
            item.put("scheduledAt", null);
            item.put("status", c.getStatus() != null ? c.getStatus().name() : null);
            item.put("synthetic", syntheticIds.contains(c.getAuthorId()));
            item.put("likeCount", c.getLikeCount());
            items.add(item);
        }
        for (Map<String, Object> p : pending) {
            Map<String, Object> item = new LinkedHashMap<>();
            String personaId = p.get("personaId") != null ? String.valueOf(p.get("personaId")) : null;
            item.put("id", null);
            item.put("planItemId", p.get("planItemId"));
            item.put("pending", true);
            item.put("parentCommentId", null);
            item.put("parentPlanItemId", p.get("parentPlanItemId"));
            item.put("authorId", personaId);
            item.put("body", p.get("body"));
            item.put("type", p.get("type"));
            item.put("createdAt", null);
            item.put("scheduledAt", p.get("scheduledAt"));
            item.put("status", p.get("status"));
            item.put("synthetic", personaId != null && syntheticIds.contains(personaId));
            item.put("likeCount", 0);
            items.add(item);
        }

        items.sort(Comparator.comparing(AdminPublishedThreadService::itemSortKey,
                Comparator.nullsLast(Comparator.naturalOrder())));

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", post.getId());
        view.put("title", post.getTitle());
        view.put("body", post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw());
        view.put("category", post.getCategory() != null ? post.getCategory().name() : null);
        view.put("status", post.getStatus() != null ? post.getStatus().name() : null);
        view.put("createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : null);
        view.put("viewCount", post.getViewCount());
        view.put("authorId", post.getAuthorId());
        view.put("synthetic", syntheticIds.contains(post.getAuthorId()));
        view.put("commentCount", comments.size());
        view.put("pendingCount", pending.size());
        view.put("items", items);
        return view;
    }

    private static Instant itemSortKey(Map<String, Object> item) {
        Object at = item.get("createdAt");
        if (at == null) at = item.get("scheduledAt");
        if (at == null) return null;
        try {
            return Instant.parse(String.valueOf(at));
        } catch (Exception e) {
            return null;
        }
    }

    private Post requirePost(String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
        if (post.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND");
        }
        return post;
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Long> commentCountsFor(List<String> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        Map<String, Long> out = new HashMap<>();
        for (Object[] row : postCommentRepository.countUndeletedByPostIds(postIds)) {
            out.put(String.valueOf(row[0]), (Long) row[1]);
        }
        return out;
    }

    @Getter
    @Setter
    public static class UpdateThreadRequest {
        private String title;
        private String body;
        private String category;
        private String status;
        private Integer viewCount;
        private String createdAt;
        private List<ThreadItemRequest> items;
        private List<PendingItemRequest> pendingItems;
    }

    @Getter
    @Setter
    public static class ThreadItemRequest {
        private Long id;
        private String body;
        private String authorId;
        private String createdAt;
    }

    @Getter
    @Setter
    public static class PendingItemRequest {
        private String planItemId;
        private String body;
        private String personaId;
        private String scheduledAt;
        private Boolean cancel;
    }
}
