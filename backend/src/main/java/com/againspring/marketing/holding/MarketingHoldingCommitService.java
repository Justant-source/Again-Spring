package com.againspring.marketing.holding;

import com.againspring.domain.marketing.MarketingHolding;
import com.againspring.domain.marketing.MarketingHoldingStatus;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.domain.marketing.MarketingPinFormat;
import com.againspring.marketing.MarketingJobService;
import com.againspring.marketing.MarketingPlatformAutoService;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * T+24h holding commit pipeline (distribution rule C / S4).
 *
 * <p>One COMMITTED story = one shared-pool slot. VIDEO commits get video + text
 * platforms (IG feed excluded when reels are included). TEXT commits get text only.
 * Pins soft-reserved first; autos by score into remaining video then text slots;
 * other due holdings → DROPPED. Force ignores daily caps.
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
        int autoVideoCommitted,
        int autoTextCommitted,
        int dropped,
        int pinnedDeferred
    ) {}

    public record ForceResult(
        String postId,
        MarketingHoldingStatus status,
        MarketingPublishFormat format,
        List<Long> jobIds,
        List<String> targets
    ) {}

    public record CompletedItem(
        String postId,
        MarketingHoldingStatus status,
        String pinFormat,
        Double scoreSnapshot,
        Instant lockedAt,
        Instant createdAt,
        Instant updatedAt,
        List<JobSummary> jobs
    ) {}

    /**
     * Summary of a marketing job for admin display.
     * Includes reschedule tracking to show when/why jobs were deferred.
     */
    public record JobSummary(
        Long id,
        String status,
        List<String> targets,
        Instant createdAt,
        Instant scheduledPublishAt,
        Integer rescheduledCount,
        String rescheduledReason,
        Instant originalScheduledAt
    ) {}

    /**
     * Scheduler entry: commit due pins, fill remaining slots by score, drop the rest.
     */
    @Transactional
    public CommitTickResult runCommitTick(Instant since) {
        Objects.requireNonNull(since, "since");

        MarketingQuotaService.QuotaStatus quota = quotaService.getStatus();
        MarketingScoreWeightService.Weights weights = scoreWeightService.getWeights();
        List<DueHoldingProjection> due = holdingRepository.findDueHoldings(since);
        if (due.isEmpty()) {
            log.debug("Holding commit tick: no due holdings since={}", since);
            return new CommitTickResult(0, 0, 0, 0, 0);
        }

        long remainingPool = quota.remainingPool();
        long effectiveVideoCap = computeEffectiveVideoCap(quota);
        int pinnedCommitted = 0;
        int pinnedDeferred = 0;
        int autoVideoCommitted = 0;
        int autoTextCommitted = 0;
        int dropped = 0;

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
        autos.sort(scoreThenCreatedDesc());

        Set<String> handled = new HashSet<>();
        Set<String> selected = new HashSet<>();

        // 1) Pins first (soft-reserved). Cap short → keep PINNED for next tick.
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
            MarketingPublishFormat format = MarketingPublishFormat.fromPin(pinFormat);
            boolean needsVideo = format == MarketingPublishFormat.VIDEO;
            if (remainingPool < 1 || (needsVideo && effectiveVideoCap < 1)) {
                log.info("Deferring pin {} format={} (remainingPool={}, effectiveVideoCap={})",
                    pin.postId(), format, remainingPool, effectiveVideoCap);
                pinnedDeferred++;
                continue;
            }
            selected.add(pin.postId());
            if (commitHolding(pin.postId(), format, REQUESTED_BY_SCHEDULER, false)) {
                pinnedCommitted++;
                remainingPool--;
                if (needsVideo) {
                    effectiveVideoCap--;
                }
                handled.add(pin.postId());
            } else {
                pinnedDeferred++;
            }
        }

        // Soft-reserve still-PINNED rows so autos do not steal their slots.
        MarketingHoldingService.SoftReserve stillReserved = MarketingHoldingService.softReserveFrom(
            holdingRepository.findByStatusIn(EnumSet.of(MarketingHoldingStatus.PINNED)), null);
        long autoPool = Math.max(0, remainingPool - stillReserved.reservedPool());
        long autoVideo = Math.max(0, effectiveVideoCap - stillReserved.reservedVideos());
        autoVideo = Math.min(autoVideo, autoPool);

        // 2) Autos: video slots by score, then text slots.
        for (ScoredDue auto : autos) {
            if (handled.contains(auto.postId()) || selected.contains(auto.postId())) {
                continue;
            }
            if (auto.status() == MarketingHoldingStatus.OUT_OF_CUT) {
                continue; // dropped in pass 3
            }
            if (autoVideo > 0 && autoPool > 0) {
                selected.add(auto.postId());
                if (commitHolding(auto.postId(), MarketingPublishFormat.VIDEO,
                        REQUESTED_BY_SCHEDULER, false)) {
                    autoVideoCommitted++;
                    autoVideo--;
                    autoPool--;
                    remainingPool--;
                    handled.add(auto.postId());
                }
            }
        }
        for (ScoredDue auto : autos) {
            if (handled.contains(auto.postId()) || selected.contains(auto.postId())) {
                continue;
            }
            if (auto.status() == MarketingHoldingStatus.OUT_OF_CUT) {
                continue;
            }
            if (autoPool > 0) {
                selected.add(auto.postId());
                if (commitHolding(auto.postId(), MarketingPublishFormat.TEXT,
                        REQUESTED_BY_SCHEDULER, false)) {
                    autoTextCommitted++;
                    autoPool--;
                    remainingPool--;
                    handled.add(auto.postId());
                }
            }
        }

        // 3) Remaining due non-pinned / non-selected → DROPPED
        // (pins waiting on cap, and selected-but-failed commits, stay for next tick).
        for (ScoredDue dueRow : due.stream().map(r -> toScored(r, weights)).toList()) {
            if (handled.contains(dueRow.postId()) || selected.contains(dueRow.postId())) {
                continue;
            }
            if (dueRow.status() == MarketingHoldingStatus.PINNED) {
                continue;
            }
            if (markDropped(dueRow.postId())) {
                dropped++;
            }
        }

        log.info("Holding commit tick: pinned={} autoVideo={} autoText={} dropped={} deferredPins={}",
            pinnedCommitted, autoVideoCommitted, autoTextCommitted, dropped, pinnedDeferred);
        return new CommitTickResult(
            pinnedCommitted, autoVideoCommitted, autoTextCommitted, dropped, pinnedDeferred);
    }

    /**
     * Completed-tab force: ignore daily caps; same target rules as auto commit.
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

        if (holding.getStatus() == MarketingHoldingStatus.COMMITTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Holding already COMMITTED");
        }
        if (holding.getStatus() != MarketingHoldingStatus.DROPPED
            && holding.getStatus() != MarketingHoldingStatus.OUT_OF_CUT
            && holding.getStatus() != MarketingHoldingStatus.IN_POOL
            && holding.getStatus() != MarketingHoldingStatus.PINNED) {
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

        List<CompletedItem> out = new ArrayList<>(holdings.size());
        for (MarketingHolding h : holdings) {
            List<JobSummary> jobs = jobsByPost.getOrDefault(h.getPostId(), List.of()).stream()
                .sorted(Comparator.comparing(MarketingJob::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())))
                .map(j -> new JobSummary(
                    j.getId(),
                    j.getStatus(),
                    parseTargets(j.getTargets()),
                    j.getCreatedAt(),
                    j.getScheduledPublishAt(),
                    j.getRescheduledCount(),
                    j.getRescheduledReason(),
                    j.getOriginalScheduledAt()))
                .toList();
            out.add(new CompletedItem(
                h.getPostId(),
                h.getStatus(),
                h.getPinFormat() != null ? h.getPinFormat().name() : null,
                h.getScoreSnapshot(),
                h.getLockedAt(),
                h.getCreatedAt(),
                h.getUpdatedAt(),
                jobs
            ));
        }
        return out;
    }

    /** Package-visible for tests: ASM job grouping (video dual + alone text). */
    static List<List<String>> groupTargetsIntoJobs(List<String> targets) {
        List<String> video = new ArrayList<>();
        List<List<String>> alone = new ArrayList<>();
        for (String raw : targets) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String id = raw.trim().toLowerCase(Locale.ROOT);
            if (VIDEO_PLATFORM_IDS.contains(id)) {
                video.add(id);
            } else {
                alone.add(List.of(id));
            }
        }
        List<List<String>> jobs = new ArrayList<>(alone.size() + 1);
        if (!video.isEmpty()) {
            jobs.add(List.copyOf(video));
        }
        jobs.addAll(alone);
        return jobs;
    }

    long computeEffectiveVideoCap(MarketingQuotaService.QuotaStatus quota) {
        if (!platformAutoService.hasEffectiveVideoPlatforms()) {
            return 0L;
        }
        return Math.max(0, quota.dailyVideoCap() - quota.videosToday());
    }

    private boolean commitHolding(
            String postId, MarketingPublishFormat format, String requestedBy, boolean ignoreCap) {
        MarketingHolding holding = holdingRepository.findById(postId).orElse(null);
        if (holding == null) {
            log.warn("Commit skipped — holding missing for {}", postId);
            return false;
        }
        if (holding.getStatus() == MarketingHoldingStatus.COMMITTED
            || holding.getStatus() == MarketingHoldingStatus.DROPPED) {
            return false;
        }

        List<String> targets = platformAutoService.resolveTargets(format);
        if (targets.isEmpty()) {
            log.warn("Commit skipped — empty targets for {} format={}", postId, format);
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
        log.info("COMMITTED holding {} format={} targets={} ignoreCap={}",
            postId, format, targets, ignoreCap);
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
            // All platforms already had jobs (retry path / partial) — still allow lock if any job exists
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

    private static ScoredDue toScored(DueHoldingProjection row, MarketingScoreWeightService.Weights w) {
        int views = row.getViewCount() == null ? 0 : row.getViewCount().intValue();
        long comments = row.getCommentCount() == null ? 0L : row.getCommentCount().longValue();
        long votes = row.getVoteCount() == null ? 0L : row.getVoteCount().longValue();
        double score = w.weightViews() * views + w.weightComments() * comments + w.weightVotes() * votes;
        MarketingHoldingStatus status;
        try {
            status = MarketingHoldingStatus.valueOf(row.getStatus());
        } catch (Exception e) {
            status = MarketingHoldingStatus.OUT_OF_CUT;
        }
        return new ScoredDue(
            row.getPostId(), status, row.getPinFormat(), score, row.getPostCreatedAt());
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

    private record ScoredDue(
        String postId,
        MarketingHoldingStatus status,
        String pinFormat,
        double score,
        Instant postCreatedAt
    ) {}
}
