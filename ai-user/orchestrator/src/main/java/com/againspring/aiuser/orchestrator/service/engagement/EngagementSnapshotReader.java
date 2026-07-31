package com.againspring.aiuser.orchestrator.service.engagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Read-only data-access layer for engagement reconciliation.
 * Queries post/comment/like state to determine which posts need more views/likes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngagementSnapshotReader {

    private final JdbcTemplate jdbc;

    /**
     * Returns post IDs that are PLAN-mode-authored and recent enough to matter.
     * Joins posts to ai_thread_plans, filtering by status and age.
     */
    public List<String> planModePostIds(int lookbackDays, int limit) {
        String sql = "SELECT DISTINCT tp.post_id FROM posts p " +
                "INNER JOIN ai_thread_plans tp ON tp.post_id = p.id " +
                "WHERE p.deleted_at IS NULL " +
                "  AND p.status = 'VOTING' " +
                "  AND p.created_at >= UTC_TIMESTAMP() - INTERVAL ? DAY " +
                "ORDER BY p.created_at DESC " +
                "LIMIT ?";
        try {
            return jdbc.queryForList(sql, String.class, lookbackDays, limit);
        } catch (Exception e) {
            log.warn("planModePostIds query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Snapshot of a single post and all its live comments/replies.
     * Queries the post row for basic metadata, then all ACTIVE comments.
     * Computes childReplyCount for each row in Java.
     * Returns null if post doesn't exist or is deleted.
     */
    public PostSnapshot snapshot(String postId) {
        try {
            // Query the post itself
            String postSql = "SELECT author_id, view_count FROM posts WHERE id = ? AND deleted_at IS NULL";
            List<Map<String, Object>> postRows = jdbc.queryForList(postSql, postId);
            if (postRows.isEmpty()) {
                return null;
            }
            Map<String, Object> postRow = postRows.get(0);
            String authorId = (String) postRow.get("author_id");
            Number viewCount = (Number) postRow.get("view_count");
            long viewCountValue = viewCount != null ? viewCount.longValue() : 0L;

            // Query all comments and replies
            String commentsSql = "SELECT id, author_id, parent_comment_id, like_count FROM post_comments " +
                    "WHERE post_id = ? AND deleted_at IS NULL AND status = 'ACTIVE' " +
                    "ORDER BY id";
            List<Map<String, Object>> commentRows = jdbc.queryForList(commentsSql, postId);

            // Convert to CommentRow and compute childReplyCount
            List<CommentRow> comments = new ArrayList<>();
            for (Map<String, Object> row : commentRows) {
                long id = ((Number) row.get("id")).longValue();
                String commentAuthorId = (String) row.get("author_id");
                Long parentCommentId = row.get("parent_comment_id") != null
                        ? ((Number) row.get("parent_comment_id")).longValue()
                        : null;
                int likeCount = ((Number) row.get("like_count")).intValue();

                // Count how many other comments have parent_comment_id == this.id
                int childReplyCount = 0;
                for (Map<String, Object> otherRow : commentRows) {
                    Long otherParentId = otherRow.get("parent_comment_id") != null
                            ? ((Number) otherRow.get("parent_comment_id")).longValue()
                            : null;
                    if (otherParentId != null && otherParentId.equals(id)) {
                        childReplyCount++;
                    }
                }

                comments.add(new CommentRow(id, commentAuthorId, parentCommentId, likeCount, childReplyCount));
            }

            return new PostSnapshot(postId, authorId, viewCountValue, comments);
        } catch (Exception e) {
            log.warn("snapshot query failed for postId={}: {}", postId, e.getMessage());
            return null;
        }
    }

    /**
     * Count of post-level likes (post_likes where post_id = ? and comment_id IS NULL).
     */
    public long currentPostLikeCount(String postId) {
        try {
            String sql = "SELECT COUNT(*) FROM post_likes WHERE post_id = ? AND comment_id IS NULL";
            Number result = jdbc.queryForObject(sql, Number.class, postId);
            return result != null ? result.longValue() : 0L;
        } catch (Exception e) {
            log.warn("currentPostLikeCount query failed for postId={}: {}", postId, e.getMessage());
            return 0L;
        }
    }

    /**
     * Set of user IDs that have already liked a specific comment.
     * Used to avoid re-liking or double-toggle issues.
     */
    public Set<String> alreadyLikedCommentAuthorIds(long commentId) {
        try {
            String sql = "SELECT user_id FROM post_likes WHERE comment_id = ?";
            List<String> userIds = jdbc.queryForList(sql, String.class, commentId);
            return new HashSet<>(userIds);
        } catch (Exception e) {
            log.warn("alreadyLikedCommentAuthorIds query failed for commentId={}: {}", commentId, e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Set of user IDs that have already liked a specific post.
     * Used to avoid re-liking or double-toggle issues.
     */
    public Set<String> alreadyLikedPostAuthorIds(String postId) {
        try {
            String sql = "SELECT user_id FROM post_likes WHERE post_id = ? AND comment_id IS NULL";
            List<String> userIds = jdbc.queryForList(sql, String.class, postId);
            return new HashSet<>(userIds);
        } catch (Exception e) {
            log.warn("alreadyLikedPostAuthorIds query failed for postId={}: {}", postId, e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Snapshot of a single post with its metadata and comments/replies.
     */
    public record PostSnapshot(String postId, String authorId, long viewCount, List<CommentRow> comments) { }

    /**
     * A single comment or reply row.
     * childReplyCount = number of direct child replies under this comment.
     */
    public record CommentRow(long id, String authorId, Long parentCommentId, int likeCount, int childReplyCount) { }
}
