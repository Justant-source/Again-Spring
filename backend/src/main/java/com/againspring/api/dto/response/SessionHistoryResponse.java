package com.againspring.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * GET /api/users/me/history 응답 DTO.
 * 완료된 세션과 진행 중인 세션을 모두 포함.
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
    private String middleCategoryId;
    private String minorCategoryId;
    private String customCategoryText;
}
