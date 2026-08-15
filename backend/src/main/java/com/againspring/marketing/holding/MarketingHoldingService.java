package com.againspring.marketing.holding;

import com.againspring.domain.community.Post;
import com.againspring.domain.marketing.MarketingHolding;
import com.againspring.domain.marketing.MarketingHoldingStatus;
import com.againspring.domain.marketing.MarketingPinFormat;
import com.againspring.marketing.MarketingQuotaService;
import com.againspring.marketing.MarketingScoreWeightService;
import com.againspring.marketing.dto.CreateJobRequest.BriefDto;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.marketing.MarketingHoldingRepository;
import com.againspring.repository.marketing.MarketingHoldingRepository.HoldingCandidateProjection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Waiting-board seed + draft + pin/soft-reserve API for marketing holdings (S2/S3).
 * T+24h commit/force lives in {@link MarketingHoldingCommitService} (S4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingHoldingService {

    public static final int BOARD_DISPLAY_LIMIT = 20;
    private static final int BOARD_REFRESH_MAX_ATTEMPTS = 3;

    private final MarketingHoldingRepository holdingRepository;
    private final PostRepository postRepository;
    private final MarketingQuotaService quotaService;
    private final MarketingScoreWeightService scoreWeightService;
    private final MarketingHoldingBriefSeeder briefSeeder;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    /** Serializes board refresh so concurrent admin polls cannot trip MariaDB 1020. */
    private final Object boardRefreshLock = new Object();

    public record RankedCandidate(
        String postId,
        double score,
        int views,
        long comments,
        long votes,
        Instant createdAt,
        int rank
    ) {}

    public record SoftReserve(
        int reservedPool,
        int reservedVideos
    ) {}

    public record BoardMeta(
        long remainingPool,
        int cutlineN,
        int dailyTextCap,
        int dailyVideoCap,
        long videosToday,
        long textsToday,
        double weightViews,
        double weightComments,
        double weightVotes
    ) {}

    public record BoardItem(
        String postId,
        String title,
        MarketingHoldingStatus status,
        String pinFormat,
        Double scoreSnapshot,
        Integer rankSnapshot,
        Map<String, Integer> platformRankSnapshot,
        String projectedFormat,
        Instant postCreatedAt,
        Instant lockedAt,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> draft
    ) {}

    public record HoldingBoard(
        List<BoardItem> items,
        BoardMeta meta
    ) {}

    /**
     * Waiting-board snapshot. Concurrent GET /holding (page load + 45s poll / Strict Mode)
     * previously raced on {@code saveAll} and raised MariaDB 1020
     * ("Record has changed since last read"), which surfaced as a board load failure.
     * Refresh is single-flight + retried inside a transaction that commits under the lock.
     */
    public HoldingBoard getBoard() {
        synchronized (boardRefreshLock) {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            RuntimeException last = null;
            for (int attempt = 1; attempt <= BOARD_REFRESH_MAX_ATTEMPTS; attempt++) {
                try {
                    return tx.execute(status -> refreshBoard());
                } catch (RuntimeException ex) {
                    last = ex;
                    if (!isMariaRecordChanged(ex) || attempt >= BOARD_REFRESH_MAX_ATTEMPTS) {
                        throw ex;
                    }
                    log.warn("Holding board refresh conflict (attempt {}/{}): {}",
                        attempt, BOARD_REFRESH_MAX_ATTEMPTS, rootMessage(ex));
                }
            }
            throw last != null ? last : new IllegalStateException("holding board refresh failed");
        }
    }

    private HoldingBoard refreshBoard() {
        MarketingQuotaService.QuotaStatus quota = quotaService.getStatus();
        MarketingScoreWeightService.Weights weights = scoreWeightService.getWeights();

        List<MarketingHolding> pinnedRows = holdingRepository.findByStatusIn(
            EnumSet.of(MarketingHoldingStatus.PINNED));
        SoftReserve reserve = softReserveFrom(pinnedRows, null);
        Set<String> pinnedIds = pinnedRows.stream()
            .filter(h -> h.getStatus() == MarketingHoldingStatus.PINNED)
            .map(MarketingHolding::getPostId)
            .collect(Collectors.toCollection(HashSet::new));

        int cutlineN = effectiveCutline(quota.remainingPool(), reserve.reservedPool());
        long videoSlots = effectiveVideoSlots(quota, reserve, cutlineN);

        List<RankedCandidate> ranked = rankCandidates(weights);
        Map<String, Integer> autoRanks = autoRankIndex(ranked, pinnedIds);
        List<RankedCandidate> display = ranked.stream()
            .limit(BOARD_DISPLAY_LIMIT)
            .toList();

        Set<String> displayIds = display.stream()
            .map(RankedCandidate::postId)
            .collect(Collectors.toCollection(HashSet::new));

        Map<String, Post> postsById = postRepository.findAllById(displayIds).stream()
            .collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));

        Map<String, MarketingHolding> existingById = holdingRepository.findByPostIdIn(displayIds)
            .stream()
            .collect(Collectors.toMap(MarketingHolding::getPostId, h -> h, (a, b) -> a));

        List<MarketingHolding> toSave = new ArrayList<>();
        List<BoardItem> items = new ArrayList<>();
        int autoInCut = 0;

        for (RankedCandidate c : display) {
            Post post = postsById.get(c.postId());
            MarketingHolding holding = existingById.get(c.postId());
            boolean isNew = holding == null;
            boolean dirty = isNew;

            if (isNew) {
                holding = MarketingHolding.builder()
                    .postId(c.postId())
                    .status(MarketingHoldingStatus.IN_POOL)
                    .draftJson(serializeDraft(briefSeeder.seedFromPost(
                        Objects.requireNonNull(post, "post missing for " + c.postId()))))
                    .build();
            }

            if (!Objects.equals(holding.getScoreSnapshot(), c.score())) {
                holding.setScoreSnapshot(c.score());
                dirty = true;
            }
            if (!Objects.equals(holding.getRankSnapshot(), c.rank())) {
                holding.setRankSnapshot(c.rank());
                dirty = true;
            }

            if (holding.getStatus() != MarketingHoldingStatus.PINNED
                && holding.getStatus() != MarketingHoldingStatus.COMMITTED
                && holding.getStatus() != MarketingHoldingStatus.DROPPED) {
                int autoRank = autoRanks.getOrDefault(c.postId(), Integer.MAX_VALUE);
                MarketingHoldingStatus next = autoRank <= cutlineN
                    ? MarketingHoldingStatus.IN_POOL
                    : (holding.getStatus() == MarketingHoldingStatus.IN_POOL || isNew
                        ? MarketingHoldingStatus.OUT_OF_CUT
                        : holding.getStatus());
                // First seed is IN_POOL then demote when outside cutline; also demote prior IN_POOL.
                // OUT_OF_CUT outside cutline stays OUT_OF_CUT (draft kept)
                if (holding.getStatus() != next) {
                    holding.setStatus(next);
                    dirty = true;
                }
            }

            if (dirty) {
                toSave.add(holding);
            }

            String projected;
            if (holding.getStatus() == MarketingHoldingStatus.PINNED
                && holding.getPinFormat() != null) {
                projected = holding.getPinFormat().name();
            } else {
                int autoRank = autoRanks.getOrDefault(c.postId(), Integer.MAX_VALUE);
                if (autoRank > cutlineN || cutlineN <= 0) {
                    projected = "OUT_OF_CUT";
                } else {
                    autoInCut++;
                    projected = projectedFormat(autoInCut, cutlineN, videoSlots);
                }
            }
            items.add(toBoardItem(holding, post, projected));
        }

        demoteOutsideDisplay(displayIds, toSave);
        if (!toSave.isEmpty()) {
            holdingRepository.saveAll(toSave);
        }

        BoardMeta meta = new BoardMeta(
            quota.remainingPool(),
            cutlineN,
            quota.dailyTextCap(),
            quota.dailyVideoCap(),
            quota.videosToday(),
            quota.textsToday(),
            weights.weightViews(),
            weights.weightComments(),
            weights.weightVotes()
        );
        return new HoldingBoard(items, meta);
    }

    static boolean isMariaRecordChanged(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof SQLException sql && sql.getErrorCode() == 1020) {
                return true;
            }
            String msg = cur.getMessage();
            if (msg != null && msg.contains("Record has changed since last read")) {
                return true;
            }
            if (cur instanceof JpaSystemException && msg != null
                && msg.contains("marketing_holding")) {
                // Fall through — still check nested causes for 1020 specifically
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage();
    }

    /**
     * Pin a holding with admin-chosen format. Soft-reserves one pool slot
     * (and a video slot when {@code format == VIDEO}). When the new reserve
     * shrinks the auto cutline, lowest-ranked non-pinned autos are pushed to
     * {@link MarketingHoldingStatus#OUT_OF_CUT} (Q8). Returns 400 if all
     * remaining pool (or video) slots are already held by pins/commits.
     */
    @Transactional
    public BoardItem pin(String postId, MarketingPinFormat format) {
        if (format == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format is required");
        }

        MarketingHolding holding = holdingRepository.findById(postId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Holding not found: " + postId));

        if (holding.getStatus() == MarketingHoldingStatus.COMMITTED
            || holding.getStatus() == MarketingHoldingStatus.DROPPED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot pin holding in status " + holding.getStatus());
        }
        if (holding.isDraftLocked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Draft is locked (committed)");
        }

        MarketingQuotaService.QuotaStatus quota = quotaService.getStatus();
        SoftReserve others = countSoftReserve(postId);

        if (others.reservedPool() + 1 > quota.remainingPool()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot pin: all remaining pool slots are held by pins");
        }

        long videoCapacity = Math.max(0, quota.dailyVideoCap() - quota.videosToday());
        int newReservedVideos = others.reservedVideos()
            + (format == MarketingPinFormat.VIDEO ? 1 : 0);
        if (format == MarketingPinFormat.VIDEO && newReservedVideos > videoCapacity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot pin as VIDEO: no video slots remaining (cap exhausted by commits/pins)");
        }

        holding.setStatus(MarketingHoldingStatus.PINNED);
        holding.setPinFormat(format);

        int newReservedPool = others.reservedPool() + 1;
        int cutlineN = effectiveCutline(quota.remainingPool(), newReservedPool);
        List<MarketingHolding> pushed = pushAutosOutsideCutline(postId, cutlineN);

        List<MarketingHolding> toSave = new ArrayList<>();
        toSave.add(holding);
        toSave.addAll(pushed);
        holdingRepository.saveAll(toSave);

        Post post = postRepository.findById(postId).orElse(null);
        return toBoardItem(holding, post, format.name());
    }

    /**
     * Unpin: release soft reserve and set status to IN_POOL or OUT_OF_CUT
     * based on the new cutline.
     */
    @Transactional
    public BoardItem unpin(String postId) {
        MarketingHolding holding = holdingRepository.findById(postId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Holding not found: " + postId));

        if (holding.getStatus() != MarketingHoldingStatus.PINNED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Holding is not pinned");
        }

        MarketingQuotaService.QuotaStatus quota = quotaService.getStatus();
        MarketingScoreWeightService.Weights weights = scoreWeightService.getWeights();
        List<RankedCandidate> ranked = rankCandidates(weights);
        SoftReserve others = countSoftReserve(postId);
        int cutlineN = effectiveCutline(quota.remainingPool(), others.reservedPool());

        Set<String> otherPinnedIds = holdingRepository.findByStatusIn(
                EnumSet.of(MarketingHoldingStatus.PINNED))
            .stream()
            .filter(h -> h.getStatus() == MarketingHoldingStatus.PINNED)
            .map(MarketingHolding::getPostId)
            .filter(id -> !id.equals(postId))
            .collect(Collectors.toCollection(HashSet::new));
        Map<String, Integer> autoRanks = autoRankIndex(ranked, otherPinnedIds);
        int autoRank = autoRanks.getOrDefault(postId, Integer.MAX_VALUE);

        holding.setPinFormat(null);
        holding.setStatus(autoRank <= cutlineN
            ? MarketingHoldingStatus.IN_POOL
            : MarketingHoldingStatus.OUT_OF_CUT);
        Map<String, Integer> absoluteRanks = rankIndex(ranked);
        if (absoluteRanks.containsKey(postId)) {
            holding.setRankSnapshot(absoluteRanks.get(postId));
        }

        MarketingHolding saved = holdingRepository.save(holding);
        Post post = postRepository.findById(postId).orElse(null);

        long videoSlots = effectiveVideoSlots(quota, others, cutlineN);
        String projected = projectAfterUnpin(postId, autoRanks, cutlineN, videoSlots);
        return toBoardItem(saved, post, projected);
    }

    /**
     * Projected format for the just-unpinned row: OUT if outside cutline;
     * otherwise VIDEO/TEXT by auto-rank slot among non-pinned.
     */
    private static String projectAfterUnpin(
            String postId, Map<String, Integer> autoRanks, int cutlineN, long videoSlots) {
        int autoRank = autoRanks.getOrDefault(postId, Integer.MAX_VALUE);
        if (autoRank > cutlineN || cutlineN <= 0) {
            return "OUT_OF_CUT";
        }
        return projectedFormat(autoRank, cutlineN, videoSlots);
    }

    @Transactional
    public BoardItem updateDraft(String postId, Map<String, Object> draft) {
        MarketingHolding holding = holdingRepository.findById(postId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Holding not found: " + postId));

        if (holding.isDraftLocked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Draft is locked (committed)");
        }
        if (draft == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "draft is required");
        }

        holding.setDraftJson(serializeMap(draft));
        MarketingHolding saved = holdingRepository.save(holding);
        Post post = postRepository.findById(postId).orElse(null);
        return toBoardItem(saved, post, null);
    }

    /** Package-visible for unit tests: score + sort + rank assignment. */
    List<RankedCandidate> rankCandidates(MarketingScoreWeightService.Weights weights) {
        List<HoldingCandidateProjection> rows = holdingRepository.findActiveCandidates();
        List<RankedCandidate> scored = new ArrayList<>(rows.size());
        for (HoldingCandidateProjection row : rows) {
            int views = toInt(row.getViewCount());
            long comments = toLong(row.getCommentCount());
            long votes = toLong(row.getVoteCount());
            double score = weights.weightViews() * views
                + weights.weightComments() * comments
                + weights.weightVotes() * votes;
            scored.add(new RankedCandidate(
                row.getId(), score, views, comments, votes, row.getCreatedAt(), 0));
        }
        scored.sort(Comparator
            .comparingDouble(RankedCandidate::score).reversed()
            .thenComparing(RankedCandidate::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));

        List<RankedCandidate> ranked = new ArrayList<>(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            RankedCandidate c = scored.get(i);
            ranked.add(new RankedCandidate(
                c.postId(), c.score(), c.views(), c.comments(), c.votes(), c.createdAt(), i + 1));
        }
        return ranked;
    }

    /**
     * Soft-reserve counts for PINNED rows. When {@code excludePostId} is set,
     * that row is omitted (pinning/unpinning the same post).
     */
    SoftReserve countSoftReserve(String excludePostId) {
        List<MarketingHolding> pinned = holdingRepository.findByStatusIn(
            EnumSet.of(MarketingHoldingStatus.PINNED));
        return softReserveFrom(pinned, excludePostId);
    }

    static SoftReserve softReserveFrom(List<MarketingHolding> pinned, String excludePostId) {
        int reservedPool = 0;
        int reservedVideos = 0;
        for (MarketingHolding h : pinned) {
            if (h.getStatus() != MarketingHoldingStatus.PINNED) {
                continue;
            }
            if (excludePostId != null && excludePostId.equals(h.getPostId())) {
                continue;
            }
            reservedPool++;
            if (h.getPinFormat() == MarketingPinFormat.VIDEO) {
                reservedVideos++;
            }
        }
        return new SoftReserve(reservedPool, reservedVideos);
    }

    static int effectiveCutline(long remainingPool, int softReservedPool) {
        return (int) Math.max(0, remainingPool - softReservedPool);
    }

    static long effectiveVideoSlots(
            MarketingQuotaService.QuotaStatus quota, SoftReserve reserve, int cutlineN) {
        long raw = Math.max(0, quota.dailyVideoCap() - quota.videosToday() - reserve.reservedVideos());
        return Math.max(0, Math.min(raw, cutlineN));
    }

    /**
     * Demote non-pinned IN_POOL holdings whose rank is outside the cutline
     * (Q8: pin displaces lowest automatic candidates).
     */
    List<MarketingHolding> pushAutosOutsideCutline(String pinnedPostId, int cutlineN) {
        MarketingScoreWeightService.Weights weights = scoreWeightService.getWeights();
        List<RankedCandidate> ranked = rankCandidates(weights);
        Set<String> stillPinned = holdingRepository.findByStatusIn(
                EnumSet.of(MarketingHoldingStatus.PINNED))
            .stream()
            .filter(h -> h.getStatus() == MarketingHoldingStatus.PINNED)
            .map(MarketingHolding::getPostId)
            .filter(id -> !id.equals(pinnedPostId))
            .collect(Collectors.toCollection(HashSet::new));
        // Include the post being pinned so it is excluded from auto ranks
        stillPinned.add(pinnedPostId);
        Map<String, Integer> autoRanks = autoRankIndex(ranked, stillPinned);

        List<MarketingHolding> active = holdingRepository.findByStatusIn(
            EnumSet.of(MarketingHoldingStatus.IN_POOL, MarketingHoldingStatus.OUT_OF_CUT));

        List<MarketingHolding> pushed = new ArrayList<>();
        for (MarketingHolding h : active) {
            if (h.getPostId().equals(pinnedPostId)) {
                continue;
            }
            int autoRank = autoRanks.getOrDefault(h.getPostId(), Integer.MAX_VALUE);
            if (autoRank > cutlineN && h.getStatus() == MarketingHoldingStatus.IN_POOL) {
                h.setStatus(MarketingHoldingStatus.OUT_OF_CUT);
                pushed.add(h);
            }
        }

        // Prefer explicit lowest-first order for determinism (already filtered)
        pushed.sort(Comparator.comparingInt(
            (MarketingHolding h) -> autoRanks.getOrDefault(h.getPostId(), Integer.MAX_VALUE)).reversed());
        return pushed;
    }

    /**
     * Rows previously on the board that fell outside the display top-20:
     * leave as OUT_OF_CUT (do not delete). PINNED/COMMITTED/DROPPED untouched.
     */
    void demoteOutsideDisplay(Set<String> displayIds, List<MarketingHolding> pendingSaves) {
        Set<String> pendingIds = pendingSaves.stream()
            .map(MarketingHolding::getPostId)
            .collect(Collectors.toSet());

        List<MarketingHolding> active = holdingRepository.findByStatusIn(
            EnumSet.of(MarketingHoldingStatus.IN_POOL, MarketingHoldingStatus.OUT_OF_CUT));

        for (MarketingHolding h : active) {
            if (displayIds.contains(h.getPostId()) || pendingIds.contains(h.getPostId())) {
                continue;
            }
            if (h.getStatus() == MarketingHoldingStatus.IN_POOL) {
                h.setStatus(MarketingHoldingStatus.OUT_OF_CUT);
                pendingSaves.add(h);
            }
        }
    }

    /**
     * @param slotOrRank 1-based index among non-pinned autos inside the cutline,
     *                   or legacy absolute rank when used with full-cutline semantics
     */
    static String projectedFormat(int slotOrRank, int cutlineN, long videoSlots) {
        if (slotOrRank > cutlineN || cutlineN <= 0) {
            return "OUT_OF_CUT";
        }
        if (slotOrRank <= videoSlots) {
            return "VIDEO";
        }
        return "TEXT";
    }

    private static Map<String, Integer> rankIndex(List<RankedCandidate> ranked) {
        Map<String, Integer> map = new HashMap<>();
        for (RankedCandidate c : ranked) {
            map.put(c.postId(), c.rank());
        }
        return map;
    }

    /**
     * 1-based rank among non-pinned candidates only (pins soft-reserve outside this ladder).
     */
    static Map<String, Integer> autoRankIndex(
            List<RankedCandidate> ranked, Set<String> pinnedIds) {
        Map<String, Integer> map = new HashMap<>();
        int autoRank = 0;
        for (RankedCandidate c : ranked) {
            if (pinnedIds.contains(c.postId())) {
                continue;
            }
            autoRank++;
            map.put(c.postId(), autoRank);
        }
        return map;
    }

    private BoardItem toBoardItem(MarketingHolding holding, Post post, String projectedFormat) {
        String title = null;
        Instant postCreatedAt = null;
        if (post != null) {
            title = post.getTitle();
            if (title == null || title.isBlank()) {
                title = post.getUserTitle();
            }
            postCreatedAt = post.getCreatedAt();
        }
        return new BoardItem(
            holding.getPostId(),
            title,
            holding.getStatus(),
            holding.getPinFormat() != null ? holding.getPinFormat().name() : null,
            holding.getScoreSnapshot(),
            holding.getRankSnapshot(),
            parsePlatformRanks(holding.getPlatformRankSnapshot()),
            projectedFormat,
            postCreatedAt,
            holding.getLockedAt(),
            holding.getCreatedAt(),
            holding.getUpdatedAt(),
            parseDraftMap(holding.getDraftJson())
        );
    }

    private String serializeDraft(BriefDto brief) {
        try {
            return objectMapper.writeValueAsString(brief);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize holding draft", e);
        }
    }

    private Map<String, Integer> parsePlatformRanks(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String serializeMap(Map<String, Object> draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize holding draft", e);
        }
    }

    private Map<String, Object> parseDraftMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse holding draft_json: {}", e.getMessage());
            return Map.of();
        }
    }

    private static int toInt(Number n) {
        return n == null ? 0 : n.intValue();
    }

    private static long toLong(Number n) {
        return n == null ? 0L : n.longValue();
    }

    /** Test helper: apply cutline status transition without DB. */
    static MarketingHoldingStatus applyCutlineStatus(
            MarketingHoldingStatus current, boolean isNew, int rank, int cutlineN) {
        if (current == MarketingHoldingStatus.PINNED
            || current == MarketingHoldingStatus.COMMITTED
            || current == MarketingHoldingStatus.DROPPED) {
            return current;
        }
        if (rank <= cutlineN) {
            return MarketingHoldingStatus.IN_POOL;
        }
        if (current == MarketingHoldingStatus.IN_POOL || isNew) {
            return MarketingHoldingStatus.OUT_OF_CUT;
        }
        return current;
    }
}
