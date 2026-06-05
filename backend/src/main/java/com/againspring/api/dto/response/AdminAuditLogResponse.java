package com.againspring.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 관리자 감사 로그 응답 (V68)
 * 감사 로그 조회 및 테이블 표시용
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLogResponse {

    @Schema(description = "감사 로그 ID", example = "1")
    private Long id;

    @Schema(description = "행위자 사용자 ID", example = "user-abc123")
    private String actorUserId;

    @Schema(description = "수행한 액션 (예: DELETE_POST, SUSPEND_USER)", example = "DELETE_POST")
    private String action;

    @Schema(description = "대상 엔티티 타입 (POST, COMMENT, USER, REPORT 등)", example = "POST")
    private String targetType;

    @Schema(description = "대상 엔티티 ID", example = "post-xyz789")
    private String targetId;

    @Schema(description = "변경 전 JSON (빈 경우도 있음)", example = "{\"status\": \"ACTIVE\"}")
    private String beforeJson;

    @Schema(description = "변경 후 JSON", example = "{\"status\": \"DELETED\"}")
    private String afterJson;

    @Schema(description = "요청 발신자 IP 주소", example = "192.168.1.100")
    private String ip;

    @Schema(description = "작업 수행 시각 (ISO 8601)", example = "2026-06-03T15:30:45Z")
    private Instant createdAt;
}
