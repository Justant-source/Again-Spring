package com.againspring.aiuser.llm.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyGenRequest {
    private String personaId;
    private String voiceProfile;
    private double slangLevel;
    private String parentCommentExcerpt;
    private String threadContext;
    private String stance;           // "AGREE" | "DISAGREE" | "CURIOUS"
    private String formality;        // "casual" (반말) | "polite" (존댓말)
    private String demographic;
    private String postBodyExcerpt;
    private String siblingComments;
    private String correlationId;
    private long timeoutMs;
    /** 이 페르소나의 과거 첨삭 기반 주의사항 (개행 구분, "- …" 형식). 없으면 null. */
    private String correctionCautions;
    /** 모든 AI 유저 공통 전역 금지 규칙 (개행 구분, "- …" 형식). 없으면 null. */
    private String globalForbidRules;
    /** 생성 백엔드: "CLI" | "API" | null */
    private String backend;
    /** 좋아요/투표 성향 수치 ("좋아요 성향 0.7/1.0, 투표 성향 0.4/1.0"). 없으면 null. */
    private String dispositionNote;
    /** 이 페르소나의 최근 댓글 본문들 ("- …" 개행 구분) — 반복 방지 주입. 없으면 null. */
    private String recentOutputs;
    /** 문체 few-shot — voice 소스 크롤 코퍼스에서 랜덤 샘플 ("---" 구분). 없으면 null. */
    private String styleExamples;
    /** 대댓글 길이 지시문 (오케스트레이터가 렌더한 한국어 1줄). 없으면 null. */
    private String modeHint;
}
