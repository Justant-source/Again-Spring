package com.againspring.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 참여(댓글·투표) 비례 조회수 보정. orchestrator ViewDispatcher의 posts.view_count 직접 UPDATE를 대체한다.
 * post_views 행을 함께 넣어 count(post_views) == view_count 불변식을 지킨다 (ViewService와 동일 계약).
 * 공식은 ViewDispatcher 원본 그대로: 12 + round((8·댓글 + 6·투표) × (0.85 + CRC32(id)%60/100)).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyntheticViewReconcileService {

    public record Candidate(String postId, int current, int target) {}
    public record Result(int updated, int viewsInserted) {}

    private static final int MAX_INSERT_PER_POST = 500;

    private final JdbcTemplate jdbc;

    @Transactional
    public Result reconcile() {
        List<Candidate> rows = jdbc.query(
            "SELECT p.id, p.view_count, " +
            "  12 + ROUND((8 * (SELECT COUNT(*) FROM post_comments c WHERE c.post_id = p.id AND c.deleted_at IS NULL)" +
            "          + 6 * (SELECT COUNT(*) FROM votes v WHERE v.post_id = p.id)) * (0.85 + (CRC32(p.id) % 60) / 100.0)) AS target " +
            "FROM posts p WHERE p.deleted_at IS NULL AND p.status = 'VOTING'",
            (rs, i) -> new Candidate(rs.getString(1), rs.getInt(2), rs.getInt(3)));
        int updated = 0, inserted = 0;
        for (Candidate c : rows) {
            int deficit = Math.min(c.target() - c.current(), MAX_INSERT_PER_POST);
            if (deficit <= 0) continue;
            List<Object[]> batch = new ArrayList<>(deficit);
            long seq = System.nanoTime();
            for (int n = 0; n < deficit; n++) {
                batch.add(new Object[] { c.postId(), "synthetic-" + c.postId() + "-" + (seq + n) });
            }
            int[] res = jdbc.batchUpdate("INSERT INTO post_views (post_id, device_id, viewed_at) VALUES (?, ?, NOW(3))", batch);
            inserted += res.length;
            updated += jdbc.update("UPDATE posts SET view_count = ? WHERE id = ?", c.current() + deficit, c.postId());
        }
        log.info("[SyntheticViewReconcile] posts={} viewsInserted={}", updated, inserted);
        return new Result(updated, inserted);
    }
}
