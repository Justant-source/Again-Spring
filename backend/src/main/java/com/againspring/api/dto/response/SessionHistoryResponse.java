package com.againspring.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * GET /api/users/me/history 응답 DTO.
 * 완료된 세션과 진행 중인 세션을 모두 포함.
 * ADMIN 사용자는 isTestRun=true인 마케팅 시뮬레이션 세션도 포함됨.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionHistoryResponse {
    private String id;
    private String status;
    private String relationType;
    private String conflictType;
    private String partnerNickname;
    private boolean soloMode;
    private Instant completedAt;
    private Instant createdAt;
    private String majorCategoryId;
    // middleCategoryId, minorCategoryId 제거 (V47 — 자동 추론 전환)
    private String customCategoryText;
    /** V47 신규: LLM 자동 생성 제목. */
    private String title;
    /** V47 신규: 추론 핵심 키워드 최대 2개. */
    private java.util.List<String> keywords;
    /** V47 신규: 한국 특화 태그 (in_law|face|lingered|generation|null). */
    private String koreanTag;
    private String reportId;
    private boolean testRun;
}
