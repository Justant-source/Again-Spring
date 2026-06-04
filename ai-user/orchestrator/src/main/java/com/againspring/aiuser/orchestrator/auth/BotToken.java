package com.againspring.aiuser.orchestrator.auth;

import lombok.Getter;

import java.time.Instant;

/**
 * 봇 계정 JWT 토큰 캐시 엔트리.
 * 24시간 유효 (backend JWT 기본 만료).
 */
@Getter
public class BotToken {
    private final String accessToken;
    private final Instant expiresAt;

    public BotToken(String accessToken, long expiresInSeconds) {
        this.accessToken = accessToken;
        // 10분 여유를 두고 만료 처리
        this.expiresAt = Instant.now().plusSeconds(expiresInSeconds - 600);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
