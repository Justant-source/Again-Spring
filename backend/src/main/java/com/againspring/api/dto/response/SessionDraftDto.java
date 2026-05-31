package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 세션 → 커뮤니티 초안 추출 DTO (Phase 5)
 * sessionId, title, category, bodyRaw를 반환
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionDraftDto {

    /** 원본 세션 ID */
    private String sessionId;

    /** LLM 자동 생성 세션 제목 */
    private String title;

    /** 대분류 카테고리 ID (majorId만) */
    private String category;

    /** 익명화된 본문 (USER_A → A님, USER_B → B님) */
    private String bodyRaw;

    /** 선택사항: 세션이 완료된 시각 */
    private Long completedAtEpochSeconds;
}
