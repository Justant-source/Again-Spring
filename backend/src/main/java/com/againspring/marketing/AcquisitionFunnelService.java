package com.againspring.marketing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 유입 퍼널 — 노출 다음 단계부터 가입까지를 한 화면에서 본다.
 *
 * <p>배경(2026-08-29): 어드민 마케팅 탭은 "발행 성공"과 플랫폼 지표(뷰·도달)까지만
 * 보여줬다. 그래서 한 달 동안 "8만 뷰가 방문 0"이라는 사실을 어떤 화면도 말해주지
 * 않았고, 발행 건수가 성과처럼 읽혔다. 이 서비스는 그 다음 칸들을 채운다.
 *
 * <p>집계 원칙:
 * <ul>
 *   <li>봇은 항상 제외한다({@code is_bot = 0}). 링크 검사 크롤러를 유입으로 세면
 *       0을 성과로 오독한다 — 실제로 t.co 유입 114건이 전부 봇이었다.</li>
 *   <li>가입은 {@code users.acquisition_source} 기준. 채널을 모르는 가입은
 *       {@code (unknown)}으로 따로 센다 — 합계를 부풀리지 않기 위해서다.</li>
 *   <li>AI 페르소나({@code synthetic})와 e2e 계정은 사람 수에서 뺀다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcquisitionFunnelService {

    private final JdbcTemplate jdbcTemplate;

    public record ChannelRow(
        String source,
        long visits,
        long visitors,
        long sessions,
        long signups
    ) {}

    public record DailyRow(
        String date,
        long visits,
        long visitors,
        long signups
    ) {}

    public record BotSplit(long human, long bot) {}

    public record FunnelDto(
        int days,
        long totalVisits,
        long totalVisitors,
        long totalSignups,
        BotSplit botSplit,
        List<ChannelRow> byChannel,
        List<DailyRow> daily,
        List<Map<String, Object>> topReferrers,
        List<Map<String, Object>> topPaths
    ) {}

    public FunnelDto funnel(int days) {
        int window = Math.max(1, Math.min(days, 180));

        long totalVisits = count(
            "SELECT COUNT(*) FROM visit_events WHERE is_bot = 0 AND occurred_at >= NOW() - INTERVAL ? DAY",
            window);
        long totalVisitors = count(
            "SELECT COUNT(DISTINCT visitor_key) FROM visit_events "
                + "WHERE is_bot = 0 AND visitor_key IS NOT NULL AND occurred_at >= NOW() - INTERVAL ? DAY",
            window);
        long humans = totalVisits;
        long bots = count(
            "SELECT COUNT(*) FROM visit_events WHERE is_bot = 1 AND occurred_at >= NOW() - INTERVAL ? DAY",
            window);

        long totalSignups = count(
            "SELECT COUNT(*) FROM users WHERE synthetic = 0 AND is_guest = 0 AND deleted_at IS NULL "
                + "AND created_at >= NOW() - INTERVAL ? DAY",
            window);

        List<ChannelRow> byChannel = channelRows(window);
        List<DailyRow> daily = dailyRows(window);

        List<Map<String, Object>> referrers = jdbcTemplate.queryForList("""
            SELECT COALESCE(NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(referrer, '/', 3), '//', -1), ''), '(direct)') AS host,
                   COUNT(*) AS visits
            FROM visit_events
            WHERE is_bot = 0 AND occurred_at >= NOW() - INTERVAL ? DAY
            GROUP BY host ORDER BY visits DESC LIMIT 15
            """, window);

        List<Map<String, Object>> paths = jdbcTemplate.queryForList("""
            SELECT path, COUNT(*) AS visits, COUNT(DISTINCT visitor_key) AS visitors
            FROM visit_events
            WHERE is_bot = 0 AND occurred_at >= NOW() - INTERVAL ? DAY
            GROUP BY path ORDER BY visits DESC LIMIT 15
            """, window);

        return new FunnelDto(
            window, totalVisits, totalVisitors, totalSignups,
            new BotSplit(humans, bots), byChannel, daily, referrers, paths);
    }

    private List<ChannelRow> channelRows(int window) {
        // 방문 쪽과 가입 쪽은 서로 다른 테이블이라 채널 키로 합친다.
        List<Map<String, Object>> visitRows = jdbcTemplate.queryForList("""
            SELECT COALESCE(utm_source, '(direct/organic)') AS source,
                   COUNT(*) AS visits,
                   COUNT(DISTINCT visitor_key) AS visitors,
                   COUNT(DISTINCT session_key) AS sessions
            FROM visit_events
            WHERE is_bot = 0 AND occurred_at >= NOW() - INTERVAL ? DAY
            GROUP BY source
            """, window);

        List<Map<String, Object>> signupRows = jdbcTemplate.queryForList("""
            SELECT COALESCE(acquisition_source, '(unknown)') AS source, COUNT(*) AS signups
            FROM users
            WHERE synthetic = 0 AND is_guest = 0 AND deleted_at IS NULL
              AND created_at >= NOW() - INTERVAL ? DAY
            GROUP BY source
            """, window);

        java.util.Map<String, Long> signupBySource = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : signupRows) {
            signupBySource.put(String.valueOf(row.get("source")), num(row.get("signups")));
        }

        List<ChannelRow> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Map<String, Object> row : visitRows) {
            String source = String.valueOf(row.get("source"));
            seen.add(source);
            out.add(new ChannelRow(
                source,
                num(row.get("visits")),
                num(row.get("visitors")),
                num(row.get("sessions")),
                signupBySource.getOrDefault(source, 0L)));
        }
        // 방문 기록 없이 가입만 있는 채널(쿠키만 남고 방문 기록이 유실된 경우)도 보여준다.
        signupBySource.forEach((source, signups) -> {
            if (!seen.contains(source)) {
                out.add(new ChannelRow(source, 0, 0, 0, signups));
            }
        });
        out.sort((a, b) -> Long.compare(b.visits(), a.visits()));
        return out;
    }

    private List<DailyRow> dailyRows(int window) {
        List<Map<String, Object>> visits = jdbcTemplate.queryForList("""
            SELECT DATE(occurred_at) AS d, COUNT(*) AS visits, COUNT(DISTINCT visitor_key) AS visitors
            FROM visit_events
            WHERE is_bot = 0 AND occurred_at >= NOW() - INTERVAL ? DAY
            GROUP BY d ORDER BY d
            """, window);
        List<Map<String, Object>> signups = jdbcTemplate.queryForList("""
            SELECT DATE(created_at) AS d, COUNT(*) AS signups
            FROM users
            WHERE synthetic = 0 AND is_guest = 0 AND deleted_at IS NULL
              AND created_at >= NOW() - INTERVAL ? DAY
            GROUP BY d ORDER BY d
            """, window);

        java.util.Map<String, Long> signupByDay = new java.util.HashMap<>();
        for (Map<String, Object> row : signups) {
            signupByDay.put(String.valueOf(row.get("d")), num(row.get("signups")));
        }

        List<DailyRow> out = new ArrayList<>();
        for (Map<String, Object> row : visits) {
            String d = String.valueOf(row.get("d"));
            out.add(new DailyRow(d, num(row.get("visits")), num(row.get("visitors")),
                signupByDay.getOrDefault(d, 0L)));
        }
        return out;
    }

    private long count(String sql, Object... args) {
        Long v = jdbcTemplate.queryForObject(sql, Long.class, args);
        return v != null ? v : 0L;
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }
}
