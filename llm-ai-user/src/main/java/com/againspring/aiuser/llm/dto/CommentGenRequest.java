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
    private String correlationId;
    private long timeoutMs;
}
