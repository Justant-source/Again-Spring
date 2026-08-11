package com.againspring.aiuser.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import java.util.List;

@Value
@Builder
public class ThreadPlanResponse {
    String provider;
    String model;
    String correlationId;
    Post post;
    List<Item> items;
    long elapsedMs;

    @Value @Builder
    public static class Post {
        String title;
        String body;
        /**
         * Master SNS scroll-stop hook (independent of plaza {@code title}).
         * May include semantic newlines for IG card layout.
         */
        @JsonProperty("promo_title")
        String promoTitle;
        /**
         * Dominant scroll-stop emotion for the SNS hook.
         * One of {@code shock|anger|tension|sad|hype}.
         */
        @JsonProperty("hook_emotion")
        String hookEmotion;
        /**
         * 1-based last block of each part except the final.
         * null/empty when body has ≤8 non-empty newline blocks (single card).
         */
        @JsonProperty("capture_split_after_lines")
        List<Integer> captureSplitAfterLines;
        /**
         * @deprecated prefer {@link #captureSplitAfterLines}
         */
        @Deprecated
        @JsonProperty("capture_split_after_line")
        Integer captureSplitAfterLine;
        /**
         * Best-fit metaphor illustration id (60-card catalog). Matched at AI_POST creation.
         * @deprecated prefer {@link #metaphorIds} (ranked list)
         */
        @Deprecated
        @JsonProperty("metaphor_id")
        String metaphorId;
        /**
         * Ranked list of 3-5 metaphor illustration ids (60-card catalog), ordered from best-fit to weakest-fit.
         * The first id is the representative/primary metaphor for video intro; remaining ids illustrate story body beats.
         * Matched at AI_POST creation.
         */
        @JsonProperty("metaphor_ids")
        List<String> metaphorIds;
    }
    @Value @Builder
    public static class Item {
        String ref;
        String parentRef;
        String personaId;
        String body;
        String stance;
        int priority;
    }
}
