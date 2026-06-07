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
