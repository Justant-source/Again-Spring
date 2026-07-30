package com.againspring.aiuser.orchestrator.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 조회수 보정 — 참여(댓글·좋아요·투표)에 비례해 posts.view_count를 직접 갱신.
 *
 * <p>기존 봇 VIEW 디스패치는 ViewService의 device_id 중복 방지 때문에 페르소나당 글당 1회만
 * 카운트되어 조회수가 과소 집계됐다(글당 ~페르소나 수 cap, 게다가 무작위 분산 → 글마다 0~수회).
 * 현실 커뮤니티는 참여 1건당 수십 명이 조회하므로 denormalized view_count를 참여에 비례해 직접 보정한다.
 *
 * <p>공식: view_count = max(현재, BASE + (8·댓글 + 6·투표) × 글별변동계수)
 * - 좋아요를 입력으로 쓰지 않는다. 조회수→좋아요 순서를 보장해 순환 증폭을 막는다.
 * - 글별변동계수 0.85~1.44 = CRC32(id) 기반 — 글마다 고정이되 서로 다른 자연스러운 분포.
 * - GREATEST로 단조 증가 → 실유저 조회 보존. 매 틱 실행, 참여 증가 시 조회수도 비례 상승.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewDispatcher {

    private final JdbcTemplate jdbcTemplate;

    private static final int VIEW_PER_COMMENT = 8;
    private static final int VIEW_PER_VOTE = 6;
    private static final int BASE_VIEWS = 12;   // 참여 0이어도 최소 노출 (균형 보정: ~12-15:1)

    public int dispatchViews() {
        try {
            int updated = jdbcTemplate.update(
                "UPDATE posts p SET p.view_count = GREATEST(p.view_count, " +
                "  " + BASE_VIEWS + " + ROUND( ( " +
                "      " + VIEW_PER_COMMENT + " * (SELECT COUNT(*) FROM post_comments c WHERE c.post_id=p.id AND c.deleted_at IS NULL) " +
                "    + " + VIEW_PER_VOTE + " * (SELECT COUNT(*) FROM votes v WHERE v.post_id=p.id) " +
                "  ) * (0.85 + (CRC32(p.id) % 60)/100.0) ) ) " +
                "WHERE p.deleted_at IS NULL AND p.status = 'VOTING'"
            );
            if (updated > 0) {
                log.info("ViewDispatcher: recomputed proportional view_count for {} posts", updated);
            }
            return updated;
        } catch (Exception e) {
            log.warn("ViewDispatcher proportional recompute failed: {}", e.getMessage());
            return 0;
        }
    }
}
