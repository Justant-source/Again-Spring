package com.againspring.aiuser.llm.dto;

import lombok.Data;
import java.util.Map;
import java.util.List;

/** 30-minute batch request. Every input item represents one unprocessed human comment/reply. */
@Data
public class HumanReplyBatchRequest {
    private String provider; // CLAUDE | CODEX
    private String model;
    private String correlationId;
    private Long timeoutMs;
    private List<Item> items;
    /** 요청별 프롬프트 가이드 오버라이드 (key="voice/reply" 등 → 본문). classpath 기본값보다 우선. 없으면 null. */
    private Map<String, String> promptOverrides;

    @Data
    public static class Item {
        private String postId;
        private Long humanCommentId;
        private Long parentCommentId;
        private String postTitle;
        private String postBody;
        private String humanBody;
        /** Optional parent comment body when the human message is a reply. */
        private String parentBody;
        /**
         * Shortlisted personas (interested pool / degrade). LLM picks 0..3 distinct
         * personaIds from this list; empty replies for an item means NO_RESPONSE.
         */
        private List<ThreadPlanRequest.Persona> candidateResponders;
    }
}
