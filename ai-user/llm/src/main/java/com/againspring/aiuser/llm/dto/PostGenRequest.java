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
    private String formality;        // "casual" (반말) | "polite" (존댓말)
    private String demographic;
    private String dynamicExamples;  // RAG: 실제 커뮤니티 유사 예시 (ai-learning 서비스에서 주입)
    private String lengthTier;       // "SHORT"|"MEDIUM"|"LONG"|"VERYLONG"
    private String correlationId;
    private long timeoutMs;
    private String stance;           // "AUTHOR"(기본, null이면 AUTHOR) | "PARTNER"
    private String counterpartBody;  // PARTNER일 때: 작성자 발행 본문 (컨텍스트)
    /** 이 페르소나의 과거 첨삭 기반 주의사항 (개행 구분, "- …" 형식). 없으면 null. */
    private String correctionCautions;
    /** 모든 AI 유저 공통 전역 금지 규칙 (개행 구분, "- …" 형식). 없으면 null. */
    private String globalForbidRules;
    /** 생성 백엔드: "CLI" | "API" | null (null→CLI). §11 토큰 관제 */
    private String backend;
}
