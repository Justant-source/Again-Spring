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
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.marketing.MarketingHoldingRepository;
import com.againspring.repository.marketing.MarketingHoldingRepository.DueHoldingProjection;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final MarketingPlatformAutoService platformAutoService;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper;

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
     */
    @Transactional
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
            return new CommitTickResult(0, 0, 0, 0, Map.of());
        }

        List<ScoredDue> pins = new ArrayList<>();
        List<ScoredDue> autos = new ArrayList<>();
        for (DueHoldingProjection row : due) {
            ScoredDue scored = toScored(row, weights);
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
            if (commitHolding(postId, platforms, REQUESTED_BY_SCHEDULER)) {
                autoCommitted++;
                handled.add(postId);
            } else {
                selectedStories.remove(postId);
                assignedByPost.remove(postId);
                for (String platform : platforms) {
                    selectedCounts.merge(platform, -1, Integer::sum);
                }
            }
        }

        // 3) Remaining due non-pinned / non-selected → DROPPED
        for (ScoredDue dueRow : due.stream().map(r -> toScored(r, weights)).toList()) {
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

        log.info("Holding commit tick: pinned={} auto={} dropped={} deferredPins={} byPlatform={}",
            pinnedCommitted, autoCommitted, dropped, pinnedDeferred, selectedCounts);
        return new CommitTickResult(
            pinnedCommitted, autoCommitted, dropped, pinnedDeferred, Map.copyOf(selectedCounts));
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

        try {
            enqueueJobs(postId, targets, requestedBy);
        } catch (Exception e) {
            log.error("Failed to enqueue jobs for {}: {}", postId, e.getMessage());
            return false;
        }

        lockCommitted(holding);
        holdingRepository.save(holding);
        log.info("COMMITTED holding {} targets={}", postId, targets);
        return true;
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

    static ScoredDue toScored(
            DueHoldingProjection row, MarketingScoreWeightService.AllPlatformWeights weights) {
        MarketingPopularityScorer.Signals signals = signalsFrom(row);
        Map<String, Double> scores = new HashMap<>();
        for (String platform : MarketingPopularityScorer.RANKED_PLATFORMS) {
            scores.put(platform, MarketingPopularityScorer.score(signals, weights.forPlatform(platform)));
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
