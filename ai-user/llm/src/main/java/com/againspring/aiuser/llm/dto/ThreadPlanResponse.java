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
        /** IG hook: title chars + semantic \\n; each line ≤10. */
        @JsonProperty("promo_title")
        String promoTitle;
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
         */
        @JsonProperty("metaphor_id")
        String metaphorId;
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
