package com.againspring.marketing.experiment;

import com.againspring.domain.marketing.MarketingFormatArmEntity;
import com.againspring.domain.marketing.MarketingPublicationOutcome;
import com.againspring.marketing.MarketingPopularityScorer;
import com.againspring.repository.marketing.MarketingFormatArmRepository;
import com.againspring.repository.marketing.MarketingPublicationOutcomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 포맷 arm 관리 + 자동 게시 잡용 arm 배정(설계 §2.3: 표본 채우기 → UCB1). */
@Service
@RequiredArgsConstructor
public class MarketingFormatArmService {

    public static final int ASSIGN_WINDOW_DAYS = 30;
    public static final int OUTCOME_WINDOW_DAYS = 60;

    private final MarketingFormatArmRepository armRepository;
    private final MarketingPublicationOutcomeRepository outcomeRepository;
    private final MarketingExperimentSettings settings;
    private final Clock clock;

    public List<FormatArm> listAll() {
        return armRepository.findAllByOrderByArmKeyAsc().stream().map(FormatArm::from).toList();
    }

    public Optional<FormatArm> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return armRepository.findById(key.trim()).map(FormatArm::from);
    }

    @Transactional
    public FormatArm setEnabled(String key, boolean enabled) {
        MarketingFormatArmEntity e = armRepository.findById(key == null ? "" : key.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown format arm: " + key));
        e.setEnabled(enabled);
        e.setUpdatedAt(Instant.now(clock));
        return FormatArm.from(armRepository.save(e));
    }

    /** 실험이 꺼져 있거나 켜진 arm이 없으면 empty. 영상 플랫폼 외에도 empty. */
    public Optional<FormatArm> pickForAutoJob(String platform) {
        if (!settings.isEnabled()) {
            return Optional.empty();
        }
        String plat = MarketingPopularityScorer.normalizePlatform(platform);
        if (!MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS.equals(plat)
            && !MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS.equals(plat)) {
            return Optional.empty();
        }
        List<FormatArm> arms = armRepository.findByEnabledTrueOrderByArmKeyAsc().stream()
            .map(FormatArm::from).toList();
        if (arms.isEmpty()) {
            return Optional.empty();
        }
        if (arms.size() == 1) {
            return Optional.of(arms.get(0));
        }

        Instant now = Instant.now(clock);
        Instant assignSince = now.minus(Duration.ofDays(ASSIGN_WINDOW_DAYS));
        Instant outcomeSince = now.minus(Duration.ofDays(OUTCOME_WINDOW_DAYS));
        int minSamples = settings.minSamples();

        // arm별 배정 수(최근 30일)
        record Stat(FormatArm arm, long assigned, int n, double mean) {}
        List<MarketingPublicationOutcome> outcomes =
            outcomeRepository.findByPlatformAndStatusAndPublishedAtGreaterThanEqual(
                plat, MarketingPublicationOutcome.SCORED, outcomeSince);
        List<Stat> stats = arms.stream().map(a -> {
            long assigned = armRepository.countAssignedSinceForPlatform(a.key(), plat, assignSince);
            var mine = outcomes.stream().filter(o -> a.key().equals(o.getFormatArm()) && o.getReward() != null).toList();
            double mean = mine.stream().mapToDouble(MarketingPublicationOutcome::getReward).average().orElse(0.0);
            return new Stat(a, assigned, mine.size(), mean);
        }).toList();

        Comparator<Stat> fewestAssigned = Comparator.comparingLong(Stat::assigned)
            .thenComparing(s -> s.arm().key());

        // 2단계: 표본이 모자란 arm 우선
        Optional<Stat> underSampled = stats.stream().filter(s -> s.assigned() < minSamples).min(fewestAssigned);
        if (underSampled.isPresent()) {
            return Optional.of(underSampled.get().arm());
        }
        // outcome이 아직 0건인 arm은 같은 규칙으로 먼저 취급
        Optional<Stat> noOutcome = stats.stream().filter(s -> s.n() == 0).min(fewestAssigned);
        if (noOutcome.isPresent()) {
            return Optional.of(noOutcome.get().arm());
        }
        // 3단계: UCB1
        double total = stats.stream().mapToInt(Stat::n).sum();
        return stats.stream()
            .max(Comparator.comparingDouble((Stat s) -> s.mean() + Math.sqrt(2 * Math.log(total) / s.n()))
                .thenComparing(Comparator.comparing((Stat s) -> s.arm().key()).reversed()))
            .map(Stat::arm);
    }
}
