package com.againspring.marketing.holding;

import com.againspring.domain.community.Post;
import com.againspring.domain.marketing.MarketingHolding;
import com.againspring.domain.marketing.MarketingHoldingStatus;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.domain.marketing.MarketingPinFormat;
import com.againspring.marketing.MarketingJobService;
import com.againspring.marketing.MarketingPlatformAutoService;
import com.againspring.marketing.MarketingPlatformSelector;
import com.againspring.marketing.MarketingPopularityScorer;
import com.againspring.marketing.MarketingPublishFormat;
import com.againspring.marketing.MarketingQuotaService;
import com.againspring.marketing.MarketingScoreWeightService;
import com.againspring.marketing.MarketingThemeBoostService;
import com.againspring.repository.community.PostRepository;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.marketing.MarketingHoldingRepository;
import com.againspring.repository.marketing.MarketingHoldingRepository.DueHoldingProjection;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * T+24h holding commit pipeline (Phase 2.1–2.2).
 *
 * <p>Per-platform popularity scores + per-platform daily caps. Same story may win
 * multiple platforms the same day. Instagram feed ⊥ Reels exclusivity
 * (higher score wins; tie → Reels). Reels and Shorts enqueue as <b>separate</b> jobs.
 *
 * <p>The tick itself is not one transaction. Each holding commit/drop runs in
 * {@code REQUIRES_NEW} so a job-insert failure cannot mark the whole tick
 * rollback-only or drop a platform-selected story.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingHoldingCommitService {

    public static final String REQUESTED_BY_SCHEDULER = "system:holding-commit-trigger";
    public static final String REQUESTED_BY_FORCE_PREFIX = "admin:force:";

    private static final Set<String> VIDEO_PLATFORM_IDS = Set.of(
        "instagram_reels", "youtube_shorts", "naver_clip");

    private final MarketingHoldingRepository holdingRepository;
    private final MarketingJobRepository marketingJobRepository;
    private final MarketingJobService marketingJobService;
    private final MarketingQuotaService quotaService;
    private final MarketingScoreWeightService scoreWeightService;
    private final MarketingThemeBoostService themeBoostService;
    private final MarketingPlatformAutoService platformAutoService;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final TelegramNotifier telegramNotifier;

    public enum ForceMode {
        VIDEO_AND_TEXT,
        TEXT_ONLY;

        public static ForceMode from(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode is required");
            }
            try {
                return ForceMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "mode must be VIDEO_AND_TEXT or TEXT_ONLY");
            }
        }

        public MarketingPublishFormat toPublishFormat() {
            return this == VIDEO_AND_TEXT
                ? MarketingPublishFormat.VIDEO
                : MarketingPublishFormat.TEXT;
        }
    }

    public record CommitTickResult(
        int pinnedCommitted,
        int autoCommitted,
        int dropped,
        int pinnedDeferred,
        int autoDeferred,
        Map<String, Integer> selectedByPlatform
    ) {
        /** Phase 1 field aliases for older callers/tests. */
        public int autoVideoCommitted() {
            int n = 0;
            if (selectedByPlatform != null) {
                n += selectedByPlatform.getOrDefault(
                    MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS, 0);
                n += selectedByPlatform.getOrDefault(
                    MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS, 0);
            }
            return n;
        }

        public int autoTextCommitted() {
            int n = 0;
            if (selectedByPlatform != null) {
                n += selectedByPlatform.getOrDefault(
                    MarketingPopularityScorer.PLATFORM_X_THREAD, 0);
                n += selectedByPlatform.getOrDefault(
                    MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED, 0);
            }
            return n;
        }
    }

    public record ForceResult(
        String postId,
        MarketingHoldingStatus status,
        MarketingPublishFormat format,
        List<Long> jobIds,
        List<String> targets
    ) {}

    public record CompletedItem(
        String postId,
        String title,
        MarketingHoldingStatus status,
        String pinFormat,
        String committedFormat,
        Double scoreSnapshot,
        Map<String, Integer> platformRankSnapshot,
        Instant lockedAt,
        Instant createdAt,
        Instant updatedAt,
        List<JobSummary> jobs
    ) {}

    public record JobSummary(
        Long id,
        String status,
        List<String> targets,
        List<PublicationSummary> publications,
        Instant createdAt,
        Instant scheduledPublishAt,
        Integer rescheduledCount,
        String rescheduledReason,
        Instant originalScheduledAt
    ) {}

    public record PublicationSummary(
        String platform,
        String state,
        String url
    ) {}

    /**
     * Scheduler entry: pins first, then independent per-platform auto fill, drop rest.
     * Orchestration only — per-story writes use isolated transactions.
     */
    public CommitTickResult runCommitTick(Instant since) {
        Objects.requireNonNull(since, "since");

        MarketingScoreWeightService.AllPlatformWeights weights = scoreWeightService.getPlatformWeights();
        List<String> enabled = platformAutoService.listEnabledPlatforms().stream()
            .map(MarketingPopularityScorer::normalizePlatform)
            .filter(MarketingPopularityScorer::isRankedPlatform)
            .filter(id -> MarketingPlatformAutoService.RUNTIME_SUPPORTED.contains(id))
            .toList();

        Map<String, Integer> remaining = quotaService.remainingCapsMutable();
        for (String platform : MarketingPopularityScorer.RANKED_PLATFORMS) {
            if (!enabled.contains(platform)) {
                remaining.put(platform, 0);
            }
        }

        List<DueHoldingProjection> due = holdingRepository.findDueHoldings(since);
        if (due.isEmpty()) {
            log.debug("Holding commit tick: no due holdings since={}", since);
            return new CommitTickResult(0, 0, 0, 0, 0, Map.of());
        }

        Map<String, Post> postsById = postRepository.findAllById(
                due.stream().map(DueHoldingProjection::getPostId).toList()).stream()
            .collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));
        // Shadow = store boosts but do not apply to allocation (plan §4.3 Sprint 3.2).
        boolean themeShadow = themeBoostService.isShadow();

        List<ScoredDue> pins = new ArrayList<>();
        List<ScoredDue> autos = new ArrayList<>();
        for (DueHoldingProjection row : due) {
            Post post = postsById.get(row.getPostId());
            ScoredDue scored = toScored(row, weights, themeKeys(post), themeShadow);
            if (MarketingHoldingStatus.PINNED.name().equals(row.getStatus())) {
                pins.add(scored);
            } else {
                autos.add(scored);
            }
        }
        pins.sort(scoreThenCreatedDesc());

        Set<String> handled = new HashSet<>();
        Set<String> selectedStories = new HashSet<>();
        Map<String, List<String>> assignedByPost = new LinkedHashMap<>();
        Map<String, Integer> selectedCounts = new LinkedHashMap<>();
        for (String p : MarketingPopularityScorer.RANKED_PLATFORMS) {
            selectedCounts.put(p, 0);
        }

        int pinnedCommitted = 0;
        int pinnedDeferred = 0;
        int autoDeferred = 0;
        int dropped = 0;

        // Soft-reserve still-PINNED so autos do not steal (after this tick's pin pass).
        // 1) Pins first
        for (ScoredDue pin : pins) {
            MarketingPinFormat pinFormat = parsePinFormat(pin.pinFormat());
            if (pinFormat == null) {
                log.warn("Due PINNED holding {} missing pinFormat — dropping", pin.postId());
                if (markDropped(pin.postId())) {
                    dropped++;
                    handled.add(pin.postId());
                }
                continue;
            }
            List<String> want = resolvePinPlatforms(pinFormat, enabled, pin.scores());
            List<String> available = want.stream()
                .filter(p -> remaining.getOrDefault(p, 0) > 0)
                .toList();
            if (available.isEmpty()) {
                log.info("Deferring pin {} — no remaining caps among {}", pin.postId(), want);
                pinnedDeferred++;
                continue;
            }
            for (String platform : available) {
                remaining.put(platform, remaining.get(platform) - 1);
                selectedCounts.merge(platform, 1, Integer::sum);
            }
            assignedByPost.put(pin.postId(), available);
            selectedStories.add(pin.postId());
            if (commitHolding(pin.postId(), available, REQUESTED_BY_SCHEDULER)) {
                pinnedCommitted++;
                handled.add(pin.postId());
            } else {
                // roll back remaining so autos can use slots
                for (String platform : available) {
                    remaining.merge(platform, 1, Integer::sum);
                    selectedCounts.merge(platform, -1, Integer::sum);
                }
                assignedByPost.remove(pin.postId());
                selectedStories.remove(pin.postId());
                pinnedDeferred++;
            }
        }

        // Soft-reserve remaining pins (deferred) from auto pool
        List<MarketingHolding> stillPinned = holdingRepository.findByStatusIn(
            EnumSet.of(MarketingHoldingStatus.PINNED));
        for (MarketingHolding h : stillPinned) {
            if (handled.contains(h.getPostId()) || selectedStories.contains(h.getPostId())) {
                continue;
            }
            MarketingPinFormat pf = h.getPinFormat();
            if (pf == null) {
                continue;
            }
            // Build zero scores for exclusivity on reserved pins — use pin format only
            List<String> reserved = resolvePinPlatforms(pf, enabled, Map.of(
                MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED, 0.0,
                MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS, 1.0));
            for (String platform : reserved) {
                int rem = remaining.getOrDefault(platform, 0);
                if (rem > 0) {
                    remaining.put(platform, rem - 1);
                }
            }
        }

        // 2) Autos — independent per-platform ranking + IG exclusivity
        List<MarketingPlatformSelector.Candidate> autoCandidates = new ArrayList<>();
        for (ScoredDue auto : autos) {
            if (handled.contains(auto.postId()) || selectedStories.contains(auto.postId())) {
                continue;
            }
            autoCandidates.add(new MarketingPlatformSelector.Candidate(
                auto.postId(), auto.postCreatedAt(), auto.scores()));
        }

        Map<String, List<String>> autoByPlatform =
            MarketingPlatformSelector.selectAutos(autoCandidates, remaining);
        Map<String, List<String>> autoByPost = MarketingPlatformSelector.invertSelections(autoByPlatform);
        Map<String, Map<String, Integer>> platformRanks = platformRanks(autoCandidates);

        int autoCommitted = 0;
        for (Map.Entry<String, List<String>> e : autoByPost.entrySet()) {
            String postId = e.getKey();
            List<String> platforms = e.getValue();
            if (platforms.isEmpty()) {
                continue;
            }
            selectedStories.add(postId);
            assignedByPost.put(postId, platforms);
            for (String platform : platforms) {
                selectedCounts.merge(platform, 1, Integer::sum);
            }
            if (commitHolding(postId, platforms, REQUESTED_BY_SCHEDULER,
                    selectedPlatformRanks(platformRanks.get(postId), platforms))) {
                autoCommitted++;
                handled.add(postId);
            } else {
                // Keep in selectedStories so the drop pass cannot DROPPED a selected candidate.
                // Roll back in-memory caps so later autos in this tick can use the slots.
                assignedByPost.remove(postId);
                for (String platform : platforms) {
                    remaining.merge(platform, 1, Integer::sum);
                    selectedCounts.merge(platform, -1, Integer::sum);
                }
                autoDeferred++;
            }
        }

        // 3) Remaining due non-pinned / non-selected → DROPPED
        List<ScoredDue> allScored = new ArrayList<>(pins.size() + autos.size());
        allScored.addAll(pins);
        allScored.addAll(autos);
        for (ScoredDue dueRow : allScored) {
            if (handled.contains(dueRow.postId()) || selectedStories.contains(dueRow.postId())) {
                continue;
            }
            if (dueRow.status() == MarketingHoldingStatus.PINNED) {
                continue;
            }
            if (markDropped(dueRow.postId())) {
                dropped++;
            }
        }

        log.info("Holding commit tick: pinned={} auto={} dropped={} deferredPins={} deferredAutos={} byPlatform={}",
            pinnedCommitted, autoCommitted, dropped, pinnedDeferred, autoDeferred, selectedCounts);
        return new CommitTickResult(
            pinnedCommitted, autoCommitted, dropped, pinnedDeferred, autoDeferred, Map.copyOf(selectedCounts));
    }

    /**
     * Completed-tab force: ignore daily caps; same target rules as Phase 1 resolveTargets.
     * Jobs are created per-platform (Reels/Shorts separate).
     */
    @Transactional
    public ForceResult forceCommit(String postId, ForceMode mode, String requestedBy) {
        if (postId == null || postId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "postId is required");
        }
        Objects.requireNonNull(mode, "mode");

        MarketingHolding holding = holdingRepository.findById(postId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Holding not found: " + postId));

        if (holding.getStatus() != MarketingHoldingStatus.DROPPED
            && holding.getStatus() != MarketingHoldingStatus.OUT_OF_CUT
            && holding.getStatus() != MarketingHoldingStatus.IN_POOL
            && holding.getStatus() != MarketingHoldingStatus.PINNED
            && holding.getStatus() != MarketingHoldingStatus.COMMITTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot force holding in status " + holding.getStatus());
        }

        if (!postRepository.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + postId);
        }

        MarketingPublishFormat format = mode.toPublishFormat();
        String by = requestedBy != null && !requestedBy.isBlank()
            ? REQUESTED_BY_FORCE_PREFIX + requestedBy
            : REQUESTED_BY_FORCE_PREFIX + "admin";

        List<String> targets = platformAutoService.resolveTargets(format);
        if (targets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No enabled∩supported platforms for mode " + mode);
        }

        List<MarketingJob> jobs = enqueueJobs(postId, targets, by);
        if (jobs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No new jobs to enqueue — all target platforms already have marketing jobs for this post");
        }
        lockCommitted(holding);
        holdingRepository.save(holding);

        return new ForceResult(
            postId,
            MarketingHoldingStatus.COMMITTED,
            format,
            jobs.stream().map(MarketingJob::getId).toList(),
            targets
        );
    }

    @Transactional(readOnly = true)
    public List<CompletedItem> listCompleted(MarketingHoldingStatus statusFilter, int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        Collection<MarketingHoldingStatus> statuses = statusFilter != null
            ? EnumSet.of(statusFilter)
            : EnumSet.of(MarketingHoldingStatus.COMMITTED, MarketingHoldingStatus.DROPPED);

        List<MarketingHolding> holdings = holdingRepository.findByStatusIn(statuses).stream()
            .sorted(Comparator
                .comparing(MarketingHolding::getUpdatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MarketingHolding::getPostId))
            .limit(capped)
            .toList();

        Set<String> postIds = holdings.stream()
            .map(MarketingHolding::getPostId)
            .collect(Collectors.toCollection(HashSet::new));
        Map<String, List<MarketingJob>> jobsByPost = postIds.isEmpty()
            ? Map.of()
            : marketingJobRepository.findByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(MarketingJob::getPostId, LinkedHashMap::new, Collectors.toList()));
        Map<String, Post> postsById = postIds.isEmpty()
            ? Map.of()
            : postRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));

        List<CompletedItem> out = new ArrayList<>(holdings.size());
        for (MarketingHolding h : holdings) {
            List<MarketingJob> holdingJobs = jobsByPost.getOrDefault(h.getPostId(), List.of());
            List<JobSummary> jobs = holdingJobs.stream()
                .sorted(Comparator.comparing(MarketingJob::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())))
                .map(j -> new JobSummary(
                    j.getId(),
                    j.getStatus(),
                    parseTargets(j.getTargets()),
                    parsePublications(j.getPublications()),
                    j.getCreatedAt(),
                    j.getScheduledPublishAt(),
                    j.getRescheduledCount(),
                    j.getRescheduledReason(),
                    j.getOriginalScheduledAt()))
                .toList();
            out.add(new CompletedItem(
                h.getPostId(),
                resolveTitle(h, postsById.get(h.getPostId())),
                h.getStatus(),
                h.getPinFormat() != null ? h.getPinFormat().name() : null,
                resolveCommittedFormat(h, holdingJobs),
                h.getScoreSnapshot(),
                parsePlatformRanks(h.getPlatformRankSnapshot()),
                h.getLockedAt(),
                h.getCreatedAt(),
                h.getUpdatedAt(),
                jobs
            ));
        }
        return out;
    }

    /**
     * Phase 2: each platform is its own job (Reels ≠ Shorts) for unique renders later.
     */
    static List<List<String>> groupTargetsIntoJobs(List<String> targets) {
        List<List<String>> jobs = new ArrayList<>();
        for (String raw : targets) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String id = raw.trim().toLowerCase(Locale.ROOT);
            jobs.add(List.of(id));
        }
        return jobs;
    }

    /**
     * Platforms a pin should consume. VIDEO → enabled video + text with IG exclusivity
     * for this story (reels preferred on tie / when VIDEO pin). TEXT → text only.
     */
    static List<String> resolvePinPlatforms(
            MarketingPinFormat pinFormat,
            List<String> enabled,
            Map<String, Double> scores) {
        Set<String> enabledSet = new HashSet<>(enabled);
        List<String> out = new ArrayList<>();
        if (pinFormat == MarketingPinFormat.VIDEO) {
            for (String v : List.of(
                MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS,
                MarketingPopularityScorer.PLATFORM_YOUTUBE_SHORTS)) {
                if (enabledSet.contains(v)) {
                    out.add(v);
                }
            }
            for (String t : List.of(
                MarketingPopularityScorer.PLATFORM_X_THREAD,
                MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED)) {
                if (enabledSet.contains(t)) {
                    out.add(t);
                }
            }
        } else {
            for (String t : List.of(
                MarketingPopularityScorer.PLATFORM_X_THREAD,
                MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED)) {
                if (enabledSet.contains(t)) {
                    out.add(t);
                }
            }
        }
        // IG exclusivity for this single story
        boolean hasFeed = out.contains(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED);
        boolean hasReels = out.contains(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS);
        if (hasFeed && hasReels) {
            double sf = scores.getOrDefault(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED, 0.0);
            double sr = scores.getOrDefault(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS, 0.0);
            // VIDEO pin: prefer reels on tie (same as global rule)
            String winner = MarketingPopularityScorer.resolveIgExclusiveWinner(sf, sr);
            if (MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS.equals(winner)) {
                out.remove(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED);
            } else {
                out.remove(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS);
            }
        }
        return List.copyOf(out);
    }

    private boolean commitHolding(String postId, List<String> targets, String requestedBy) {
        return commitHolding(postId, targets, requestedBy, Map.of());
    }

    /**
     * Isolated write: job insert + COMMITTED. Failures roll back only this story and
     * return false so the tick can defer (not drop) and continue.
     */
    private boolean commitHolding(
            String postId, List<String> targets, String requestedBy,
            Map<String, Integer> platformRanks) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            Boolean ok = tx.execute(status -> doCommitHolding(postId, targets, requestedBy, platformRanks));
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException e) {
            log.error("Failed to enqueue jobs for {}: {}", postId, e.getMessage());
            notifyCommitFailure(postId, targets, e);
            return false;
        }
    }

    private boolean doCommitHolding(
            String postId, List<String> targets, String requestedBy,
            Map<String, Integer> platformRanks) {
        MarketingHolding holding = holdingRepository.findById(postId).orElse(null);
        if (holding == null) {
            log.warn("Commit skipped — holding missing for {}", postId);
            return false;
        }
        if (holding.getStatus() == MarketingHoldingStatus.COMMITTED
            || holding.getStatus() == MarketingHoldingStatus.DROPPED) {
            return false;
        }
        if (targets == null || targets.isEmpty()) {
            log.warn("Commit skipped — empty targets for {}", postId);
            return false;
        }

        enqueueJobs(postId, targets, requestedBy);

        lockCommitted(holding);
        if (platformRanks != null && !platformRanks.isEmpty()) {
            holding.setPlatformRankSnapshot(serializePlatformRanks(platformRanks));
        }
        holdingRepository.save(holding);
        log.info("COMMITTED holding {} targets={} platformRanks={}", postId, targets, platformRanks);
        return true;
    }

    private void notifyCommitFailure(String postId, List<String> targets, Exception e) {
        String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        telegramNotifier.send(String.format(
            "⚠️ [Again-Spring] 마케팅 확정 잡 생성 실패%n"
                + "사연: %s%n"
                + "플랫폼: %s%n"
                + "원인: %s%n"
                + "조치: 홀딩 유지 · 다음 틱에서 재시도 (탈락 없음)",
            postId, targets, cause));
    }

    private List<MarketingJob> enqueueJobs(String postId, List<String> targets, String requestedBy) {
        List<List<String>> groups = groupTargetsIntoJobs(targets);
        List<MarketingJob> created = new ArrayList<>(groups.size());
        for (List<String> group : groups) {
            boolean anyActive = false;
            for (String platform : group) {
                if (marketingJobRepository.countActivePlatformJobs(postId, platform) > 0
                    || marketingJobRepository.countAnyPlatformJobs(postId, platform) > 0) {
                    anyActive = true;
                    break;
                }
            }
            if (anyActive) {
                log.debug("Skipping job group {} for {} — platform already attempted", group, postId);
                continue;
            }
            MarketingJob job = marketingJobService.createJob(postId, group, true, requestedBy);
            created.add(job);
        }
        if (created.isEmpty() && !groups.isEmpty()) {
            long any = groups.stream()
                .flatMap(List::stream)
                .mapToLong(p -> marketingJobRepository.countAnyPlatformJobs(postId, p))
                .sum();
            if (any <= 0) {
                throw new IllegalStateException("No marketing jobs created for " + postId);
            }
        }
        return created;
    }

    private void lockCommitted(MarketingHolding holding) {
        holding.setStatus(MarketingHoldingStatus.COMMITTED);
        holding.setLockedAt(Instant.now());
        holding.setPinFormat(null);
    }

    private boolean markDropped(String postId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            Boolean ok = tx.execute(status -> doMarkDropped(postId));
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException e) {
            log.error("Failed to mark DROPPED for {}: {}", postId, e.getMessage());
            return false;
        }
    }

    private boolean doMarkDropped(String postId) {
        MarketingHolding holding = holdingRepository.findById(postId).orElse(null);
        if (holding == null) {
            return false;
        }
        if (holding.getStatus() == MarketingHoldingStatus.COMMITTED
            || holding.getStatus() == MarketingHoldingStatus.DROPPED
            || holding.getStatus() == MarketingHoldingStatus.PINNED) {
            return false;
        }
        holding.setStatus(MarketingHoldingStatus.DROPPED);
        holdingRepository.save(holding);
        return true;
    }

    /**
     * Per-platform {@code finalScore = featureScore × themeBoost}.
     * When {@code themeShadow} is true, boost is forced to 1.0 (allocation unchanged).
     */
    ScoredDue toScored(
            DueHoldingProjection row,
            MarketingScoreWeightService.AllPlatformWeights weights,
            ThemeKeys themeKeys,
            boolean themeShadow) {
        MarketingPopularityScorer.Signals signals = signalsFrom(row);
        String emotion = themeKeys != null ? themeKeys.hookEmotion() : null;
        String category = themeKeys != null ? themeKeys.category() : null;
        Map<String, Double> scores = new HashMap<>();
        for (String platform : MarketingPopularityScorer.RANKED_PLATFORMS) {
            double feature = MarketingPopularityScorer.score(signals, weights.forPlatform(platform));
            double boost = themeShadow
                ? 1.0
                : themeBoostService.getBoost(platform, emotion, category);
            scores.put(platform, MarketingPopularityScorer.applyThemeBoost(feature, boost));
        }
        MarketingHoldingStatus status;
        try {
            status = MarketingHoldingStatus.valueOf(row.getStatus());
        } catch (Exception e) {
            status = MarketingHoldingStatus.OUT_OF_CUT;
        }
        // Board-facing snapshot = max platform score
        double snapshot = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return new ScoredDue(
            row.getPostId(), status, row.getPinFormat(), snapshot, scores, row.getPostCreatedAt());
    }

    /** Emotion/category pair for theme boost lookup (nulls → boost 1.0 via service). */
    record ThemeKeys(String hookEmotion, String category) {}

    static ThemeKeys themeKeys(Post post) {
        if (post == null) {
            return new ThemeKeys(null, null);
        }
        String category = post.getCategory() != null ? post.getCategory().name() : null;
        return new ThemeKeys(post.getHookEmotion(), category);
    }

    static MarketingPopularityScorer.Signals signalsFrom(DueHoldingProjection row) {
        long views = row.getViewCount() == null ? 0L : row.getViewCount().longValue();
        long comments = row.getCommentCount() == null ? 0L : row.getCommentCount().longValue();
        long votes = row.getVoteCount() == null ? 0L : row.getVoteCount().longValue();
        long authorVotes = row.getAuthorVoteCount() == null ? 0L : row.getAuthorVoteCount().longValue();
        double hasPartner = row.getHasPartner() != null && row.getHasPartner().intValue() != 0 ? 1.0 : 0.0;
        double hook = MarketingPopularityScorer.hookStrength(row.getHookText());
        double skew = MarketingPopularityScorer.voteSkew(authorVotes, votes);
        return new MarketingPopularityScorer.Signals(views, comments, votes, skew, hasPartner, hook);
    }

    private static Comparator<ScoredDue> scoreThenCreatedDesc() {
        return Comparator
            .comparingDouble(ScoredDue::score).reversed()
            .thenComparing(ScoredDue::postCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    /** Rank every auto candidate independently for each platform, before cap/IG backfill. */
    private static Map<String, Map<String, Integer>> platformRanks(
            List<MarketingPlatformSelector.Candidate> candidates) {
        Map<String, Map<String, Integer>> ranks = new HashMap<>();
        for (String platform : MarketingPopularityScorer.RANKED_PLATFORMS) {
            List<MarketingPlatformSelector.Candidate> ranked = new ArrayList<>(candidates);
            ranked.sort(Comparator
                .comparingDouble((MarketingPlatformSelector.Candidate c) -> c.scoreOf(platform)).reversed()
                .thenComparing(MarketingPlatformSelector.Candidate::createdAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MarketingPlatformSelector.Candidate::postId));
            for (int i = 0; i < ranked.size(); i++) {
                ranks.computeIfAbsent(ranked.get(i).postId(), ignored -> new LinkedHashMap<>())
                    .put(platform, i + 1);
            }
        }
        return ranks;
    }

    private static Map<String, Integer> selectedPlatformRanks(
            Map<String, Integer> ranks, List<String> targets) {
        if (ranks == null || targets == null || targets.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> selected = new LinkedHashMap<>();
        for (String platform : targets) {
            Integer rank = ranks.get(platform);
            if (rank != null) {
                selected.put(platform, rank);
            }
        }
        return Map.copyOf(selected);
    }

    private String serializePlatformRanks(Map<String, Integer> ranks) {
        try {
            return objectMapper.writeValueAsString(ranks);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize marketing platform ranks", e);
        }
    }

    private Map<String, Integer> parsePlatformRanks(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Ignoring invalid platform rank snapshot: {}", e.getMessage());
            return Map.of();
        }
    }

    private static MarketingPinFormat parsePinFormat(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MarketingPinFormat.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<String> parseTargets(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<PublicationSummary> parsePublications(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            List<PublicationSummary> out = new ArrayList<>(raw.size());
            for (Map<String, Object> pub : raw) {
                Object platform = pub.get("platform");
                Object state = pub.get("state");
                Object url = pub.get("url");
                out.add(new PublicationSummary(
                    platform != null ? platform.toString() : null,
                    state != null ? state.toString() : null,
                    url != null ? url.toString() : null));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String resolveTitle(MarketingHolding h, Post post) {
        String draftTitle = parseDraftTitle(h.getDraftJson());
        if (draftTitle != null && !draftTitle.isBlank()) {
            return draftTitle;
        }
        if (post == null) {
            return null;
        }
        String title = post.getTitle();
        if (title != null && !title.isBlank()) {
            return title;
        }
        return post.getUserTitle();
    }

    private String parseDraftTitle(String draftJson) {
        if (draftJson == null || draftJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> draft = objectMapper.readValue(draftJson, new TypeReference<>() {});
            Object title = draft.get("title");
            return title != null ? title.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveCommittedFormat(MarketingHolding h, List<MarketingJob> jobs) {
        boolean hasVideoTarget = false;
        boolean hasAnyTarget = false;
        for (MarketingJob j : jobs) {
            for (String target : parseTargets(j.getTargets())) {
                hasAnyTarget = true;
                if (VIDEO_PLATFORM_IDS.contains(target.trim().toLowerCase(Locale.ROOT))) {
                    hasVideoTarget = true;
                }
            }
        }
        if (h.getPinFormat() == MarketingPinFormat.VIDEO || hasVideoTarget) {
            return MarketingPublishFormat.VIDEO.name();
        }
        if (h.getPinFormat() == MarketingPinFormat.TEXT || hasAnyTarget) {
            return MarketingPublishFormat.TEXT.name();
        }
        return null;
    }

    record ScoredDue(
        String postId,
        MarketingHoldingStatus status,
        String pinFormat,
        double score,
        Map<String, Double> scores,
        Instant postCreatedAt
    ) {}
}
