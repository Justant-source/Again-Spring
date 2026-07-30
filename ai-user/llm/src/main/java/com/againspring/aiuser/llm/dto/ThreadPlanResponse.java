package com.againspring.aiuser.llm.dto;

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
    public static class Post { String title; String body; }
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
