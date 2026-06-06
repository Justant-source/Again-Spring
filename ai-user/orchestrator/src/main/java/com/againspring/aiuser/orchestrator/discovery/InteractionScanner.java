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

    /**
     * Find comments on synthetic posts that bots haven't replied to yet.
     * Returns ReplyTarget list for ActionPlanner's reply loop.
     */
    public List<ReplyTarget> findReplyTargets() {
        try {
            // Find recent posts authored by synthetic (ai-user) accounts
            // We look for post_ids from persona_action_log (POST actions in last 48h)
            Instant since = Instant.now().minusSeconds(48 * 3600);

            List<java.util.Map<String, java.lang.Object>> rows = jdbcTemplate.queryForList(
                "SELECT pal.target_id AS post_id, " +
                "       pc.id AS comment_id, " +
                "       LEFT(pc.body, 200) AS comment_excerpt, " +
                "       LEFT(p.user_title, 100) AS post_title, " +
                "       LEFT(p.body_published, 300) AS post_body_excerpt " +
                "FROM persona_action_log pal " +
                "JOIN posts p ON p.id = pal.target_id " +
                "JOIN post_comments pc ON pc.post_id = pal.target_id " +
                "WHERE pal.action_type = 'POST' " +
                "  AND pal.created_at > ? " +
                "  AND pc.parent_comment_id IS NULL " +
                "  AND pc.author_id IN (SELECT id FROM users WHERE " + com.againspring.aiuser.orchestrator.seed.AiUserIdentity.REAL_USER_AUTHOR_CONDITION + ") " +
                "ORDER BY pc.id DESC " +
                "LIMIT ?",
                since, MAX_RESULTS
            );

            List<ReplyTarget> targets = new java.util.ArrayList<>();
            for (java.util.Map<String, java.lang.Object> row : rows) {
                String postId = (String) row.get("post_id");
                java.lang.Object commentIdObj = row.get("comment_id");
                Long commentId = commentIdObj instanceof java.lang.Number ? ((java.lang.Number) commentIdObj).longValue() : null;
                String excerpt = (String) row.get("comment_excerpt");
                String postTitle = (String) row.get("post_title");
                if (postId != null && commentId != null) {
                    String postBodyExcerpt = (String) row.get("post_body_excerpt");
                    // Fetch sibling comments (other top-level comments on same post)
                    String siblingComments = fetchSiblingComments(postId, commentId);
                    targets.add(new ReplyTarget(postId, postTitle, commentId, excerpt,
                        "다시봄 커뮤니티 갈등 글", postBodyExcerpt, siblingComments));
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
                "WHERE post_id = ? AND parent_comment_id IS NULL AND id != ? " +
                "  AND author_id IN (SELECT id FROM users WHERE " + com.againspring.aiuser.orchestrator.seed.AiUserIdentity.REAL_USER_AUTHOR_CONDITION + ") " +
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
