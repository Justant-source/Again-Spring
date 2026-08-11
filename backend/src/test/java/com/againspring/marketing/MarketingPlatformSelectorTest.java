package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarketingPlatformSelectorTest {

    private static final Instant T0 = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    void selectAutos_respectsPerPlatformCaps() {
        List<MarketingPlatformSelector.Candidate> candidates = List.of(
            candidate("a", 10, 1, 1, 1),
            candidate("b", 9, 9, 1, 1),
            candidate("c", 8, 8, 9, 1),
            candidate("d", 1, 1, 1, 9)
        );
        Map<String, Integer> remaining = remaining(1, 1, 1, 1);

        Map<String, List<String>> selected = MarketingPlatformSelector.selectAutos(candidates, remaining);

        assertThat(selected.get("x_thread")).containsExactly("a");
        assertThat(selected.get("instagram_feed")).containsExactly("b");
        // c wins reels on score; d wins shorts — no IG conflict
        assertThat(selected.get("instagram_reels")).containsExactly("c");
        assertThat(selected.get("youtube_shorts")).containsExactly("d");
        assertThat(remaining.values()).containsOnly(0);
    }

    @Test
    void igExclusivity_sameStory_higherFeedKeepsFeed_backfillsReels() {
        // a tops both feed and reels; b is second on reels
        List<MarketingPlatformSelector.Candidate> candidates = List.of(
            candidate("a", 1, 100, 90, 1),
            candidate("b", 1, 1, 80, 1),
            candidate("c", 1, 50, 1, 1)
        );
        Map<String, Integer> remaining = remaining(0, 1, 1, 0);

        Map<String, List<String>> selected = MarketingPlatformSelector.selectAutos(candidates, remaining);

        assertThat(selected.get("instagram_feed")).containsExactly("a");
        assertThat(selected.get("instagram_reels")).containsExactly("b");
        assertThat(selected.get("instagram_feed")).doesNotContain("b");
    }

    @Test
    void igExclusivity_tie_keepsReels_backfillsFeed() {
        List<MarketingPlatformSelector.Candidate> candidates = List.of(
            candidate("a", 1, 50, 50, 1), // tie feed=reels=50
            candidate("b", 1, 40, 1, 1)
        );
        Map<String, Integer> remaining = remaining(0, 1, 1, 0);

        Map<String, List<String>> selected = MarketingPlatformSelector.selectAutos(candidates, remaining);

        assertThat(selected.get("instagram_reels")).containsExactly("a");
        assertThat(selected.get("instagram_feed")).containsExactly("b");
    }

    @Test
    void invertSelections_groupsPlatformsPerStory() {
        Map<String, List<String>> byPlatform = Map.of(
            "x_thread", List.of("a", "b"),
            "instagram_feed", List.of("a"),
            "instagram_reels", List.of("c"),
            "youtube_shorts", List.of("a", "c")
        );
        Map<String, List<String>> byPost = MarketingPlatformSelector.invertSelections(byPlatform);
        assertThat(byPost.get("a")).containsExactly("x_thread", "instagram_feed", "youtube_shorts");
        assertThat(byPost.get("b")).containsExactly("x_thread");
        assertThat(byPost.get("c")).containsExactly("instagram_reels", "youtube_shorts");
    }

    private static Map<String, Integer> remaining(int x, int feed, int reels, int shorts) {
        Map<String, Integer> m = new HashMap<>();
        m.put("x_thread", x);
        m.put("instagram_feed", feed);
        m.put("instagram_reels", reels);
        m.put("youtube_shorts", shorts);
        return m;
    }

    private static MarketingPlatformSelector.Candidate candidate(
            String id, double x, double feed, double reels, double shorts) {
        return new MarketingPlatformSelector.Candidate(
            id,
            T0,
            Map.of(
                "x_thread", x,
                "instagram_feed", feed,
                "instagram_reels", reels,
                "youtube_shorts", shorts
            ));
    }
}
