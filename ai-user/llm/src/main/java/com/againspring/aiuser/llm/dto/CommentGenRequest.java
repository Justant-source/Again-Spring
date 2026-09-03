package com.againspring.aiuser.llm.dto;

import lombok.*;
import java.util.Map;

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
    private String dynamicExamples;  // RAG: 실제 커뮤니티 유사 예시
    private String archetypeCommentSamples;
    private String existingComments;
    private String correlationId;
    private long timeoutMs;
    /** 이 페르소나의 과거 첨삭 기반 주의사항 (개행 구분, "- …" 형식). 없으면 null. */
    private String correctionCautions;
    /** 모든 AI 유저 공통 전역 금지 규칙 (개행 구분, "- …" 형식). 없으면 null. */
    private String globalForbidRules;
    /** 생성 백엔드: "CLI" | "API" | null */
    private String backend;
    /** CLAUDE | CODEX | API | STUB. 비면 backend(구 필드) → CLAUDE 순으로 해석. */
    private String provider;

    public com.againspring.aiuser.llm.service.LlmProvider resolveProvider() {
        return com.againspring.aiuser.llm.service.LlmProvider.parseLegacy(provider, backend);
    }
    /** 번호 매긴 댓글 목록 ("1. 본문\n2. ↳ 대댓글\n..."). 피기백 반응용. 없으면 null. */
    private String reactableComments;
    /** 좋아요/투표 성향 수치 ("좋아요 성향 0.7/1.0, 투표 성향 0.4/1.0"). 없으면 null. */
    private String dispositionNote;
    /** 이 페르소나의 최근 댓글 본문들 ("- …" 개행 구분) — 반복 방지 주입. 없으면 null. */
    private String recentOutputs;
    /** 문체 few-shot — voice 소스 크롤 코퍼스에서 랜덤 샘플 ("---" 구분). 없으면 null. */
    private String styleExamples;
    /** 댓글 모드·길이 지시문 (오케스트레이터가 렌더한 한국어 1~2줄). 없으면 null. */
    private String modeHint;
    /** 커뮤니티 voice 타입 (NATEPAN/DCINSIDE/THEQOO 등) — OutputSanitizer 분포 매칭용. 없으면 null. */
    private String voiceType;
    /** 요청별 프롬프트 가이드 오버라이드 (key="voice/comment" 등 → 본문). classpath 기본값보다 우선. 없으면 null. */
    private Map<String, String> promptOverrides;
}
