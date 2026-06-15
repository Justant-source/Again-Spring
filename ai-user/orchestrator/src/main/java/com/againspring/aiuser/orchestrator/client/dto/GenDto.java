package com.againspring.aiuser.orchestrator.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class GenDto {
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PostRequest {
        private String personaId;
        private String archetype;
        private String voiceProfile;
        private String tier;
        private double slangLevel;
        private String category;
        private String topicSeed;
        private String formality;
        private String demographic;
        private String dynamicExamples;
        private String lengthTier;   // "SHORT"|"MEDIUM"|"LONG"|"VERYLONG"
        private String correlationId;
        @Builder.Default
        private long timeoutMs = 120000L;
        private String stance;           // "AUTHOR" | "PARTNER"
        private String counterpartBody;  // PARTNER일 때 원글 본문
        /** 이 페르소나의 과거 첨삭 기반 주의사항 (개행 구분). 없으면 null. */
        private String correctionCautions;
        /** 모든 AI 유저 공통 전역 금지 규칙 (개행 구분). 없으면 null. */
        private String globalForbidRules;
        /** 생성 백엔드: "CLI" | "API" | null (null→CLI). §11 */
        private String backend;
        /** 이 페르소나의 최근 글 본문들 ("- ..." 개행 구분) — 반복 방지 주입. 없으면 null. */
        private String recentOutputs;
        // ── 재구성 모드 (원본 비교 기능) ────────────────────────────────────────────
        /** true = 단일 크롤 원본을 기반으로 사연을 재구성하는 모드. */
        @Builder.Default
        private boolean reconstructMode = false;
        /** 재구성 원본 example_bank.id (reconstruct_mode=true 시 필수) */
        private Long sourceExampleId;
        /** 재구성할 크롤 원본 본문 (reconstruct_mode=true 시 필수) */
        private String sourceBody;
        /** scope=RECONSTRUCTION 전역 규칙 목록 ("- …" 개행 구분). 없으면 null. */
        private String reconstructionRules;
        /** 커뮤니티 voice 타입 — OutputSanitizer 분포 매칭용. 없으면 null. */
        private String voiceType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CommentRequest {
        private String personaId;
        private String voiceProfile;
        private double slangLevel;
        private String postTitle;
        private String postBodyExcerpt;
        private String stance;
        private String category;
        private String formality;
        private String demographic;
        private String dynamicExamples;
        private String archetypeCommentSamples;
        private String existingComments;
        private String correlationId;
        @Builder.Default
        private long timeoutMs = 120000L;
        /** 이 페르소나의 과거 첨삭 기반 주의사항 (개행 구분). 없으면 null. */
        private String correctionCautions;
        /** 모든 AI 유저 공통 전역 금지 규칙 (개행 구분). 없으면 null. */
        private String globalForbidRules;
        /** 생성 백엔드: "CLI" | "API" | null */
        private String backend;
        /** 번호 매긴 댓글 목록 ("1. 본문\n2. ↳ 대댓글\n..."). 피기백 반응용. 없으면 null. */
        private String reactableComments;
        /** 좋아요/투표 성향 수치 ("좋아요 성향 0.7/1.0, 투표 성향 0.4/1.0"). 없으면 null. */
        private String dispositionNote;
        /** 이 페르소나의 최근 댓글 본문들 ("- ..." 개행 구분) — 반복 방지 주입. 없으면 null. */
        private String recentOutputs;
        /** 문체 few-shot — voice 소스 크롤 코퍼스에서 랜덤 샘플 ("---" 구분). 없으면 null. */
        private String styleExamples;
        /** 댓글 모드·길이 지시문 (렌더된 한국어 1~2줄). 없으면 null. */
        private String modeHint;
        /** 커뮤니티 voice 타입 — OutputSanitizer 분포 매칭용. 없으면 null. */
        private String voiceType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReplyRequest {
        private String personaId;
        private String voiceProfile;
        private double slangLevel;
        private String parentCommentExcerpt;
        private String threadContext;
        private String stance;
        private String formality;
        private String demographic;
        private String postBodyExcerpt;
        private String siblingComments;
        private String correlationId;
        @Builder.Default
        private long timeoutMs = 120000L;
        /** 이 페르소나의 과거 첨삭 기반 주의사항 (개행 구분). 없으면 null. */
        private String correctionCautions;
        /** 모든 AI 유저 공통 전역 금지 규칙 (개행 구분). 없으면 null. */
        private String globalForbidRules;
        /** 생성 백엔드: "CLI" | "API" | null */
        private String backend;
        /** 좋아요/투표 성향 수치 ("좋아요 성향 0.7/1.0, 투표 성향 0.4/1.0"). 없으면 null. */
        private String dispositionNote;
        /** 이 페르소나의 최근 댓글 본문들 ("- ..." 개행 구분) — 반복 방지 주입. 없으면 null. */
        private String recentOutputs;
        /** 문체 few-shot — voice 소스 크롤 코퍼스에서 랜덤 샘플 ("---" 구분). 없으면 null. */
        private String styleExamples;
        /** 대댓글 길이 지시문 (렌더된 한국어 1줄). 없으면 null. */
        private String modeHint;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private String text;
        /** 피기백 반응 JSON (comment/reply 전용). null이면 반응 없음. */
        private String reactionsJson;
        private long latencyMs;
        private String correlationId;
        private String error;
        private String errorType;

        public boolean isSuccess() {
            return text != null && !text.isBlank();
        }
    }
}
