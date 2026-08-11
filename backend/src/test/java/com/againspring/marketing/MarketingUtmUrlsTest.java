package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarketingUtmUrlsTest {

    @Test
    void utmSourceForTarget_mapsKnownPlatforms() {
        assertThat(MarketingUtmUrls.utmSourceForTarget("x_thread")).isEqualTo("x");
        assertThat(MarketingUtmUrls.utmSourceForTarget("instagram_feed")).isEqualTo("instagram");
        assertThat(MarketingUtmUrls.utmSourceForTarget("instagram_reels")).isEqualTo("instagram");
        assertThat(MarketingUtmUrls.utmSourceForTarget("youtube_shorts")).isEqualTo("youtube");
        assertThat(MarketingUtmUrls.utmSourceForTarget("naver_blog")).isNull();
    }

    @Test
    void buildUrl_matchesPhase1Contract() {
        String url = MarketingUtmUrls.buildUrl("abc123", "x", "story_9");
        assertThat(url).isEqualTo(
            "https://againspring.net/community/abc123"
                + "?utm_source=x"
                + "&utm_medium=organic"
                + "&utm_campaign=story_9"
                + "&utm_content=abc123_master"
        );
    }

    @Test
    void buildPostUrls_andPrimary_preferYoutubeThenX() {
        Map<String, String> urls = MarketingUtmUrls.buildPostUrls(
            "p1",
            List.of("instagram_reels", "youtube_shorts", "x_thread"),
            "story_3"
        );
        assertThat(urls).containsOnlyKeys("instagram_reels", "youtube_shorts", "x_thread");
        assertThat(urls.get("youtube_shorts")).contains("utm_source=youtube");
        assertThat(urls.get("x_thread")).contains("utm_source=x");
        assertThat(urls.get("instagram_reels")).contains("utm_source=instagram");
        assertThat(MarketingUtmUrls.primaryPostUrl("p1", urls)).isEqualTo(urls.get("youtube_shorts"));
    }

    @Test
    void primaryPostUrl_emptyMap_returnsBare() {
        assertThat(MarketingUtmUrls.primaryPostUrl("p1", Map.of()))
            .isEqualTo("https://againspring.net/community/p1");
    }
}
