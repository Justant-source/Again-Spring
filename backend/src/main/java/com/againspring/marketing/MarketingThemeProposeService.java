package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingPublicationStats;
import com.againspring.repository.marketing.MarketingPublicationStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Phase 3 theme proposal engine (plan S8 / S12 / S13): emotion×category heatmap
 * + top/bottom boost suggestions for one ranked platform.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingThemeProposeService {

    private static final int MAX_WEEKS_AGO = 12;
    private static final int TOP_K = 2;
    private static final int BOTTOM_K = 2;
    /** Cross-cell unlocked count below this → also emit rolled-axis proposals. */
    private static final int SPARSE_UNLOCKED_THRESHOLD = 4;

    private final MarketingPublicationStatsRepository statsRepository;
    private final MarketingThemeBoostService themeBoostService;
    private final MarketingStatsDashboardService dashboardService;
    private final JdbcTemplate jdbcTemplate;

    // ── public API ──

    public ThemeMatrixView buildMatrix(String platform, int weeksAgo) {
        PlatformWeekContext ctx = prepare(platform, weeksAgo);
        return toView(ctx);
    }

    /**
     * Cross-cell proposals only (top {@value TOP_K} + bottom {@value BOTTOM_K} unlocked cells).
     * Rolled-axis suggestions live on {@link ThemeMatrixView#rolledProposals()}.
     */
    public List<Proposal> propose(String platform, int weeksAgo) {
        PlatformWeekContext ctx = prepare(platform, weeksAgo);
        return List.copyOf(ctx.crossProposals);
    }

    // ── core ──

    private PlatformWeekContext prepare(String platform, int weeksAgo) {
        weeksAgo = Math.max(0, Math.min(weeksAgo, MAX_WEEKS_AGO));
        String plat = MarketingPopularityScorer.normalizePlatform(platform);
        if (!MarketingPopularityScorer.isRankedPlatform(plat)) {
            throw new IllegalArgumentException("Unknown ranked platform: " + platform);
        }

        MarketingStatsDashboardService.WeekWindow week =
            MarketingStatsDashboardService.weekWindow(weeksAgo);
        MarketingStatsDashboardService.WeekWindow prev = new MarketingStatsDashboardService.WeekWindow(
            week.start().minusWeeks(1),
            week.start()
        );

        Instant fetchSince = prev.startInstant();
        List<MarketingPublicationStats> rows = statsRepository.findCollectedSince(fetchSince);
        List<MarketingPublicationStats> currRows = latestInWindow(
            rows, plat, week.startInstant(), week.endInstant());
        List<MarketingPublicationStats> prevRows = latestInWindow(
            rows, plat, prev.startInstant(), prev.endInstant());

        Set<String> postIds = new HashSet<>();
        for (MarketingPublicationStats r : currRows) {
            if (r.getPostId() != null && !r.getPostId().isBlank()) {
                postIds.add(r.getPostId());
            }
        }
        for (MarketingPublicationStats r : prevRows) {
            if (r.getPostId() != null && !r.getPostId().isBlank()) {
                postIds.add(r.getPostId());
            }
        }
        Map<String, PostMeta> metaByPost = loadPostMeta(postIds);

        int minN = themeBoostService.getMinN();
        double boostMin = themeBoostService.getBoostMin();
        double boostMax = themeBoostService.getBoostMax();
        double deltaCap = themeBoostService.getDeltaCap();
        String primaryMetric = MarketingStatsDashboardService.defaultPrimaryMetric(plat);

        Map<CellKey, CellAgg> currCells = aggregateCells(currRows, metaByPost, plat);
        Map<CellKey, CellAgg> prevCells = aggregateCells(prevRows, metaByPost, plat);

        UnknownHints unknownHints = countUnknown(currRows, metaByPost);

        List<Double> cellScoresForMedian = new ArrayList<>();
        for (CellAgg agg : currCells.values()) {
            if (agg.n > 0) {
                cellScoresForMedian.add(agg.avg());
            }
        }
        double platformMedian = median(cellScoresForMedian);

        List<MatrixCell> cells = new ArrayList<>();
        List<ScoredAxis> unlocked = new ArrayList<>();

        for (String emotion : MarketingThemeBoostService.EMOTIONS) {
            for (String category : MarketingThemeBoostService.CATEGORIES) {
                CellKey key = new CellKey(emotion, category);
                CellAgg curr = currCells.getOrDefault(key, CellAgg.empty());
                CellAgg prevAgg = prevCells.getOrDefault(key, CellAgg.empty());
                double score = curr.avg();
                double prevScore = prevAgg.avg();
                Double delta = prevAgg.n > 0 ? relativeDelta(score, prevScore) : null;
                boolean locked = curr.n < minN;
                double boost = themeBoostService.getBoost(plat, emotion, category);

                cells.add(new MatrixCell(emotion, category, curr.n, score, delta, boost, locked));

                if (!locked) {
                    double combo = proposalCombo(score, prevScore, prevAgg.n > 0, platformMedian);
                    double suggested = mapSuggestedBoost(combo, boost, boostMin, boostMax, deltaCap);
                    unlocked.add(new ScoredAxis(
                        emotion, category, "cross", curr.n, score, prevScore,
                        delta != null ? delta : 0.0, combo, boost, suggested));
                }
            }
        }

        List<Proposal> crossProposals = selectTopBottom(unlocked);
        List<Proposal> rolledProposals = List.of();
        if (unlocked.size() < SPARSE_UNLOCKED_THRESHOLD) {
            rolledProposals = buildRolledProposals(
                currRows, prevRows, metaByPost, plat, platformMedian,
                minN, boostMin, boostMax, deltaCap);
        }

        return new PlatformWeekContext(
            plat, primaryMetric, week, prev, cells, crossProposals, rolledProposals, unknownHints);
    }

    private ThemeMatrixView toView(PlatformWeekContext ctx) {
        return new ThemeMatrixView(
            ctx.platform,
            ctx.week.start().toLocalDate().toString(),
            ctx.week.end().toLocalDate().toString(),
            ctx.prev.start().toLocalDate().toString(),
            ctx.prev.end().toLocalDate().toString(),
            ctx.primaryMetric,
            List.copyOf(MarketingThemeBoostService.EMOTIONS),
            List.copyOf(MarketingThemeBoostService.CATEGORIES),
            List.copyOf(ctx.cells),
            List.copyOf(ctx.crossProposals),
            List.copyOf(ctx.rolledProposals),
            ctx.unknownHints
        );
    }

    private List<Proposal> buildRolledProposals(
        List<MarketingPublicationStats> currRows,
        List<MarketingPublicationStats> prevRows,
        Map<String, PostMeta> metaByPost,
        String platform,
        double platformMedian,
        int minN,
        double boostMin,
        double boostMax,
        double deltaCap
    ) {
        Map<String, CellAgg> currEmo = new HashMap<>();
        Map<String, CellAgg> prevEmo = new HashMap<>();
        Map<String, CellAgg> currCat = new HashMap<>();
        Map<String, CellAgg> prevCat = new HashMap<>();

        rollInto(currRows, metaByPost, platform, currEmo, currCat);
        rollInto(prevRows, metaByPost, platform, prevEmo, prevCat);

        List<ScoredAxis> candidates = new ArrayList<>();

        for (String emotion : MarketingThemeBoostService.EMOTIONS) {
            CellAgg curr = currEmo.getOrDefault(emotion, CellAgg.empty());
            if (curr.n < minN) {
                continue;
            }
            CellAgg prev = prevEmo.getOrDefault(emotion, CellAgg.empty());
            double score = curr.avg();
            double prevScore = prev.avg();
            double combo = proposalCombo(score, prevScore, prev.n > 0, platformMedian);
            double currentBoost = averageBoostForEmotion(platform, emotion);
            double suggested = mapSuggestedBoost(combo, currentBoost, boostMin, boostMax, deltaCap);
            candidates.add(new ScoredAxis(
                emotion, null, "emotion", curr.n, score, prevScore,
                prev.n > 0 ? relativeDelta(score, prevScore) : 0.0,
                combo, currentBoost, suggested));
        }

        for (String category : MarketingThemeBoostService.CATEGORIES) {
            CellAgg curr = currCat.getOrDefault(category, CellAgg.empty());
            if (curr.n < minN) {
                continue;
            }
            CellAgg prev = prevCat.getOrDefault(category, CellAgg.empty());
            double score = curr.avg();
            double prevScore = prev.avg();
            double combo = proposalCombo(score, prevScore, prev.n > 0, platformMedian);
            double currentBoost = averageBoostForCategory(platform, category);
            double suggested = mapSuggestedBoost(combo, currentBoost, boostMin, boostMax, deltaCap);
            candidates.add(new ScoredAxis(
                null, category, "category", curr.n, score, prevScore,
                prev.n > 0 ? relativeDelta(score, prevScore) : 0.0,
                combo, currentBoost, suggested));
        }

        return selectTopBottom(candidates);
    }

    private void rollInto(
        List<MarketingPublicationStats> rows,
        Map<String, PostMeta> metaByPost,
        String platform,
        Map<String, CellAgg> byEmotion,
        Map<String, CellAgg> byCategory
    ) {
        for (EnrichedPost post : enrichPosts(rows, metaByPost, platform)) {
            byEmotion.computeIfAbsent(post.emotion, k -> new CellAgg()).add(post.metric);
            byCategory.computeIfAbsent(post.category, k -> new CellAgg()).add(post.metric);
        }
    }

    private double averageBoostForEmotion(String platform, String emotion) {
        double sum = 0;
        int n = 0;
        for (String category : MarketingThemeBoostService.CATEGORIES) {
            sum += themeBoostService.getBoost(platform, emotion, category);
            n++;
        }
        return n > 0 ? sum / n : MarketingThemeBoostService.DEFAULT_BOOST;
    }

    private double averageBoostForCategory(String platform, String category) {
        double sum = 0;
        int n = 0;
        for (String emotion : MarketingThemeBoostService.EMOTIONS) {
            sum += themeBoostService.getBoost(platform, emotion, category);
            n++;
        }
        return n > 0 ? sum / n : MarketingThemeBoostService.DEFAULT_BOOST;
    }

    /**
     * Rank by proposal combo; emit top {@value TOP_K} and bottom {@value BOTTOM_K} without overlap.
     */
    private List<Proposal> selectTopBottom(List<ScoredAxis> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<ScoredAxis> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingDouble((ScoredAxis a) -> a.combo).reversed()
            .thenComparing(a -> Objects.toString(a.emotion, ""))
            .thenComparing(a -> Objects.toString(a.category, "")));

        LinkedHashSet<ScoredAxis> selected = new LinkedHashSet<>();
        int top = Math.min(TOP_K, ranked.size());
        for (int i = 0; i < top; i++) {
            selected.add(ranked.get(i));
        }
        int bottom = Math.min(BOTTOM_K, ranked.size());
        for (int i = ranked.size() - 1; i >= 0 && bottom > 0; i--) {
            ScoredAxis a = ranked.get(i);
            if (selected.add(a)) {
                bottom--;
            }
        }

        List<Proposal> out = new ArrayList<>();
        for (ScoredAxis a : selected) {
            String direction = a.suggestedBoost >= a.currentBoost - 1e-12 ? "up" : "down";
            if (Math.abs(a.suggestedBoost - a.currentBoost) < 1e-9) {
                direction = a.combo >= 1.0 ? "up" : "down";
            }
            out.add(new Proposal(
                a.emotion,
                a.category,
                a.axis,
                a.n,
                round4(a.score),
                round4(a.prevScore),
                round4(a.delta),
                round4(a.combo),
                round4(a.currentBoost),
                round4(a.suggestedBoost),
                direction
            ));
        }
        // Stable: ups first by combo desc, then downs by combo asc
        out.sort(Comparator
            .comparing((Proposal p) -> !"up".equals(p.direction()))
            .thenComparingDouble(p -> "up".equals(p.direction()) ? -p.proposalScore() : p.proposalScore()));
        return out;
    }

    // ── scoring ──

    /**
     * {@code 0.5 * (level vs median) + 0.5 * (1 + WoW relative)}.
     * Level = cellAvg / platformMedian (1.0 at median). WoW neutral when no previous sample.
     */
    static double proposalCombo(double score, double prevScore, boolean hasPrev, double median) {
        double level = median > 1e-9 ? score / median : 1.0;
        double wowFactor = 1.0;
        if (hasPrev) {
            wowFactor = 1.0 + relativeDelta(score, prevScore);
        }
        return 0.5 * level + 0.5 * wowFactor;
    }

    static double mapSuggestedBoost(
        double combo,
        double currentBoost,
        double boostMin,
        double boostMax,
        double deltaCap
    ) {
        double clamped = Math.max(boostMin, Math.min(boostMax, combo));
        double lo = currentBoost - deltaCap;
        double hi = currentBoost + deltaCap;
        return Math.max(lo, Math.min(hi, clamped));
    }

    static double relativeDelta(double curr, double prev) {
        if (prev > 1e-9) {
            return (curr - prev) / prev;
        }
        if (curr > 1e-9) {
            return 1.0;
        }
        return 0.0;
    }

    static double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    // ── aggregation helpers ──

    private Map<CellKey, CellAgg> aggregateCells(
        List<MarketingPublicationStats> rows,
        Map<String, PostMeta> metaByPost,
        String platform
    ) {
        Map<CellKey, CellAgg> out = new HashMap<>();
        for (EnrichedPost post : enrichPosts(rows, metaByPost, platform)) {
            CellKey key = new CellKey(post.emotion, post.category);
            out.computeIfAbsent(key, k -> new CellAgg()).add(post.metric);
        }
        return out;
    }

    private List<EnrichedPost> enrichPosts(
        List<MarketingPublicationStats> rows,
        Map<String, PostMeta> metaByPost,
        String platform
    ) {
        List<EnrichedPost> out = new ArrayList<>();
        for (MarketingPublicationStats row : rows) {
            if (row.getPostId() == null) {
                continue;
            }
            PostMeta meta = metaByPost.get(row.getPostId());
            if (meta == null) {
                continue;
            }
            String emotion = MarketingThemeBoostService.normalizeEmotion(meta.emotion);
            String category = MarketingThemeBoostService.normalizeCategory(meta.category);
            if (emotion == null || category == null) {
                continue;
            }
            long metric = dashboardService.pickMetricValue(platform, null, row.getMetricsJson());
            out.add(new EnrichedPost(row.getPostId(), emotion, category, metric));
        }
        return out;
    }

    private UnknownHints countUnknown(
        List<MarketingPublicationStats> currRows,
        Map<String, PostMeta> metaByPost
    ) {
        Set<String> seen = new LinkedHashSet<>();
        int missingEmotion = 0;
        int missingCategory = 0;
        for (MarketingPublicationStats row : currRows) {
            String postId = row.getPostId();
            if (postId == null || postId.isBlank() || !seen.add(postId)) {
                continue;
            }
            PostMeta meta = metaByPost.get(postId);
            if (meta == null) {
                missingEmotion++;
                missingCategory++;
                continue;
            }
            if (MarketingThemeBoostService.normalizeEmotion(meta.emotion) == null) {
                missingEmotion++;
            }
            if (MarketingThemeBoostService.normalizeCategory(meta.category) == null) {
                missingCategory++;
            }
        }
        return new UnknownHints(missingEmotion, missingCategory);
    }

    private Map<String, PostMeta> loadPostMeta(Set<String> postIds) {
        Map<String, PostMeta> out = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return out;
        }
        for (String postId : postIds) {
            try {
                Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT hook_emotion, category FROM posts WHERE id = ?",
                    postId
                );
                out.put(postId, new PostMeta(str(row.get("hook_emotion")), str(row.get("category"))));
            } catch (Exception e) {
                log.debug("theme propose post meta miss {}: {}", postId, e.getMessage());
            }
        }
        return out;
    }

    /**
     * Latest snapshot per jobId for one platform in [since, until).
     */
    private List<MarketingPublicationStats> latestInWindow(
        List<MarketingPublicationStats> rows,
        String platform,
        Instant since,
        Instant until
    ) {
        String norm = MarketingPopularityScorer.normalizePlatform(platform);
        Map<Long, MarketingPublicationStats> latest = new HashMap<>();
        for (MarketingPublicationStats row : rows) {
            if (row.getCollectedAt() == null || row.getJobId() == null) {
                continue;
            }
            if (row.getCollectedAt().isBefore(since) || !row.getCollectedAt().isBefore(until)) {
                continue;
            }
            if (!norm.equals(MarketingPopularityScorer.normalizePlatform(row.getPlatform()))) {
                continue;
            }
            if (!row.hasAnyMetric()) {
                continue;
            }
            MarketingPublicationStats prev = latest.get(row.getJobId());
            if (prev == null || row.getCollectedAt().isAfter(prev.getCollectedAt())) {
                latest.put(row.getJobId(), row);
            }
        }
        return new ArrayList<>(latest.values());
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static double round4(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0.0;
        }
        return Math.round(v * 10000.0) / 10000.0;
    }

    // ── internal types ──

    private record CellKey(String emotion, String category) {}

    private static final class CellAgg {
        int n;
        long sum;

        static CellAgg empty() {
            return new CellAgg();
        }

        void add(long metric) {
            n++;
            sum += metric;
        }

        double avg() {
            return n > 0 ? (double) sum / n : 0.0;
        }
    }

    private record PostMeta(String emotion, String category) {}

    private record EnrichedPost(String postId, String emotion, String category, long metric) {}

    private record ScoredAxis(
        String emotion,
        String category,
        String axis,
        int n,
        double score,
        double prevScore,
        double delta,
        double combo,
        double currentBoost,
        double suggestedBoost
    ) {}

    private static final class PlatformWeekContext {
        final String platform;
        final String primaryMetric;
        final MarketingStatsDashboardService.WeekWindow week;
        final MarketingStatsDashboardService.WeekWindow prev;
        final List<MatrixCell> cells;
        final List<Proposal> crossProposals;
        final List<Proposal> rolledProposals;
        final UnknownHints unknownHints;

        PlatformWeekContext(
            String platform,
            String primaryMetric,
            MarketingStatsDashboardService.WeekWindow week,
            MarketingStatsDashboardService.WeekWindow prev,
            List<MatrixCell> cells,
            List<Proposal> crossProposals,
            List<Proposal> rolledProposals,
            UnknownHints unknownHints
        ) {
            this.platform = platform;
            this.primaryMetric = primaryMetric;
            this.week = week;
            this.prev = prev;
            this.cells = cells;
            this.crossProposals = crossProposals;
            this.rolledProposals = rolledProposals;
            this.unknownHints = unknownHints;
        }
    }

    // ── DTOs ──

    public record ThemeMatrixView(
        String platform,
        String weekStart,
        String weekEnd,
        String prevWeekStart,
        String prevWeekEnd,
        String primaryMetric,
        List<String> emotions,
        List<String> categories,
        List<MatrixCell> cells,
        List<Proposal> proposals,
        List<Proposal> rolledProposals,
        UnknownHints unknownHints
    ) {}

    public record MatrixCell(
        String emotion,
        String category,
        int n,
        double score,
        Double delta,
        double boost,
        boolean locked
    ) {}

    public record Proposal(
        String emotion,
        String category,
        String axis,
        int n,
        double score,
        double prevScore,
        double delta,
        double proposalScore,
        double currentBoost,
        double suggestedBoost,
        String direction
    ) {}

    public record UnknownHints(int missingEmotion, int missingCategory) {}
}
