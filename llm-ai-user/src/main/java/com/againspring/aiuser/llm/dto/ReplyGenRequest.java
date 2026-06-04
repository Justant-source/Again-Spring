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
    private String correlationId;
    private long timeoutMs;
}
