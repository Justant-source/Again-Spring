package com.againspring.service.ai;

import com.againspring.domain.User;
import com.againspring.domain.ai.AiUserOutboxEvent;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.repository.UserRepository;
import com.againspring.repository.ai.AiUserOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Post/Comment 쓰기 트랜잭션에 결합된 outbox 기록기.
 * 이 서비스는 외부 전송이나 LLM 호출을 절대 수행하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUserOutboxWriter {

    private final AiUserOutboxEventRepository outboxRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public void postPublished(Post post) {
        write("POST", post.getId(), "POST_PUBLISHED",
                "post:" + post.getId() + ":revision:" + post.getContentRevision() + ":published",
                postPayload(post));
    }

    public void postRevised(Post post, String reason) {
        Map<String, Object> payload = postPayload(post);
        payload.put("reason", reason);
        write("POST", post.getId(), "POST_UPDATED",
                "post:" + post.getId() + ":revision:" + post.getContentRevision() + ":updated",
                payload);
    }

    public void postLifecycleChanged(Post post, String eventType, String reason) {
        Map<String, Object> payload = postPayload(post);
        payload.put("reason", reason);
        write("POST", post.getId(), eventType,
                "post:" + post.getId() + ":lifecycle:" + eventType + ":" + post.getUpdatedAt(),
                payload);
    }

    public void commentCreated(Post post, PostComment comment) {
        Map<String, Object> payload = commentPayload(post, comment);
        write("COMMENT", String.valueOf(comment.getId()),
                comment.getParentCommentId() == null ? "COMMENT_CREATED" : "REPLY_CREATED",
                "comment:" + comment.getId() + ":revision:" + comment.getContentRevision() + ":created",
                payload);
    }

    public void commentUpdated(Post post, PostComment comment) {
        Map<String, Object> payload = commentPayload(post, comment);
        write("COMMENT", String.valueOf(comment.getId()),
                comment.getParentCommentId() == null ? "COMMENT_UPDATED" : "REPLY_UPDATED",
                "comment:" + comment.getId() + ":revision:" + comment.getContentRevision() + ":updated",
                payload);
    }

    public void commentLifecycleChanged(Post post, PostComment comment, String eventType, String reason) {
        Map<String, Object> payload = commentPayload(post, comment);
        payload.put("reason", reason);
        write("COMMENT", String.valueOf(comment.getId()), eventType,
                "comment:" + comment.getId() + ":lifecycle:" + eventType + ":" + comment.getUpdatedAt(),
                payload);
    }

    private Map<String, Object> commentPayload(Post post, PostComment comment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("postId", post.getId());
        payload.put("postRevision", post.getContentRevision());
        payload.put("commentId", comment.getId());
        payload.put("commentRevision", comment.getContentRevision());
        payload.put("parentCommentId", comment.getParentCommentId());
        payload.put("authorId", comment.getAuthorId());
        payload.put("createdAt", comment.getCreatedAt());
        payload.put("status", comment.getStatus() == null ? null : comment.getStatus().name());
        payload.put("deletedAt", comment.getDeletedAt());
        return payload;
    }

    private Map<String, Object> postPayload(Post post) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("postId", post.getId());
        payload.put("postRevision", post.getContentRevision());
        payload.put("authorId", post.getAuthorId());
        payload.put("visibility", post.getVisibility() == null ? null : post.getVisibility().name());
        payload.put("status", post.getStatus() == null ? null : post.getStatus().name());
        payload.put("partnerAnswered", post.getPartnerAnsweredAt() != null);
        // Orchestrator classifies AI_POST vs HUMAN_POST from this flag. Missing → false → HUMAN_POST.
        payload.put("syntheticPost", isSyntheticAuthor(post.getAuthorId()));
        payload.put("occurredAt", Instant.now());
        return payload;
    }

    private boolean isSyntheticAuthor(String authorId) {
        if (authorId == null || authorId.isBlank()) return false;
        return userRepository.findById(authorId).map(User::isSynthetic).orElse(false);
    }

    private void write(String aggregateType, String aggregateId, String eventType,
                       String idempotencyKey, Map<String, Object> payload) {
        // 동일 요청 재전송/재시도에서 추가 계획 생성이 일어나지 않게 한다.
        if (outboxRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }
        Instant now = Instant.now();
        try {
            outboxRepository.save(AiUserOutboxEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .idempotencyKey(idempotencyKey)
                    .payload(objectMapper.writeValueAsString(payload))
                    .occurredAt(now)
                    .availableAt(now)
                    .build());
        } catch (JsonProcessingException e) {
            // payload는 내부 scalar 값만 사용한다. 실패 시 원 트랜잭션을 롤백해 이벤트 유실을 막는다.
            throw new IllegalStateException("Failed to serialize AI-user outbox event", e);
        }
    }
}
