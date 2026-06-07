package com.againspring.aiuser.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 글 1건 분석 요청 — 좋아요·투표 결정용 신호 추출.
 * orchestrator가 글마다 1회 호출하고 결과를 영구 캐시한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostAnalysisRequest {
    private String postId;          // 캐시·추적용
    private String title;           // 글 제목
    private String bodyPublished;   // 글 본문(공개본)
    private String category;        // COUPLE|MARRIED|FAMILY|FRIEND|WORK|OTHER
    private String archetypeHints;  // 카테고리별 후보 archetype id (콤마 구분), 없으면 null
    private String correlationId;   // 추적용
}
