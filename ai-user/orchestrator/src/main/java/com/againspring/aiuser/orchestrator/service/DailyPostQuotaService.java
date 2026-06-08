package com.againspring.aiuser.orchestrator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 일일 글 쿼터 서비스.
 * posts 테이블(단일 진실원)을 직접 조회 — 별도 카운터 테이블 없음.
 * synthetic=1 봇 유저만 카운트(보안 불변식).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPostQuotaService {

    private final JdbcTemplate jdbcTemplate;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 오늘(KST) AI 유저가 작성한 비삭제 게시글 수.
     */
    public int postsCreatedToday() {
        try {
            ZonedDateTime todayStart = LocalDate.now(KST).atStartOfDay(KST);
            Timestamp since = Timestamp.from(todayStart.toInstant());
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts p" +
                " JOIN users u ON p.author_id = u.id" +
                " WHERE u.synthetic = 1 AND p.deleted_at IS NULL AND p.created_at >= ?",
                Integer.class, since);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("postsCreatedToday query failed: {}", e.getMessage());
            return 0;
        }
    }

    /** 오늘 남은 글 쿼터. targetPosts <= 0이면 0 반환(글 생성 금지). */
    public int remaining(int targetPosts) {
        if (targetPosts <= 0) return 0;
        return Math.max(0, targetPosts - postsCreatedToday());
    }

    /** 오늘 쿼터에 도달했으면 true. */
    public boolean hasReachedQuota(int targetPosts) {
        if (targetPosts <= 0) return true;
        return postsCreatedToday() >= targetPosts;
    }
}
