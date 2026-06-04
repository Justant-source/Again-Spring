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
        private String correlationId;
        @Builder.Default
        private long timeoutMs = 120000L;
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
        private String archetypeCommentSamples;
        private String existingComments;
        private String correlationId;
        @Builder.Default
        private long timeoutMs = 120000L;
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
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private String text;
        private long latencyMs;
        private String correlationId;
        private String error;
        private String errorType;

        public boolean isSuccess() {
            return text != null && !text.isBlank();
        }
    }
}
