package com.againspring.aiuser.orchestrator.service.threadplan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Thin "obvious twin" guard for PLAN AI_POST generation.
 *
 * <p>Compares a candidate title+body against recent <strong>published</strong> AI posts
 * ({@code users.synthetic = 1}, same identification as {@code DailyPostQuotaService}).
 * Window: last {@value #WINDOW_DAYS} days, up to {@value #RECENT_LIMIT} rows from each of
 * two sources — published posts and still-unpublished scheduled holds.
 * No embedding — char 2-gram Jaccard (adapted from {@code ActionExecutor.maxBigramJaccard}).
 *
 * <h2>Thresholds (high bar — catch near-copies like "직장 엄마" twins, not all WORK posts)</h2>
 * <ul>
 *   <li>Exact normalized title match → twin</li>
 *   <li>Title char-bigram Jaccard ≥ {@value #TITLE_JACCARD_THRESHOLD}</li>
 *   <li>Body char-bigram Jaccard ≥ {@value #BODY_JACCARD_THRESHOLD}</li>
 * </ul>
 *
 * <p>Texts shorter than 12 chars (whitespace stripped) yield an empty bigram set and are
 * exempt from Jaccard — short titles rely on exact normalized match only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoryTwinGuard {

    public static final int WINDOW_DAYS = 14;
    /**
     * 각 소스(발행분·홀딩분)에서 가져올 최대 건수. 새벽 배치가 한 번에 최대 100건까지 만들므로
     * 30이면 같은 배치 안에서도 앞쪽 글들이 비교 대상에서 밀려난다(2026-09-05 리뷰).
     */
    public static final int RECENT_LIMIT = 120;
    /** Title near-copy bar (stricter than body). */
    public static final double TITLE_JACCARD_THRESHOLD = 0.45;
    /** Body near-copy bar (slightly looser — catch paraphrased twins). */
    public static final double BODY_JACCARD_THRESHOLD = 0.35;

    private final JdbcTemplate jdbcTemplate;

    public record RecentAiPost(String title, String body) { }

    /**
     * @return true if the candidate is an obvious twin of a recent published AI post
     */
    public boolean isObviousTwin(String title, String body) {
        return twinReason(title, body, loadRecentAiPosts()).isPresent();
    }

    /**
     * Testable pure check against a provided recent list.
     *
     * @return human-readable match reason, or empty if not a twin
     */
    public Optional<String> twinReason(String title, String body, List<RecentAiPost> recents) {
        if (recents == null || recents.isEmpty()) return Optional.empty();
        String titleNorm = normalize(title);
        String bodyNorm = normalize(body);
        if (titleNorm.isEmpty() && bodyNorm.isEmpty()) return Optional.empty();

        for (RecentAiPost recent : recents) {
            if (recent == null) continue;
            String rTitle = normalize(recent.title());
            String rBody = normalize(recent.body());

            if (!titleNorm.isEmpty() && !rTitle.isEmpty() && titleNorm.equals(rTitle)) {
                return Optional.of("exact-title");
            }
            if (!titleNorm.isEmpty() && !rTitle.isEmpty()
                    && maxBigramJaccard(titleNorm, List.of(rTitle)) >= TITLE_JACCARD_THRESHOLD) {
                return Optional.of("title-jaccard>=" + TITLE_JACCARD_THRESHOLD);
            }
            if (!bodyNorm.isEmpty() && !rBody.isEmpty()
                    && maxBigramJaccard(bodyNorm, List.of(rBody)) >= BODY_JACCARD_THRESHOLD) {
                return Optional.of("body-jaccard>=" + BODY_JACCARD_THRESHOLD);
            }
        }
        return Optional.empty();
    }

    /**
     * Recent AI stories to compare against: published posts <em>and</em> still-unpublished
     * scheduled holds, both from the last {@value #WINDOW_DAYS} days, newest first,
     * up to {@value #RECENT_LIMIT} each.
     *
     * <p>발행분만 보면 같은 배치에서 동시에 만들어지는 형제 글끼리는 서로 비교되지 않는다.
     * 새벽 배치가 한 번에 최대 100건까지 만드는데, 그것들이 전부 홀딩 상태라 이 가드에
     * 보이지 않았다. 대량 배치일수록 실질 비교 범위가 "최근 14일"이 아니라 "최근 발행 30건"
     * (몇 시간치)으로 쪼그라들던 사각지대다(2026-09-05 리뷰).</p>
     *
     * <p>Fail-open (empty list) on query errors so generation is not blocked by DB blips.</p>
     */
    public List<RecentAiPost> loadRecentAiPosts() {
        List<RecentAiPost> out = new java.util.ArrayList<>(queryPublished());
        out.addAll(queryScheduledHolds());
        return out;
    }

    private List<RecentAiPost> queryPublished() {
        try {
            return jdbcTemplate.query(
                    "SELECT COALESCE(NULLIF(TRIM(p.user_title), ''), p.title) AS title,"
                            + " p.body_published AS body"
                            + " FROM posts p"
                            + " JOIN users u ON p.author_id = u.id"
                            + " WHERE u.synthetic = 1"
                            + "   AND p.deleted_at IS NULL"
                            + "   AND p.body_published IS NOT NULL"
                            + "   AND p.created_at >= NOW() - INTERVAL " + WINDOW_DAYS + " DAY"
                            + " ORDER BY p.created_at DESC"
                            + " LIMIT " + RECENT_LIMIT,
                    (rs, rowNum) -> new RecentAiPost(rs.getString("title"), rs.getString("body")));
        } catch (Exception e) {
            log.warn("StoryTwinGuard published query failed (fail-open): {}", e.getMessage());
            return List.of();
        }
    }

    /** 아직 발행되지 않은 홀딩분. 같은 배치의 형제 글을 서로 비교하기 위한 것이다. */
    private List<RecentAiPost> queryScheduledHolds() {
        try {
            return jdbcTemplate.query(
                    "SELECT s.title AS title, s.body AS body"
                            + " FROM ai_scheduled_posts s"
                            + " WHERE s.status = 'SCHEDULED'"
                            + "   AND s.body IS NOT NULL"
                            + "   AND s.created_at >= NOW() - INTERVAL " + WINDOW_DAYS + " DAY"
                            + " ORDER BY s.created_at DESC"
                            + " LIMIT " + RECENT_LIMIT,
                    (rs, rowNum) -> new RecentAiPost(rs.getString("title"), rs.getString("body")));
        } catch (Exception e) {
            log.warn("StoryTwinGuard scheduled-hold query failed (fail-open): {}", e.getMessage());
            return List.of();
        }
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Char 2-gram Jaccard max vs recents — same algorithm as ActionExecutor (package-private there). */
    static double maxBigramJaccard(String text, List<String> recents) {
        Set<String> a = charBigrams(text);
        if (a.isEmpty() || recents == null) return 0.0;
        double max = 0.0;
        for (String r : recents) {
            Set<String> b = charBigrams(r);
            if (b.isEmpty()) continue;
            int inter = 0;
            for (String g : a) if (b.contains(g)) inter++;
            int union = a.size() + b.size() - inter;
            double j = union > 0 ? (double) inter / union : 0.0;
            if (j > max) max = j;
        }
        return max;
    }

    /** Whitespace stripped; under 12 chars → empty set (noise floor, mirrors ActionExecutor). */
    static Set<String> charBigrams(String text) {
        if (text == null) return Set.of();
        String t = text.replaceAll("\\s+", "");
        if (t.length() < 12) return Set.of();
        Set<String> grams = new HashSet<>();
        for (int i = 0; i < t.length() - 1; i++) grams.add(t.substring(i, i + 2));
        return grams;
    }

    /** Convenience for callers that want a mutable copy of loaded posts (tests). */
    static List<RecentAiPost> copyOf(List<RecentAiPost> src) {
        return src == null ? List.of() : new ArrayList<>(src);
    }
}
