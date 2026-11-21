package com.againspring.marketing.experiment;

import com.againspring.domain.community.Post;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.domain.marketing.MarketingPublicationOutcome;
import com.againspring.domain.marketing.MarketingPublicationStats;
import com.againspring.marketing.MarketingPopularityScorer;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.againspring.repository.marketing.MarketingPublicationOutcomeRepository;
import com.againspring.repository.marketing.MarketingPublicationStatsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 게시 48시간 성과 채점(설계 §2.2).
 *
 * <pre>
 * engagement = likes + 2*(comments|replies) + 3*(shares + saves + reposts)
 * reward     = 0.6*min(viewRatio,10) + 0.4*min(engRatio,5);   breakout = viewRatio >= 3.0
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingOutcomeService {

    static final Duration WINDOW_START = Duration.ofHours(36);
    static final Duration WINDOW_END = Duration.ofHours(96);
    static final int BASELINE_MIN_SAMPLES = 10;
    static final double BREAKOUT_VIEW_RATIO = 3.0;
    /** 채점 후보 잡을 훑는 기간(WINDOW_END + 여유). */
    static final Duration LOOKBACK = Duration.ofDays(14);

    private final MarketingJobRepository jobRepository;
    private final MarketingPublicationStatsRepository statsRepository;
    private final MarketingPublicationOutcomeRepository outcomeRepository;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** 아직 outcome이 없는 게시 완료 잡을 채점한다. 반환 = 새로 만든 행 수. */
    public int scorePending() {
        Instant now = Instant.now(clock);
        List<MarketingJob> jobs = new ArrayList<>(jobRepository.findPublishAttemptsSince(now.minus(LOOKBACK)));
        jobs.sort(Comparator.comparing(MarketingOutcomeService::publishedAt));
        int created = 0;
        for (MarketingJob job : jobs) {
            for (String platform : publishedPlatforms(job)) {
                try {
                    if (scoreOne(job, platform, now)) {
                        created++;
                    }
                } catch (Exception e) {
                    log.warn("outcome scoring failed job={} platform={}: {}", job.getId(), platform, e.getMessage());
                }
            }
        }
        return created;
    }

    private boolean scoreOne(MarketingJob job, String platform, Instant now) {
        if (outcomeRepository.existsByJobIdAndPlatform(job.getId(), platform)) {
            return false;
        }
        Instant published = publishedAt(job);
        Instant from = published.plus(WINDOW_START);
        Instant to = published.plus(WINDOW_END);

        Optional<MarketingPublicationStats> snap = statsRepository.findByJobIdOrderByCollectedAtDesc(job.getId())
            .stream()
            .filter(s -> s.getCollectedAt() != null
                && platform.equals(MarketingPopularityScorer.normalizePlatform(s.getPlatform()))
                && s.hasAnyMetric()
                && !s.getCollectedAt().isBefore(from)
                && !s.getCollectedAt().isAfter(to))
            .min(Comparator.comparing(MarketingPublicationStats::getCollectedAt));

        Post post = postRepository.findById(job.getPostId()).orElse(null);
        MarketingPublicationOutcome.MarketingPublicationOutcomeBuilder b = MarketingPublicationOutcome.builder()
            .jobId(job.getId())
            .postId(job.getPostId())
            .platform(platform)
            .formatArm(job.getFormatArm())
            .category(post != null && post.getCategory() != null ? post.getCategory().name() : null)
            .hookEmotion(post != null ? post.getHookEmotion() : null)
            .publishedAt(published)
            .computedAt(now);

        if (snap.isEmpty()) {
            if (now.isBefore(to)) {
                return false; // 아직 구간 안 — 다음 실행에서 다시 본다
            }
            outcomeRepository.save(b.status(MarketingPublicationOutcome.MISSING).breakout(false).build());
            return true;
        }

        Metrics m = extract(platform, snap.get().getMetricsJson());
        double engRate = (double) m.engagement() / Math.max(m.primary(), 1L);
        List<MarketingPublicationOutcome> base =
            outcomeRepository.findTop30ByPlatformAndStatusAndPublishedAtBeforeOrderByPublishedAtDesc(
                platform, MarketingPublicationOutcome.SCORED, published);

        double viewRatio = 1.0;
        double engRatio = 1.0;
        if (base.size() >= BASELINE_MIN_SAMPLES) {
            double medPrimary = median(base.stream()
                .map(o -> o.getPrimaryMetric() == null ? 0.0 : o.getPrimaryMetric().doubleValue()).toList());
            double medEngRate = median(base.stream().map(o -> {
                long p = o.getPrimaryMetric() == null ? 0L : o.getPrimaryMetric();
                long e = o.getEngagement() == null ? 0L : o.getEngagement();
                return (double) e / Math.max(p, 1L);
            }).toList());
            viewRatio = medPrimary > 0 ? m.primary() / medPrimary : 1.0;
            engRatio = medEngRate > 0 ? engRate / medEngRate : (engRate > 0 ? 2.0 : 1.0);
        }
        double reward = reward(viewRatio, engRatio);

        outcomeRepository.save(b
            .status(MarketingPublicationOutcome.SCORED)
            .snapshotAt(snap.get().getCollectedAt())
            .primaryMetric(m.primary())
            .engagement(m.engagement())
            .viewRatio(viewRatio)
            .engRatio(engRatio)
            .reward(reward)
            .breakout(viewRatio >= BREAKOUT_VIEW_RATIO)
            .build());
        return true;
    }

    static double reward(double viewRatio, double engRatio) {
        return 0.6 * Math.min(viewRatio, 10.0) + 0.4 * Math.min(engRatio, 5.0);
    }

    static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> s = new ArrayList<>(values);
        s.sort(Double::compare);
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
    }

    record Metrics(long primary, long engagement) {}

    Metrics extract(String platform, String metricsJson) {
        JsonNode n;
        try {
            n = objectMapper.readTree(metricsJson == null ? "{}" : metricsJson);
        } catch (Exception e) {
            return new Metrics(0, 0);
        }
        long primary;
        long likes = num(n, "likes");
        long engagement;
        switch (platform) {
            case MarketingPopularityScorer.PLATFORM_X_THREAD -> {
                primary = num(n, "impressions");
                engagement = likes + 2 * num(n, "replies") + 3 * num(n, "reposts");
            }
            case MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS -> {
                primary = num(n, "views");
                engagement = likes + 2 * num(n, "comments");
            }
            default -> { // instagram_feed / instagram_reels
                primary = num(n, "reach");
                engagement = likes + 2 * num(n, "comments") + 3 * (num(n, "shares") + num(n, "saves"));
            }
        }
        return new Metrics(primary, engagement);
    }

    private static long num(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v != null && v.isNumber() ? v.asLong() : 0L;
    }

    static Instant publishedAt(MarketingJob job) {
        return job.getScheduledPublishAt() != null ? job.getScheduledPublishAt() : job.getCreatedAt();
    }

    private List<String> publishedPlatforms(MarketingJob job) {
        List<String> out = new ArrayList<>();
        String publications = job.getPublications();
        if (publications == null || publications.isBlank()) {
            return out;
        }
        try {
            JsonNode arr = objectMapper.readTree(publications);
            if (!arr.isArray()) {
                return out;
            }
            for (JsonNode e : arr) {
                String platform = e.path("platform").asText(null);
                if (platform != null && "PUBLISHED".equalsIgnoreCase(e.path("state").asText(null))) {
                    String p = MarketingPopularityScorer.normalizePlatform(platform);
                    if (MarketingPopularityScorer.isRankedPlatform(p) && !out.contains(p)) {
                        out.add(p);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("outcome: publications parse failed job={}: {}", job.getId(), ex.getMessage());
        }
        return out;
    }
}
