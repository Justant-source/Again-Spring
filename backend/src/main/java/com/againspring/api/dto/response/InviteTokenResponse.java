package com.againspring.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * InviteTokenResponse (V1.5)
 * 초대 토큰 정보
 */
@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
public class InviteTokenResponse {
    private String inviteToken;
    private Instant inviteExpiresAt;
}
