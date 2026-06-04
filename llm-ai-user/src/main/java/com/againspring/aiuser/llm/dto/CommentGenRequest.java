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
    private String archetypeCommentSamples;
    private String existingComments;
    private String correlationId;
    private long timeoutMs;
}
