package com.againspring.aiuser.llm.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Logical Call2 for AI paired posts: 상대방(B) body + phase2 comment candidates
 * grounded on author + partner (+ up to 5–8 latest published top-level comments).
 */
@Data
public class PairedPhase2Request {
    private String provider; // CLAUDE | CODEX
    private String model;
    private String correlationId;
    private Long timeoutMs;
    private String category;
    /** Published 작성자 post — required. */
    private AuthorPost authorPost;
    /** Explicit 상대방 voice/profile. */
    private Map<String, Object> partner;
    private List<ThreadPlanRequest.Persona> personas;
    /**
     * Up to 5–8 latest published top-level comments (may be empty).
     * When empty, the model must ground comments on author (+ partner) only.
     */
    private List<PublishedComment> publishedTopLevelComments;
    /**
     * When true (default), generate {@code partner_post}. When false, comment-only
     * micro-batch continuation of logical Call2 — {@code partner_post} must be null.
     */
    private Boolean includePartnerPost = true;
    private Integer maxTopLevel = 14;
    private Integer maxReplies = 10;
    private Integer minTopLevel;
    private Integer minItems;

    @Data
    public static class AuthorPost {
        /** Optional — when present, must not be used as a comment personaId. */
        private String personaId;
        private String title;
        private String body;
    }

    @Data
    public static class PublishedComment {
        private String body;
        private String nickname;
        private String createdAt;
    }
}
