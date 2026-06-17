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
    /** 이 페르소나의 최근 글 본문들 ("- …" 개행 구분) — 소재·표현 반복 방지 주입. 없으면 null. */
    private String recentOutputs;
    // ── 재구성 모드 (원본 비교 기능) ────────────────────────────────────────────
    /** true = 단일 크롤 원본을 기반으로 사연을 재구성하는 모드 */
    private boolean reconstructMode;
    /** 재구성 원본 example_bank.id */
    private Long sourceExampleId;
    /** 재구성할 크롤 원본 본문 */
    private String sourceBody;
    /** scope=RECONSTRUCTION 전역 규칙 목록 ("- …" 개행 구분). 없으면 null. */
    private String reconstructionRules;
    /** 커뮤니티 voice 타입 (NATEPAN/DCINSIDE/THEQOO 등) — OutputSanitizer 분포 매칭용. 없으면 null. */
    private String voiceType;
    /** 글 생성 모드: "CONFLICT"(갈등 서사, 기본) | "CASUAL"(일상/잡담). PromptAssembler 분기용. */
    private String postKind;
}
