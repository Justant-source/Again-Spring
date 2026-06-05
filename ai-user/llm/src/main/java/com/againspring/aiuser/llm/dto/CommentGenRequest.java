package com.againspring.aiuser.llm.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentGenRequest {
    private String personaId;
    private String voiceProfile;
    private double slangLevel;
    private String postTitle;
    private String postBodyExcerpt;  // max ~300 chars
    private String stance;           // "AUTHOR" | "PARTNER" | "NEUTRAL"
    private String category;
    private String formality;        // "casual" (반말) | "polite" (존댓말)
    private String demographic;
    private String dynamicExamples;  // RAG: 실제 커뮤니티 유사 예시
    private String archetypeCommentSamples;
    private String existingComments;
    private String correlationId;
    private long timeoutMs;
    /** 이 페르소나의 과거 첨삭 기반 주의사항 (개행 구분, "- …" 형식). 없으면 null. */
    private String correctionCautions;
    /** 모든 AI 유저 공통 전역 금지 규칙 (개행 구분, "- …" 형식). 없으면 null. */
    private String globalForbidRules;
}
