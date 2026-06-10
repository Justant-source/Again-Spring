package com.againspring.service.admin;

import com.againspring.repository.DailyStatsRepository;
import com.againspring.repository.FeedbackRepository;
import com.againspring.repository.UserRepository;
import com.againspring.repository.VisitEventRepository;
import com.againspring.repository.community.CommunityReportRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteRepository;
import com.againspring.repository.inquiry.InquiryRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 어드민 대시보드 통계 서비스
 * KPI, 액션 센터, 커뮤니티 맥박, 트래픽 분석 등 통합 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardOpsService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final VoteRepository voteRepository;
    private final CommunityReportRepository communityReportRepository;
    private final InquiryRepository inquiryRepository;
    private final MarketingJobRepository marketingJobRepository;
    private final DailyStatsRepository dailyStatsRepository;
    private final VisitEventRepository visitEventRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * 액션 센터: 대기 중인 신고, 문의, 마케팅 잡 등 즉시 조치 필요한 항목 집계
     */
    public ActionCenterDto getActionCenter() {
        long pendingReports = communityReportRepository.countByStatus("PENDING");
        long openInquiries = inquiryRepository.countByStatus("OPEN");

        // 마케팅 잡: status=READY AND autoPublish=false (수동 검수 필요)
        long marketingAwaitingApproval = countMarketingJobsByStatusAndAutoPublish(
                Arrays.asList("READY"), false);

        // 마케팅 실패: status IN ('FAILED','STALE')
        long marketingFailed = countMarketingJobsByStatus(
                Arrays.asList("FAILED", "STALE"));

        // AI 실패/차단: persona_action_log 오늘 기준
        Instant todayStart = LocalDate.now(KST)
                .atStartOfDay(KST)
                .toInstant();

        Map<String, Long> aiCounts = countPersonaActionLogByStatus(
                Arrays.asList("FAILED", "BLOCKED"), todayStart);
        long aiFailuresToday = aiCounts.getOrDefault("FAILED", 0L);
        long aiBlockedToday = aiCounts.getOrDefault("BLOCKED", 0L);

        // 위기 지표: daily_stats에서 최근 1일 crisisTriggers 합
        long crisisRecent24h = countCrisisTriggersLastDay();

        return ActionCenterDto.builder()
                .pendingReports(pendingReports)
                .openInquiries(openInquiries)
                .marketingAwaitingApproval(marketingAwaitingApproval)
                .marketingFailed(marketingFailed)
                .aiFailuresToday(aiFailuresToday)
                .aiBlockedToday(aiBlockedToday)
                .crisisRecent24h(crisisRecent24h)
                .build();
    }

    /**
     * KPI 메트릭: 일정 기간(days)의 주요 지표와 추이 제공
     */
    public List<KpiMetricDto> getKpiMetrics(int days) {
        int clampedDays = Math.max(1, Math.min(90, days));
        LocalDate today = LocalDate.now(KST);
        LocalDate fromDate = today.minusDays(clampedDays);
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);

        // 오늘 신규 회원
        Instant todayStart = today.atStartOfDay(KST).toInstant();
        Instant todayEnd = today.plusDays(1).atStartOfDay(KST).toInstant();
        long todayNewUsers = userRepository.countByIsGuestFalseAndCreatedAtBetween(todayStart, todayEnd);

        // 어제 신규 회원
        Instant yesterdayStart = yesterday.atStartOfDay(KST).toInstant();
        Instant yesterdayEnd = today.atStartOfDay(KST).toInstant();
        long yesterdayNewUsers = userRepository.countByIsGuestFalseAndCreatedAtBetween(yesterdayStart, yesterdayEnd);

        // 전일모레 신규 회원 (delta 계산용)
        Instant twoDaysStart = twoDaysAgo.atStartOfDay(KST).toInstant();
        Instant twoDaysEnd = yesterday.atStartOfDay(KST).toInstant();
        long twoDaysAgoNewUsers = userRepository.countByIsGuestFalseAndCreatedAtBetween(twoDaysStart, twoDaysEnd);

        // sparkline: 최근 clampedDays일의 new_users from daily_stats
        List<Integer> todayNewUsersSparkline = dailyStatsRepository
                .findByStatDateBetweenOrderByStatDateAsc(fromDate, today)
                .stream()
                .map(ds -> ds.getNewUsers())
                .collect(Collectors.toList());

        // 전체 회원
        long totalUsers = userRepository.countByIsGuestFalseAndDeletedAtIsNull();

        // 최근 7일 신규 합
        LocalDate last7daysStart = today.minusDays(7);
        List<Integer> last7daysNewUsers = dailyStatsRepository
                .findByStatDateBetweenOrderByStatDateAsc(last7daysStart, today)
                .stream()
                .map(ds -> ds.getNewUsers())
                .collect(Collectors.toList());
        long last7daysTotalNew = last7daysNewUsers.stream()
                .mapToLong(Integer::longValue)
                .sum();

        long totalUsersDelta = last7daysTotalNew;
        Double totalUsersPercent = totalUsers > 0 && totalUsersDelta > 0
                ? (double) totalUsersDelta / (totalUsers - totalUsersDelta) * 100
                : null;

        // 전체 게시글
        long totalPosts = postRepository.countByDeletedAtIsNull();

        // sparkline: 최근 clampedDays일의 post_count from daily_stats
        List<Integer> postsSparkline = dailyStatsRepository
                .findByStatDateBetweenOrderByStatDateAsc(fromDate, today)
                .stream()
                .map(ds -> ds.getPostCount())
                .collect(Collectors.toList());

        // 오늘 투표
        long todayVotes = voteRepository.countByCreatedAtBetween(todayStart, todayEnd);
        long yesterdayVotes = voteRepository.countByCreatedAtBetween(yesterdayStart, yesterdayEnd);

        // sparkline: 최근 clampedDays일의 vote_count from daily_stats
        List<Integer> votesSparkline = dailyStatsRepository
                .findByStatDateBetweenOrderByStatDateAsc(fromDate, today)
                .stream()
                .map(ds -> ds.getVoteCount())
                .collect(Collectors.toList());

        // 대기 신고
        long pendingReports = communityReportRepository.countByStatus("PENDING");

        // 미해결 문의
        long openInquiries = inquiryRepository.countByStatus("OPEN");

        List<KpiMetricDto> metrics = new ArrayList<>();

        // 신규 회원 (오늘)
        metrics.add(KpiMetricDto.builder()
                .key("todayNewUsers")
                .label("신규 회원 (오늘)")
                .value(todayNewUsers)
                .delta(Math.toIntExact(todayNewUsers - yesterdayNewUsers))
                .deltaPercent(calculatePercent(todayNewUsers - yesterdayNewUsers, yesterdayNewUsers))
                .sparkline(todayNewUsersSparkline)
                .build());

        // 전체 회원
        metrics.add(KpiMetricDto.builder()
                .key("totalUsers")
                .label("전체 회원")
                .value(totalUsers)
                .delta(Math.toIntExact(totalUsersDelta))
                .deltaPercent(totalUsersPercent)
                .sparkline(last7daysNewUsers)
                .build());

        // 전체 게시글
        metrics.add(KpiMetricDto.builder()
                .key("totalPosts")
                .label("전체 게시글")
                .value(totalPosts)
                .delta(0) // delta는 sparkline 마지막 2개 비교로도 가능하지만, 간단히 0
                .deltaPercent(null)
                .sparkline(postsSparkline)
                .build());

        // 오늘 투표
        metrics.add(KpiMetricDto.builder()
                .key("todayVotes")
                .label("오늘 투표")
                .value(todayVotes)
                .delta(Math.toIntExact(todayVotes - yesterdayVotes))
                .deltaPercent(calculatePercent(todayVotes - yesterdayVotes, yesterdayVotes))
                .sparkline(votesSparkline)
                .build());

        // 대기 신고
        metrics.add(KpiMetricDto.builder()
                .key("pendingReports")
                .label("대기 신고")
                .value(pendingReports)
                .delta(0)
                .deltaPercent(null)
                .sparkline(new ArrayList<>())
                .build());

        // 미해결 문의
        metrics.add(KpiMetricDto.builder()
                .key("openInquiries")
                .label("미해결 문의")
                .value(openInquiries)
                .delta(0)
                .deltaPercent(null)
                .sparkline(new ArrayList<>())
                .build());

        return metrics;
    }

    /**
     * 커뮤니티 맥박: 최근 hours 시간대별 실시간/AI 콘텐츠 생성
     */
    public PulseDto getCommunityPulse(int hours) {
        int clampedHours = Math.max(1, Math.min(168, hours)); // 1~168시간(1주)
        Instant since = Instant.now().minusSeconds(clampedHours * 3600L);

        Set<String> syntheticIds = userRepository.findAllSyntheticIds();

        // 포스트 시간대별 집계
        String postsSql = """
            SELECT HOUR(CONVERT_TZ(p.created_at, '+00:00', '+09:00')) as hr,
                   CASE WHEN u.synthetic=1 THEN 'ai' ELSE 'real' END as utype,
                   COUNT(*) as cnt
            FROM posts p
            JOIN users u ON p.author_id=u.id
            WHERE p.created_at >= ? AND p.deleted_at IS NULL
            GROUP BY hr, utype
            """;

        List<Map<String, Object>> postsResult = jdbcTemplate.queryForList(postsSql, since);

        // 댓글 시간대별 집계
        String commentsSql = """
            SELECT HOUR(CONVERT_TZ(pc.created_at, '+00:00', '+09:00')) as hr,
                   CASE WHEN u.synthetic=1 THEN 'ai' ELSE 'real' END as utype,
                   COUNT(*) as cnt
            FROM post_comments pc
            JOIN users u ON pc.author_id=u.id
            WHERE pc.created_at >= ? AND pc.deleted_at IS NULL
            GROUP BY hr, utype
            """;

        List<Map<String, Object>> commentsResult = jdbcTemplate.queryForList(commentsSql, since);

        // 투표 시간대별 집계 (voter 기준) — votes 테이블에 deleted_at 없음
        String votesSql = """
            SELECT HOUR(CONVERT_TZ(v.created_at, '+00:00', '+09:00')) as hr,
                   CASE WHEN u.synthetic=1 THEN 'ai' ELSE 'real' END as utype,
                   COUNT(*) as cnt
            FROM votes v
            JOIN users u ON v.voter_user_id=u.id
            WHERE v.created_at >= ?
            GROUP BY hr, utype
            """;

        List<Map<String, Object>> votesResult = jdbcTemplate.queryForList(votesSql, since);

        // 0~23 시간대 초기화
        Map<Integer, Integer> postsRealByHour = new HashMap<>();
        Map<Integer, Integer> postsAiByHour = new HashMap<>();
        Map<Integer, Integer> commentsRealByHour = new HashMap<>();
        Map<Integer, Integer> commentsAiByHour = new HashMap<>();
        Map<Integer, Integer> votesRealByHour = new HashMap<>();
        Map<Integer, Integer> votesAiByHour = new HashMap<>();

        for (int h = 0; h < 24; h++) {
            postsRealByHour.put(h, 0);
            postsAiByHour.put(h, 0);
            commentsRealByHour.put(h, 0);
            commentsAiByHour.put(h, 0);
            votesRealByHour.put(h, 0);
            votesAiByHour.put(h, 0);
        }

        // 포스트 집계
        for (Map<String, Object> row : postsResult) {
            int hr = ((Number) row.get("hr")).intValue();
            String utype = (String) row.get("utype");
            int cnt = ((Number) row.get("cnt")).intValue();
            if ("ai".equals(utype)) {
                postsAiByHour.put(hr, postsAiByHour.get(hr) + cnt);
            } else {
                postsRealByHour.put(hr, postsRealByHour.get(hr) + cnt);
            }
        }

        // 댓글 집계
        for (Map<String, Object> row : commentsResult) {
            int hr = ((Number) row.get("hr")).intValue();
            String utype = (String) row.get("utype");
            int cnt = ((Number) row.get("cnt")).intValue();
            if ("ai".equals(utype)) {
                commentsAiByHour.put(hr, commentsAiByHour.get(hr) + cnt);
            } else {
                commentsRealByHour.put(hr, commentsRealByHour.get(hr) + cnt);
            }
        }

        // 투표 집계
        for (Map<String, Object> row : votesResult) {
            int hr = ((Number) row.get("hr")).intValue();
            String utype = (String) row.get("utype");
            int cnt = ((Number) row.get("cnt")).intValue();
            if ("ai".equals(utype)) {
                votesAiByHour.put(hr, votesAiByHour.get(hr) + cnt);
            } else {
                votesRealByHour.put(hr, votesRealByHour.get(hr) + cnt);
            }
        }

        List<PulseDayDataDto> data = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            data.add(PulseDayDataDto.builder()
                    .hour(h)
                    .postsReal(postsRealByHour.get(h))
                    .postsAi(postsAiByHour.get(h))
                    .commentsReal(commentsRealByHour.get(h))
                    .commentsAi(commentsAiByHour.get(h))
                    .votesReal(votesRealByHour.get(h))
                    .votesAi(votesAiByHour.get(h))
                    .build());
        }

        return PulseDto.builder()
                .data(data)
                .build();
    }

    /**
     * 핫 포스트: 최근 hours 시간 내 engagement score 상위 limit개
     * score = vote_count * 3 + comment_count * 2 + view_count * 0.1
     */
    public List<HotPostDto> getHotPosts(int hours, int limit) {
        int clampedHours = Math.max(1, Math.min(168, hours));
        int clampedLimit = Math.max(1, Math.min(100, limit));
        Instant since = Instant.now().minusSeconds(clampedHours * 3600L);

        String sql = """
            SELECT p.id, p.title, p.author_id, u.synthetic,
                   COALESCE(vc.cnt,0) as vote_count,
                   COALESCE(cc.cnt,0) as comment_count,
                   COALESCE(p.view_count,0) as view_count,
                   (COALESCE(vc.cnt,0)*3 + COALESCE(cc.cnt,0)*2 + COALESCE(p.view_count,0)*0.1) as score,
                   p.created_at as created_at
            FROM posts p
            JOIN users u ON p.author_id = u.id
            LEFT JOIN (SELECT post_id, COUNT(*) cnt FROM votes GROUP BY post_id) vc ON vc.post_id=p.id
            LEFT JOIN (SELECT post_id, COUNT(*) cnt FROM post_comments WHERE deleted_at IS NULL GROUP BY post_id) cc ON cc.post_id=p.id
            WHERE p.created_at >= ? AND p.deleted_at IS NULL AND p.status != 'BLOCKED'
            ORDER BY score DESC
            LIMIT ?
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, since, clampedLimit);

        return rows.stream()
                .map(row -> HotPostDto.builder()
                        .id((String) row.get("id"))
                        .title((String) row.get("title"))
                        .synthetic(toBoolean(row.get("synthetic")))
                        .voteCount(((Number) row.get("vote_count")).intValue())
                        .commentCount(((Number) row.get("comment_count")).intValue())
                        .viewCount(((Number) row.get("view_count")).doubleValue())
                        .score(((Number) row.get("score")).doubleValue())
                        .createdAt(ISO_FORMATTER.format(((java.sql.Timestamp) row.get("created_at")).toInstant()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 커뮤니티 인사이트: DAU, WAU, MAU, retention, funnel, content health
     */
    public InsightsDto getCommunityInsights(int days, boolean realOnly) {
        int clampedDays = Math.max(1, Math.min(90, days));
        LocalDate today = LocalDate.now(KST);
        LocalDate fromDate = today.minusDays(clampedDays);

        // DAU: 최근 1일(어제) daily_stats 기준
        LocalDate yesterday = today.minusDays(1);
        int dau = dailyStatsRepository.findByStatDate(yesterday)
                .map(ds -> ds.getDau())
                .orElse(0);

        // WAU, MAU: native query로 user 집계
        Instant week7daysAgo = today.minusDays(7).atStartOfDay(KST).toInstant();
        Instant month30daysAgo = today.minusDays(30).atStartOfDay(KST).toInstant();

        long wau = countActiveUsersSince(week7daysAgo, realOnly);
        long mau = countActiveUsersSince(month30daysAgo, realOnly);

        // stickiness
        Double stickiness = mau > 0 ? (double) dau / mau : null;

        // funnel
        long activeUsers = countActiveFunnelUsers(realOnly);
        long voters = countVoters(realOnly);
        long commenters = countCommenters(realOnly);
        long posters = countPosters(realOnly);

        // content health
        double avgCommentsPerPost = calculateAvgCommentsPerPost(fromDate, today, realOnly);
        double noComments24hRate = calculateNoComments24hRate(fromDate, today, realOnly);

        // production series
        List<ProductionSeriesDto> productionSeries = getProductionSeries(fromDate, today, realOnly);

        InsightsDto.FunnelDto funnel = InsightsDto.FunnelDto.builder()
                .active(activeUsers)
                .voters(voters)
                .commenters(commenters)
                .posters(posters)
                .build();

        InsightsDto.ContentHealthDto contentHealth = InsightsDto.ContentHealthDto.builder()
                .avgCommentsPerPost(avgCommentsPerPost)
                .noComments24hRate(noComments24hRate)
                .build();

        return InsightsDto.builder()
                .dau(dau)
                .wau(wau)
                .mau(mau)
                .stickiness(stickiness)
                .funnel(funnel)
                .contentHealth(contentHealth)
                .productionSeries(productionSeries)
                .build();
    }

    /**
     * 트래픽 요약: visit_events 집계
     */
    public TrafficDto getTraffic(int days) {
        int clampedDays = Math.max(1, Math.min(90, days));
        LocalDate today = LocalDate.now(KST);
        LocalDate fromDate = today.minusDays(clampedDays);
        Instant since = fromDate.atStartOfDay(KST).toInstant();

        String dailyCountSql = """
            SELECT DATE_FORMAT(CONVERT_TZ(occurred_at, '+00:00', '+09:00'), '%Y-%m-%d') as date,
                   COUNT(*) as visits,
                   COUNT(DISTINCT session_key) as unique_sessions
            FROM visit_events
            WHERE occurred_at >= ?
            GROUP BY DATE_FORMAT(CONVERT_TZ(occurred_at, '+00:00', '+09:00'), '%Y-%m-%d')
            ORDER BY date ASC
            """;

        List<Map<String, Object>> dailyRows = jdbcTemplate.queryForList(dailyCountSql, since);
        List<TrafficDailyDto> dailySeries = dailyRows.stream()
                .map(row -> TrafficDailyDto.builder()
                        .date((String) row.get("date"))
                        .visits(((Number) row.get("visits")).intValue())
                        .uniqueSessions(((Number) row.get("unique_sessions")).intValue())
                        .build())
                .collect(Collectors.toList());

        // Top sources
        String topSourcesSql = """
            SELECT utm_source as source, COUNT(*) as visits
            FROM visit_events
            WHERE occurred_at >= ? AND utm_source IS NOT NULL
            GROUP BY utm_source
            ORDER BY visits DESC
            LIMIT 10
            """;

        List<Map<String, Object>> sourceRows = jdbcTemplate.queryForList(topSourcesSql, since);
        List<TrafficSourceDto> topSources = sourceRows.stream()
                .map(row -> TrafficSourceDto.builder()
                        .source((String) row.get("source"))
                        .visits(((Number) row.get("visits")).intValue())
                        .build())
                .collect(Collectors.toList());

        // Top campaigns
        String topCampaignsSql = """
            SELECT utm_campaign as campaign, COUNT(*) as visits
            FROM visit_events
            WHERE occurred_at >= ? AND utm_campaign IS NOT NULL
            GROUP BY utm_campaign
            ORDER BY visits DESC
            LIMIT 10
            """;

        List<Map<String, Object>> campaignRows = jdbcTemplate.queryForList(topCampaignsSql, since);
        List<TrafficCampaignDto> topCampaigns = campaignRows.stream()
                .map(row -> TrafficCampaignDto.builder()
                        .campaign((String) row.get("campaign"))
                        .visits(((Number) row.get("visits")).intValue())
                        .build())
                .collect(Collectors.toList());

        return TrafficDto.builder()
                .dailySeries(dailySeries)
                .topSources(topSources)
                .topCampaigns(topCampaigns)
                .build();
    }

    // ========== HELPER METHODS ==========

    private long countMarketingJobsByStatusAndAutoPublish(List<String> statuses, boolean autoPublish) {
        String sql = "SELECT COUNT(*) FROM marketing_job WHERE status IN (" +
                String.join(",", Collections.nCopies(statuses.size(), "?")) +
                ") AND auto_publish = ?";
        Object[] params = new Object[statuses.size() + 1];
        for (int i = 0; i < statuses.size(); i++) {
            params[i] = statuses.get(i);
        }
        params[statuses.size()] = autoPublish ? 1 : 0;
        Number count = jdbcTemplate.queryForObject(sql, params, Number.class);
        return count != null ? count.longValue() : 0L;
    }

    private long countMarketingJobsByStatus(List<String> statuses) {
        String sql = "SELECT COUNT(*) FROM marketing_job WHERE status IN (" +
                String.join(",", Collections.nCopies(statuses.size(), "?")) + ")";
        Object[] params = statuses.toArray();
        Number count = jdbcTemplate.queryForObject(sql, params, Number.class);
        return count != null ? count.longValue() : 0L;
    }

    private Map<String, Long> countPersonaActionLogByStatus(List<String> statuses, Instant since) {
        String sql = "SELECT status, COUNT(*) as cnt FROM persona_action_log WHERE created_at >= ? " +
                "AND status IN (" + String.join(",", Collections.nCopies(statuses.size(), "?")) + ") " +
                "GROUP BY status";
        Object[] params = new Object[statuses.size() + 1];
        params[0] = since;
        for (int i = 0; i < statuses.size(); i++) {
            params[i + 1] = statuses.get(i);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String status = (String) row.get("status");
            long cnt = ((Number) row.get("cnt")).longValue();
            result.put(status, cnt);
        }
        return result;
    }

    private long countCrisisTriggersLastDay() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        return dailyStatsRepository.findByStatDate(yesterday)
                .map(ds -> (long) ds.getCrisisTriggers())
                .orElse(0L);
    }

    private static boolean toBoolean(Object val) {
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).intValue() != 0;
        return false;
    }

    private Double calculatePercent(long delta, long prev) {
        if (prev == 0) return null;
        return (double) delta / prev * 100;
    }

    private long countActiveUsersSince(Instant since, boolean realOnly) {
        String sql = """
            SELECT COUNT(DISTINCT user_id) FROM (
                SELECT author_id as user_id FROM posts WHERE created_at >= ? AND deleted_at IS NULL
                UNION
                SELECT author_id as user_id FROM post_comments WHERE created_at >= ? AND deleted_at IS NULL
                UNION
                SELECT voter_user_id as user_id FROM votes WHERE created_at >= ?
            ) active_users
            """ + (realOnly ? " WHERE user_id NOT IN (SELECT id FROM users WHERE synthetic = 1)" : "");

        List<Object> params = Arrays.asList(since, since, since);
        Number count = jdbcTemplate.queryForObject(sql, params.toArray(), Number.class);
        return count != null ? count.longValue() : 0L;
    }

    private long countActiveFunnelUsers(boolean realOnly) {
        String sql = """
            SELECT COUNT(DISTINCT user_id) FROM (
                SELECT author_id as user_id FROM posts WHERE deleted_at IS NULL
                UNION
                SELECT author_id as user_id FROM post_comments WHERE deleted_at IS NULL
                UNION
                SELECT voter_user_id as user_id FROM votes
            ) active_users
            """ + (realOnly ? " WHERE user_id NOT IN (SELECT id FROM users WHERE synthetic = 1)" : "");

        Number count = jdbcTemplate.queryForObject(sql, Number.class);
        return count != null ? count.longValue() : 0L;
    }

    private long countVoters(boolean realOnly) {
        String sql = "SELECT COUNT(DISTINCT voter_user_id) FROM votes WHERE 1=1" +
                (realOnly ? " AND voter_user_id NOT IN (SELECT id FROM users WHERE synthetic = 1)" : "");
        Number count = jdbcTemplate.queryForObject(sql, Number.class);
        return count != null ? count.longValue() : 0L;
    }

    private long countCommenters(boolean realOnly) {
        String sql = "SELECT COUNT(DISTINCT author_id) FROM post_comments WHERE deleted_at IS NULL" +
                (realOnly ? " AND author_id NOT IN (SELECT id FROM users WHERE synthetic = 1)" : "");
        Number count = jdbcTemplate.queryForObject(sql, Number.class);
        return count != null ? count.longValue() : 0L;
    }

    private long countPosters(boolean realOnly) {
        String sql = "SELECT COUNT(DISTINCT author_id) FROM posts WHERE deleted_at IS NULL" +
                (realOnly ? " AND author_id NOT IN (SELECT id FROM users WHERE synthetic = 1)" : "");
        Number count = jdbcTemplate.queryForObject(sql, Number.class);
        return count != null ? count.longValue() : 0L;
    }

    private double calculateAvgCommentsPerPost(LocalDate fromDate, LocalDate toDate, boolean realOnly) {
        Instant fromInstant = fromDate.atStartOfDay(KST).toInstant();
        Instant toInstant = toDate.plusDays(1).atStartOfDay(KST).toInstant();

        String postCountSql = "SELECT COUNT(*) FROM posts WHERE created_at >= ? AND created_at < ? AND deleted_at IS NULL" +
                (realOnly ? " AND author_id NOT IN (SELECT id FROM users WHERE synthetic = 1)" : "");
        Number postCount = jdbcTemplate.queryForObject(postCountSql, new Object[]{fromInstant, toInstant}, Number.class);

        String commentCountSql = "SELECT COUNT(*) FROM post_comments pc WHERE pc.created_at >= ? AND pc.created_at < ? AND pc.deleted_at IS NULL" +
                (realOnly ? " AND pc.author_id NOT IN (SELECT id FROM users WHERE synthetic = 1)" : "");
        Number commentCount = jdbcTemplate.queryForObject(commentCountSql, new Object[]{fromInstant, toInstant}, Number.class);

        long posts = postCount != null ? postCount.longValue() : 0;
        long comments = commentCount != null ? commentCount.longValue() : 0;

        return posts > 0 ? (double) comments / posts : 0.0;
    }

    private double calculateNoComments24hRate(LocalDate fromDate, LocalDate toDate, boolean realOnly) {
        Instant fromInstant = fromDate.atStartOfDay(KST).toInstant();
        Instant toInstant = toDate.plusDays(1).atStartOfDay(KST).toInstant();

        // 기간 내 생성 후 24h 이상 경과한 게시글 중 댓글 0인 비율
        String sql = """
            SELECT
                COUNT(CASE WHEN cc.cnt IS NULL OR cc.cnt = 0 THEN 1 END) * 100.0 / COUNT(*) as rate
            FROM posts p
            LEFT JOIN (SELECT post_id, COUNT(*) cnt FROM post_comments WHERE deleted_at IS NULL GROUP BY post_id) cc ON cc.post_id = p.id
            WHERE p.created_at >= ? AND p.created_at < ? AND p.deleted_at IS NULL
                AND TIMESTAMPDIFF(HOUR, p.created_at, NOW()) >= 24
            """ + (realOnly ? " AND p.author_id NOT IN (SELECT id FROM users WHERE synthetic = 1)" : "");

        Number rate = jdbcTemplate.queryForObject(sql, new Object[]{fromInstant, toInstant}, Number.class);
        return rate != null ? rate.doubleValue() : 0.0;
    }

    private List<ProductionSeriesDto> getProductionSeries(LocalDate fromDate, LocalDate toDate, boolean realOnly) {
        Instant fromInstant = fromDate.atStartOfDay(KST).toInstant();
        Instant toInstant = toDate.plusDays(1).atStartOfDay(KST).toInstant();

        String sql = """
            SELECT
                DATE_FORMAT(CONVERT_TZ(p.created_at, '+00:00', '+09:00'), '%Y-%m-%d') as date,
                SUM(CASE WHEN u.synthetic = 0 THEN 1 ELSE 0 END) as real_posts,
                SUM(CASE WHEN u.synthetic = 1 THEN 1 ELSE 0 END) as ai_posts,
                SUM(CASE WHEN pc.u_synthetic = 0 THEN 1 ELSE 0 END) as real_comments,
                SUM(CASE WHEN pc.u_synthetic = 1 THEN 1 ELSE 0 END) as ai_comments
            FROM posts p
            JOIN users u ON p.author_id = u.id
            LEFT JOIN (
                SELECT post_id, u2.synthetic as u_synthetic
                FROM post_comments pc2
                JOIN users u2 ON pc2.author_id = u2.id
                WHERE pc2.deleted_at IS NULL
            ) pc ON pc.post_id = p.id
            WHERE p.created_at >= ? AND p.created_at < ? AND p.deleted_at IS NULL
            """ + (realOnly ? " AND u.synthetic = 0 " : "") + """
            GROUP BY DATE_FORMAT(CONVERT_TZ(p.created_at, '+00:00', '+09:00'), '%Y-%m-%d')
            ORDER BY date ASC
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, fromInstant, toInstant);

        return rows.stream()
                .map(row -> ProductionSeriesDto.builder()
                        .date((String) row.get("date"))
                        .realPosts(((Number) row.get("real_posts")).intValue())
                        .aiPosts(((Number) row.get("ai_posts")).intValue())
                        .realComments(((Number) row.get("real_comments")).intValue())
                        .aiComments(((Number) row.get("ai_comments")).intValue())
                        .build())
                .collect(Collectors.toList());
    }

    // ========== DTO CLASSES ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionCenterDto {
        private long pendingReports;
        private long openInquiries;
        private long marketingAwaitingApproval;
        private long marketingFailed;
        private long aiFailuresToday;
        private long aiBlockedToday;
        private long crisisRecent24h;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KpiMetricDto {
        private String key;
        private String label;
        private long value;
        private int delta;
        private Double deltaPercent;
        private List<Integer> sparkline;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PulseDto {
        private List<PulseDayDataDto> data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PulseDayDataDto {
        private int hour;
        private int postsReal;
        private int postsAi;
        private int commentsReal;
        private int commentsAi;
        private int votesReal;
        private int votesAi;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HotPostDto {
        private String id;
        private String title;
        private boolean synthetic;
        private int voteCount;
        private int commentCount;
        private double viewCount;
        private double score;
        private String createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsightsDto {
        private int dau;
        private long wau;
        private long mau;
        private Double stickiness;
        private FunnelDto funnel;
        private ContentHealthDto contentHealth;
        private List<ProductionSeriesDto> productionSeries;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FunnelDto {
            private long active;
            private long voters;
            private long commenters;
            private long posters;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ContentHealthDto {
            private double avgCommentsPerPost;
            private double noComments24hRate;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductionSeriesDto {
        private String date;
        private int realPosts;
        private int aiPosts;
        private int realComments;
        private int aiComments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficDto {
        private List<TrafficDailyDto> dailySeries;
        private List<TrafficSourceDto> topSources;
        private List<TrafficCampaignDto> topCampaigns;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficDailyDto {
        private String date;
        private int visits;
        private int uniqueSessions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficSourceDto {
        private String source;
        private int visits;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficCampaignDto {
        private String campaign;
        private int visits;
    }
}
