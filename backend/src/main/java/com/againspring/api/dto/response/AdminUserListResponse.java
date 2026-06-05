package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 관리자 사용자 목록 항목 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminUserListResponse {
    private String id;
    private String email;
    private String nickname;
    private List<String> roles;
    private String status;
    private boolean isGuest;
    private Instant createdAt;
    private Instant suspendedUntil;
    private String suspendedReason;
}
