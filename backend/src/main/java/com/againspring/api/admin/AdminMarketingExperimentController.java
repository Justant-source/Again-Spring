package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.domain.marketing.MarketingAccountStats;
import com.againspring.domain.marketing.MarketingPublicationOutcome;
import com.againspring.marketing.experiment.FormatArm;
import com.againspring.marketing.experiment.MarketingExperimentSettings;
import com.againspring.marketing.experiment.MarketingFormatArmService;
import com.againspring.marketing.experiment.MarketingOutcomeService;
import com.againspring.marketing.experiment.MarketingTopicBiasService;
import com.againspring.repository.marketing.MarketingAccountStatsRepository;
import com.againspring.repository.marketing.MarketingFormatArmRepository;
import com.againspring.repository.marketing.MarketingPublicationOutcomeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 포맷 실험 어드민 API: 설정 · arm 켜기/끄기 · 주제 배수 · 계정 지표 · 수동 채점. */
@RestController
@RequestMapping("/api/admin/marketing/experiment")
@RequiredArgsConstructor
@Tag(name = "Admin — Marketing Experiment", description = "포맷 arm 실험 · 성과 점수 · 주제 편향 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketingExperimentController {

    private final MarketingExperimentSettings settings;
    private final MarketingFormatArmService armService;
    private final MarketingFormatArmRepository armRepository;
    private final MarketingOutcomeService outcomeService;
    private final MarketingTopicBiasService topicBiasService;
    private final MarketingPublicationOutcomeRepository outcomeRepository;
    private final MarketingAccountStatsRepository accountStatsRepository;
    private final Clock clock;

    public record SettingsView(boolean enabled, boolean topicBiasEnabled, int minSamples) {}

    public record ArmView(String key, String renderProfile, String scriptStyle, int commentCount,
                          String outroMode, Integer maxDurationSec, boolean enabled, String description,
                          long assigned30d, long outcomes60d, double meanReward, long breakouts) {}

    public record AccountView(String platform, LocalDate collectedOn, Long followers) {}

    public record ExperimentView(SettingsView settings, List<ArmView> arms,
                                 List<MarketingTopicBiasService.TopicCell> topics, List<AccountView> accounts) {}

    public record SettingsRequest(Boolean enabled, Boolean topicBiasEnabled) {}

    public record ArmToggleRequest(Boolean enabled) {}

    @GetMapping({"", "/"})
    @Operation(summary = "실험 현황", description = "설정 · arm별 배정/채점/평균 점수 · 주제 배수 · 계정 지표(최근 60일)")
    public ResponseEntity<ExperimentView> overview() {
        Instant now = Instant.now(clock);
        Instant assignSince = now.minus(Duration.ofDays(MarketingFormatArmService.ASSIGN_WINDOW_DAYS));
        Instant outcomeSince = now.minus(Duration.ofDays(MarketingFormatArmService.OUTCOME_WINDOW_DAYS));
        List<MarketingPublicationOutcome> outcomes = outcomeRepository
            .findByStatusAndPublishedAtGreaterThanEqual(MarketingPublicationOutcome.SCORED, outcomeSince).stream()
            .filter(o -> o.getReward() != null).toList();

        List<ArmView> arms = armService.listAll().stream().map(a -> armView(a, outcomes, assignSince)).toList();
        LocalDate accountsSince = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(60);
        List<AccountView> accounts = accountStatsRepository
            .findByCollectedOnGreaterThanEqualOrderByCollectedOnDescPlatformAsc(accountsSince).stream()
            .map(AdminMarketingExperimentController::accountView).toList();

        return ResponseEntity.ok(new ExperimentView(
            new SettingsView(settings.isEnabled(), settings.isTopicBiasEnabled(), settings.minSamples()),
            arms, topicBiasService.cells(), accounts));
    }

    @PutMapping("/settings")
    @Auditable(action = "UPDATE_MARKETING_EXPERIMENT_SETTINGS")
    @Operation(summary = "실험·주제 편향 스위치")
    public ResponseEntity<SettingsView> updateSettings(@RequestBody SettingsRequest body) {
        if (body != null && body.enabled() != null) {
            settings.setEnabled(body.enabled());
        }
        if (body != null && body.topicBiasEnabled() != null) {
            settings.setTopicBiasEnabled(body.topicBiasEnabled());
        }
        return ResponseEntity.ok(
            new SettingsView(settings.isEnabled(), settings.isTopicBiasEnabled(), settings.minSamples()));
    }

    @PatchMapping("/arms/{key}")
    @Auditable(action = "TOGGLE_MARKETING_FORMAT_ARM")
    @Operation(summary = "arm 켜기/끄기")
    public ResponseEntity<FormatArm> toggleArm(@PathVariable String key, @RequestBody ArmToggleRequest body) {
        if (body == null || body.enabled() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "enabled is required");
        }
        return ResponseEntity.ok(armService.setEnabled(key, body.enabled()));
    }

    @PostMapping("/score/run")
    @Auditable(action = "RUN_MARKETING_OUTCOME_SCORING")
    @Operation(summary = "지금 채점", description = "아직 outcome이 없는 게시 완료 잡을 채점. created = 새로 만든 행 수")
    public ResponseEntity<Map<String, Integer>> runScoring() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("created", outcomeService.scorePending());
        return ResponseEntity.ok(out);
    }

    private ArmView armView(FormatArm a, List<MarketingPublicationOutcome> outcomes, Instant assignSince) {
        List<MarketingPublicationOutcome> mine = outcomes.stream().filter(o -> a.key().equals(o.getFormatArm())).toList();
        double mean = mine.stream().mapToDouble(MarketingPublicationOutcome::getReward).average().orElse(0.0);
        return new ArmView(a.key(), a.renderProfile(), a.scriptStyle(), a.commentCount(), a.outroMode(),
            a.maxDurationSec(), a.enabled(), a.description(),
            armRepository.countAssignedSince(a.key(), assignSince), mine.size(), mean,
            mine.stream().filter(MarketingPublicationOutcome::isBreakout).count());
    }

    private static AccountView accountView(MarketingAccountStats s) {
        return new AccountView(s.getPlatform(), s.getCollectedOn(), s.getFollowers());
    }
}
