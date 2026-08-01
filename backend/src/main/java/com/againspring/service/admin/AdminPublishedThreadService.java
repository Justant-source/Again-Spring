package com.againspring.service.admin;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.enums.CommentStatus;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.enums.PostStatus;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.ai.AiCorrectionService;
import com.againspring.service.ai.AiUserOutboxWriter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
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
 * (post fields + comment/reply timeline with editable timestamps).
 */
@Service
@RequiredArgsConstructor
public class AdminPublishedThreadService {

    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserRepository userRepository;
    private final AiCorrectionService aiCorrectionService;
    private final AiUserOutboxWriter aiUserOutboxWriter;

    @Transactional(readOnly = true)
    public Map<String, Object> getThread(String postId) {
        Post post = requirePost(postId);
        List<PostComment> comments = postCommentRepository
                .findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId);
        return toThreadView(post, comments);
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
            post.setStatus(PostStatus.valueOf(req.getStatus()));
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

        List<PostComment> comments = postCommentRepository
                .findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId);
        return toThreadView(postRepository.findById(postId).orElse(updated), comments);
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

    private Map<String, Object> toThreadView(Post post, List<PostComment> comments) {
        Set<String> authorIds = comments.stream()
                .map(PostComment::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (post.getAuthorId() != null) authorIds.add(post.getAuthorId());
        Set<String> syntheticIds = authorIds.isEmpty()
                ? Set.of()
                : userRepository.findSyntheticIds(authorIds);

        List<Map<String, Object>> items = new ArrayList<>();
        for (PostComment c : comments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("parentCommentId", c.getParentCommentId());
            item.put("authorId", c.getAuthorId());
            item.put("body", c.getBody());
            item.put("type", c.getParentCommentId() == null ? "COMMENT" : "REPLY");
            item.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
            item.put("status", c.getStatus() != null ? c.getStatus().name() : null);
            item.put("synthetic", syntheticIds.contains(c.getAuthorId()));
            item.put("likeCount", c.getLikeCount());
            items.add(item);
        }

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
        view.put("items", items);
        return view;
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
    }

    @Getter
    @Setter
    public static class ThreadItemRequest {
        private Long id;
        private String body;
        private String authorId;
        private String createdAt;
    }
}
