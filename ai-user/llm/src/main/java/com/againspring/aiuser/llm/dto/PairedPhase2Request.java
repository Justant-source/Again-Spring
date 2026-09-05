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
    /**
     * persona-diversity-v4 계약 5/7 — 골격의 {@code b_side_viable}. false인데
     * {@code includePartnerPost}가 true면 상대방(B)이 글을 올릴 만한 관계가 아니라는 뜻이므로
     * {@code createPairedPhase2}가 400으로 거부한다. 골격이 없거나(freestyle) 알 수 없으면 null
     * — 이 경우 게이트를 적용하지 않는다.
     */
    private Boolean bSideViable;
    /**
     * persona-diversity-v4 WP2 재배선 — Call1과 같은 계약7 골격(뼈대). partner_post는 이 안의
     * {@code counterpart_claim}으로 같은 사건을 상대방 시점에서 다시 말해야 한다. 골격이 없으면
     * (freestyle) null — 기존 자유 생성 동작을 유지한다.
     */
    private Map<String, Object> sourceContext;
    private Boolean reconstructMode;
    private Long sourceExampleId;
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
    /** 요청별 프롬프트 가이드 오버라이드 (key="voice/paired_phase2" 등 → 본문). classpath 기본값보다 우선. 없으면 null. */
    private Map<String, String> promptOverrides;

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
