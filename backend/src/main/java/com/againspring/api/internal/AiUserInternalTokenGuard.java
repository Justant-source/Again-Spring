package com.againspring.api.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** AI_USER_INTERNAL_TOKEN 상수시간 Bearer 검사. orchestrator → backend 내부 채널 전용. */
@Component
public class AiUserInternalTokenGuard {
    private final String token;

    public AiUserInternalTokenGuard(@Value("${ai-user.internal-token:}") String token) { this.token = token == null ? "" : token; }

    public boolean isAuthorized(String authHeader) {
        if (token.isBlank() || authHeader == null) return false;
        byte[] a = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        byte[] b = authHeader.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) return false;
        byte r = 0;
        for (int i = 0; i < a.length; i++) r |= (byte) (a[i] ^ b[i]);
        return r == 0;
    }
}
