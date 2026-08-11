package com.againspring.marketing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Phase 2.1–2.2 auto-selection: independent per-platform ranking up to remaining
 * daily caps, then Instagram feed ⊥ Reels exclusivity (higher score wins; tie → Reels).
 * Same story may win multiple platforms the same day.
 */
public final class MarketingPlatformSelector {

    private MarketingPlatformSelector() {}

    public record Candidate(
        String postId,
        Instant createdAt,
        Map<String, Double> scores
    ) {
        public Candidate {
            Objects.requireNonNull(postId, "postId");
            scores = scores == null ? Map.of() : Map.copyOf(scores);
        }

        public double scoreOf(String platform) {
            Double v = scores.get(MarketingPopularityScorer.normalizePlatform(platform));
            return v != null ? v : 0.0;
        }
    }

    /**
     * @param candidates     auto candidates (pins already applied / excluded)
     * @param remainingCaps  mutable remaining slots per platform (enabled only)
     * @return platform → selected postIds in rank order (mutates remainingCaps)
     */
    public static Map<String, List<String>> selectAutos(
            List<Candidate> candidates,
            Map<String, Integer> remainingCaps) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(remainingCaps, "remainingCaps");

        Map<String, List<String>> selected = new LinkedHashMap<>();
        for (String platform : MarketingPopularityScorer.RANKED_PLATFORMS) {
            int remaining = remainingCaps.getOrDefault(platform, 0);
            if (remaining <= 0) {
                selected.put(platform, List.of());
                continue;
            }
            List<Candidate> ranked = new ArrayList<>(candidates);
            ranked.sort(comparatorFor(platform));
            List<String> picks = new ArrayList<>(remaining);
            for (Candidate c : ranked) {
                if (picks.size() >= remaining) {
                    break;
                }
                picks.add(c.postId());
            }
            selected.put(platform, picks);
            remainingCaps.put(platform, remaining - picks.size());
        }

        applyIgExclusivity(selected, indexById(candidates), remainingCaps, candidates);
        return selected;
    }

    /**
     * Resolve feed ⊥ Reels conflicts. After removing a loser, backfill that platform
     * from the next-ranked eligible candidates (still under remaining + not already picked).
     */
    public static void applyIgExclusivity(
            Map<String, List<String>> selected,
            Map<String, Candidate> byId,
            Map<String, Integer> remainingCaps,
            List<Candidate> allCandidates) {
        List<String> feed = new ArrayList<>(selected.getOrDefault(
            MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED, List.of()));
        List<String> reels = new ArrayList<>(selected.getOrDefault(
            MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS, List.of()));

        Set<String> feedSet = new HashSet<>(feed);
        Set<String> reelsSet = new HashSet<>(reels);
        Set<String> conflict = new HashSet<>(feedSet);
        conflict.retainAll(reelsSet);

        for (String postId : conflict) {
            Candidate c = byId.get(postId);
            double scoreFeed = c != null
                ? c.scoreOf(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED) : 0.0;
            double scoreReels = c != null
                ? c.scoreOf(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS) : 0.0;
            String winner = MarketingPopularityScorer.resolveIgExclusiveWinner(scoreFeed, scoreReels);
            if (MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS.equals(winner)) {
                feed.remove(postId);
                remainingCaps.merge(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED, 1, Integer::sum);
            } else {
                reels.remove(postId);
                remainingCaps.merge(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS, 1, Integer::sum);
            }
        }

        backfill(
            MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED,
            feed,
            remainingCaps,
            allCandidates,
            reels);
        backfill(
            MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS,
            reels,
            remainingCaps,
            allCandidates,
            feed);

        selected.put(MarketingPopularityScorer.PLATFORM_INSTAGRAM_FEED, List.copyOf(feed));
        selected.put(MarketingPopularityScorer.PLATFORM_INSTAGRAM_REELS, List.copyOf(reels));
    }

    private static void backfill(
            String platform,
            List<String> current,
            Map<String, Integer> remainingCaps,
            List<Candidate> allCandidates,
            List<String> exclusiveOther) {
        int room = remainingCaps.getOrDefault(platform, 0);
        if (room <= 0 || allCandidates == null) {
            return;
        }
        List<Candidate> ranked = new ArrayList<>(allCandidates);
        ranked.sort(comparatorFor(platform));
        for (Candidate c : ranked) {
            if (room <= 0) {
                break;
            }
            if (current.contains(c.postId())) {
                continue;
            }
            // IG exclusivity: do not add a story already assigned to the other IG channel
            if (exclusiveOther.contains(c.postId())) {
                continue;
            }
            current.add(c.postId());
            room--;
            remainingCaps.put(platform, room);
        }
    }

    private static Map<String, Candidate> indexById(List<Candidate> candidates) {
        Map<String, Candidate> map = new HashMap<>();
        for (Candidate c : candidates) {
            map.put(c.postId(), c);
        }
        return map;
    }

    private static Comparator<Candidate> comparatorFor(String platform) {
        return Comparator
            .comparingDouble((Candidate c) -> c.scoreOf(platform)).reversed()
            .thenComparing(Candidate::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Candidate::postId);
    }

    /**
     * Invert platform→posts into postId→platforms (stable platform order).
     */
    public static Map<String, List<String>> invertSelections(Map<String, List<String>> byPlatform) {
        Map<String, List<String>> byPost = new LinkedHashMap<>();
        for (String platform : MarketingPopularityScorer.RANKED_PLATFORMS) {
            for (String postId : byPlatform.getOrDefault(platform, List.of())) {
                byPost.computeIfAbsent(postId, k -> new ArrayList<>()).add(platform);
            }
        }
        return byPost;
    }
}
