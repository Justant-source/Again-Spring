package com.againspring.aiuser.llm.dto;

import lombok.*;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostGenRequest {
    private String personaId;
    private String archetype;
    private String voiceProfile;     // JSON string with voice descriptor
    /**
     * persona-diversity-v4 계약 4 — WP1 {@code PersonaCard.render()} 출력. 있으면 legacy
     * {@code /generate/post}(PromptAssembler)가 {@link #voiceProfile} 대신 이 값을 페르소나
     * 특성 섹션에 쓴다. 없으면 기존처럼 voiceProfile 문자열을 그대로 쓴다.
     */
    private String personaCard;
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
    /** CLAUDE | CODEX | API | STUB. 비면 backend(구 필드) → CLAUDE 순으로 해석. */
    private String provider;

    public com.againspring.aiuser.llm.service.LlmProvider resolveProvider() {
        return com.againspring.aiuser.llm.service.LlmProvider.parseLegacy(provider, backend);
    }
    /** 이 페르소나의 최근 글 본문들 ("- …" 개행 구분) — 소재·표현 반복 방지 주입. 없으면 null. */
    private String recentOutputs;
    /** 진행 중인 상황 — CASUAL이 아닌 갈등 글에서 추출한 첫 문장 (이어가기용). 없으면 null. */
    private String ongoingSituation;
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
    /** 요청별 프롬프트 가이드 오버라이드 (key="voice/post" 등 → 본문). classpath 기본값보다 우선. 없으면 null. */
    private Map<String, String> promptOverrides;
}
