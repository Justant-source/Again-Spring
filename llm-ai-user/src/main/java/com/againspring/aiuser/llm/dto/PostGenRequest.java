package com.againspring.aiuser.llm.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostGenRequest {
    private String personaId;
    private String archetype;
    private String voiceProfile;     // JSON string with voice descriptor
    private String tier;             // HEAVY/REGULAR/LIGHT/DORMANT
    private double slangLevel;       // 0.0-1.0
    private String category;         // PostCategory name: COUPLE/MARRIED/FRIEND/FAMILY/WORK/OTHER
    private String topicSeed;        // optional hint
    private String correlationId;
    private long timeoutMs;
}
