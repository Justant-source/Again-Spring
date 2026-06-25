package com.againspring.aiuser.llm.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostRewriteRequest {
    private String postId;
    private String personaId;
    private String voiceProfile;
    private double slangLevel;
    private String category;
    private String targetCategory;
    private String formality;
    private String demographic;
    private String correlationId;
    private long timeoutMs;
    private String correctionCautions;
    private String globalForbidRules;
    /** rewrite 배치는 clcocloud 직접 호출이 기본값이므로 null/blank면 API로 강제한다. */
    private String backend;
    private String voiceType;
    private String originalTitle;
    private String originalBody;
    private String rewriteInstruction;
}
