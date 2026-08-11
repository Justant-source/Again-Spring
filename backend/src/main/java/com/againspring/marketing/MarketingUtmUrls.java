package com.againspring.marketing;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase-1 UTM landing URLs for marketing jobs.
 *
 * <pre>
 * https://againspring.net/community/{postId}
 *   ?utm_source={x|instagram|youtube}
 *   &amp;utm_medium=organic
 *   &amp;utm_campaign=story_{jobId}
 *   &amp;utm_content={postId}_master
 * </pre>
 */
public final class MarketingUtmUrls {

    public static final String COMMUNITY_BASE = "https://againspring.net/community/";
    public static final String MEDIUM = "organic";
    public static final String CONTENT_SUFFIX = "_master";

    private MarketingUtmUrls() {}

    public static String campaignForJob(long jobId) {
        return "story_" + jobId;
    }

    /**
     * Map ASM target platform id → {@code utm_source}. Unknown platforms return null
     * (no UTM attachment).
     */
    public static String utmSourceForTarget(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        String id = target.trim().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "x_thread", "x" -> "x";
            case "instagram_feed", "instagram_reels", "instagram" -> "instagram";
            case "youtube_shorts", "youtube" -> "youtube";
            default -> null;
        };
    }

    public static String buildUrl(String postId, String utmSource, String campaign) {
        String content = postId + CONTENT_SUFFIX;
        return COMMUNITY_BASE + postId
            + "?utm_source=" + enc(utmSource)
            + "&utm_medium=" + enc(MEDIUM)
            + "&utm_campaign=" + enc(campaign)
            + "&utm_content=" + enc(content);
    }

    /** Bare story URL (no UTM) — fallback when no target maps to a known source. */
    public static String barePostUrl(String postId) {
        return COMMUNITY_BASE + postId;
    }

    /**
     * Per-target landing URLs keyed by ASM platform id (e.g. {@code x_thread}).
     * Targets without a known {@code utm_source} are omitted.
     */
    public static Map<String, String> buildPostUrls(String postId, List<String> targets, String campaign) {
        Map<String, String> urls = new LinkedHashMap<>();
        if (targets == null) {
            return urls;
        }
        for (String target : targets) {
            if (target == null || target.isBlank()) {
                continue;
            }
            String id = target.trim().toLowerCase(Locale.ROOT);
            String source = utmSourceForTarget(id);
            if (source == null) {
                continue;
            }
            urls.putIfAbsent(id, buildUrl(postId, source, campaign));
        }
        return urls;
    }

    /**
     * Single {@code brief.post_url} for ASM (still reads one URL). Prefer YouTube then X
     * then any mapped URL; else bare community path.
     */
    public static String primaryPostUrl(String postId, Map<String, String> postUrls) {
        if (postUrls == null || postUrls.isEmpty()) {
            return barePostUrl(postId);
        }
        if (postUrls.containsKey("youtube_shorts")) {
            return postUrls.get("youtube_shorts");
        }
        if (postUrls.containsKey("x_thread")) {
            return postUrls.get("x_thread");
        }
        if (postUrls.containsKey("x")) {
            return postUrls.get("x");
        }
        return postUrls.values().iterator().next();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
