package com.againspring.api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 정지 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuspendUserRequest {
    /** 정지 사유 (필수) */
    private String reason;

    /** 정지 종료 시각 (ISO-8601, null이면 무기한) */
    private String suspendedUntil;
}
