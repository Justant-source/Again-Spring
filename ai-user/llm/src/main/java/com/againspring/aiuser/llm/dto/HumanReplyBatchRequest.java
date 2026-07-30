package com.againspring.aiuser.llm.dto;

import lombok.Data;
import java.util.List;

/** 30-minute batch request. Every input item represents one unprocessed human comment/reply. */
@Data
public class HumanReplyBatchRequest {
    private String provider; // CLAUDE | CODEX
    private String model;
    private String correlationId;
    private Long timeoutMs;
    private List<Item> items;

    @Data
    public static class Item {
        private Long postId;
        private Long humanCommentId;
        private Long parentCommentId;
        private String postTitle;
        private String postBody;
        private String humanBody;
        private ThreadPlanRequest.Persona responder;
    }
}
