package com.againspring.aiuser.orchestrator.discovery;

import com.againspring.aiuser.orchestrator.engine.ReplyTarget;
import com.againspring.aiuser.orchestrator.repository.PersonaActionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * 알림 시스템 부재 우회: synthetic 글에 달린 실유저 댓글을 직접 DB 조회.
 *
 * 댓글 생성 시 알림이 발행되지 않는 구조(NotificationEventListener에 NewCommentEvent 없음)이므로,
 * orchestrator가 자체 datasource로 post_comments를 직접 스캔.
 *
 * 스캔 범위: 최근 48시간 이내 synthetic 유저가 작성한 글에 달린 댓글.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionScanner {

    private final JdbcTemplate jdbcTemplate;
    private static final int MAX_RESULTS = 20;
    // 댓글 1개당 최대 대댓글 수 — pile-on 방지(자연스러운 스레드 유지)
    private static final int MAX_REPLIES_PER_COMMENT = 2;

    /**
     * Find comments on synthetic posts that bots haven't replied to yet.
     * Returns ReplyTarget list for ActionPlanner's reply loop.
     */
    public List<ReplyTarget> findReplyTargets() {
        try {
            // 최근 48h 내 작성된 top-level 댓글 중 대댓글이 적은(< cap) 것을 직접 스캔.
            // persona_action_log POST 의존 제거(로그 누락 시 0건 버그) → posts/post_comments 직접 조회.
            // 실유저 댓글 우선(synthetic ASC; NULL=실유저). AI 댓글도 포함 → AI끼리 자연 스레드 형성.
            // 삭제 글·댓글 제외, PUBLIC 글만.
            Instant since = Instant.now().minusSeconds(48 * 3600);

            List<java.util.Map<String, java.lang.Object>> rows = jdbcTemplate.queryForList(
                "SELECT pc.post_id AS post_id, " +
                "       pc.id AS comment_id, pc.author_id AS comment_author_id, " +
                "       LEFT(pc.body, 200) AS comment_excerpt, " +
                "       LEFT(p.user_title, 100) AS post_title, " +
                "       LEFT(p.body_published, 300) AS post_body_excerpt " +
                "FROM post_comments pc " +
                "JOIN posts p ON p.id = pc.post_id AND p.deleted_at IS NULL AND p.visibility = 'PUBLIC' " +
                "JOIN users u ON u.id = pc.author_id " +
                "WHERE pc.parent_comment_id IS NULL AND pc.deleted_at IS NULL " +
                "  AND pc.created_at > ? " +
                "  AND (SELECT COUNT(*) FROM post_comments r WHERE r.parent_comment_id = pc.id AND r.deleted_at IS NULL) < ? " +
                "ORDER BY u.synthetic ASC, pc.id DESC " +
                "LIMIT ?",
                since, MAX_REPLIES_PER_COMMENT, MAX_RESULTS * 2
            );

            List<ReplyTarget> targets = new java.util.ArrayList<>();
            java.util.Set<Long> seenComments = new java.util.HashSet<>();
            for (java.util.Map<String, java.lang.Object> row : rows) {
                if (targets.size() >= MAX_RESULTS) break;
                String postId = (String) row.get("post_id");
                java.lang.Object commentIdObj = row.get("comment_id");
                Long commentId = commentIdObj instanceof java.lang.Number ? ((java.lang.Number) commentIdObj).longValue() : null;
                String excerpt = (String) row.get("comment_excerpt");
                String postTitle = (String) row.get("post_title");
                String commentAuthorId = (String) row.get("comment_author_id");
                if (postId != null && commentId != null && seenComments.add(commentId)) {
                    String postBodyExcerpt = (String) row.get("post_body_excerpt");
                    // Fetch sibling comments (other top-level comments on same post)
                    String siblingComments = fetchSiblingComments(postId, commentId);
                    targets.add(new ReplyTarget(postId, postTitle, commentId, excerpt,
                        "다시봄 커뮤니티 갈등 글", postBodyExcerpt, siblingComments, commentAuthorId));
                }
            }
            log.debug("InteractionScanner: found {} reply targets", targets.size());
            return targets;
        } catch (Exception e) {
            log.warn("InteractionScanner query failed: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private String fetchSiblingComments(String postId, Long excludeCommentId) {
        try {
            List<Map<String, Object>> siblings = jdbcTemplate.queryForList(
                "SELECT LEFT(body, 100) AS body " +
                "FROM post_comments " +
                "WHERE post_id = ? AND parent_comment_id IS NULL AND id != ? AND deleted_at IS NULL " +
                "ORDER BY id DESC LIMIT 3",
                postId, excludeCommentId);
            if (siblings.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> s : siblings) {
                String body = (String) s.get("body");
                if (body != null && !body.isBlank()) sb.append("- ").append(body).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }
}
