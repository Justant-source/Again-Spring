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
        /** 1-based last front-half newline block; null when body has ≤12 blocks. */
        @JsonProperty("capture_split_after_line")
        Integer captureSplitAfterLine;
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
