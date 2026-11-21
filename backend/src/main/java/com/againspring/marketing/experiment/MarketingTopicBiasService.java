package com.againspring.marketing.experiment;

import com.againspring.domain.marketing.MarketingPublicationOutcome;
import com.againspring.marketing.MarketingPopularityScorer;
import com.againspring.repository.marketing.MarketingPublicationOutcomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 주제 편향(설계 §2.4). (platform, category, hook_emotion) 셀의 최근 60일 평균 reward를
 * 플랫폼 평균 쪽으로 수축(prior 5건)해 배수로 만든다. clamp [0.5, 2.0].
 */
@Service
@RequiredArgsConstructor
public class MarketingTopicBiasService {

    static final int WINDOW_DAYS = 60;
    static final double PRIOR_WEIGHT = 5.0;
    static final double MIN_MULTIPLIER = 0.5;
    static final double MAX_MULTIPLIER = 2.0;

    private final MarketingPublicationOutcomeRepository outcomeRepository;
    private final MarketingExperimentSettings settings;
    private final Clock clock;

    public record TopicCell(String platform, String category, String hookEmotion,
                            int n, double meanReward, double multiplier) {}

    /** 플래그가 꺼져 있으면 1.0. */
    public double multiplier(String platform, String category, String hookEmotion) {
        if (!settings.isTopicBiasEnabled()) {
            return 1.0;
        }
        String plat = MarketingPopularityScorer.normalizePlatform(platform);
        List<MarketingPublicationOutcome> all = load(plat);
        if (all.isEmpty()) {
            return 1.0;
        }
        double platformMean = mean(all);
        List<MarketingPublicationOutcome> cell = all.stream()
            .filter(o -> Objects.equals(o.getCategory(), category) && Objects.equals(o.getHookEmotion(), hookEmotion))
            .toList();
        return compute(cell.size(), cell.isEmpty() ? 0.0 : mean(cell), platformMean);
    }

    /** 어드민 표용: 플래그와 무관하게 모든 셀의 현재 배수를 계산한다(배수 내림차순). */
    public List<TopicCell> cells() {
        Instant since = Instant.now(clock).minus(Duration.ofDays(WINDOW_DAYS));
        List<MarketingPublicationOutcome> all = outcomeRepository
            .findByStatusAndPublishedAtGreaterThanEqual(MarketingPublicationOutcome.SCORED, since).stream()
            .filter(o -> o.getReward() != null).toList();
        Map<String, Double> platformMean = new LinkedHashMap<>();
        all.stream().map(MarketingPublicationOutcome::getPlatform).distinct().forEach(p ->
            platformMean.put(p, mean(all.stream().filter(o -> p.equals(o.getPlatform())).toList())));
        record Key(String p, String c, String e) {}
        Map<Key, List<MarketingPublicationOutcome>> grouped = new LinkedHashMap<>();
        for (MarketingPublicationOutcome o : all) {
            grouped.computeIfAbsent(new Key(o.getPlatform(), o.getCategory(), o.getHookEmotion()),
                k -> new java.util.ArrayList<>()).add(o);
        }
        return grouped.entrySet().stream()
            .map(en -> {
                double cellMean = mean(en.getValue());
                return new TopicCell(en.getKey().p(), en.getKey().c(), en.getKey().e(), en.getValue().size(),
                    cellMean, compute(en.getValue().size(), cellMean, platformMean.get(en.getKey().p())));
            })
            .sorted(Comparator.comparingDouble(TopicCell::multiplier).reversed())
            .toList();
    }

    static double compute(int n, double cellMean, double platformMean) {
        if (platformMean <= 0) {
            return 1.0;
        }
        double m = ((n * cellMean + PRIOR_WEIGHT * platformMean) / (n + PRIOR_WEIGHT)) / platformMean;
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, m));
    }

    private List<MarketingPublicationOutcome> load(String platform) {
        Instant since = Instant.now(clock).minus(Duration.ofDays(WINDOW_DAYS));
        return outcomeRepository.findByPlatformAndStatusAndPublishedAtGreaterThanEqual(
                platform, MarketingPublicationOutcome.SCORED, since).stream()
            .filter(o -> o.getReward() != null).toList();
    }

    private static double mean(List<MarketingPublicationOutcome> rows) {
        return rows.stream().mapToDouble(MarketingPublicationOutcome::getReward).average().orElse(0.0);
    }
}
