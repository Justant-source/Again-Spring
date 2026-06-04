package com.againspring.aiuser.orchestrator.auth;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 봇 계정 JWT 토큰 캐시.
 * - Lazy login: 처음 필요할 때 로그인
 * - 만료 10분 전 자동 갱신
 * - 401 응답 시 강제 재발급 (invalidate + 1회 재시도)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotTokenCache {

    private final BackendBotClient backendBotClient;
    private final ConcurrentHashMap<String, BotToken> cache = new ConcurrentHashMap<>();

    /** Get valid JWT for a bot account (email + password). Logs in lazily. */
    public Optional<String> getToken(String personaId, String email, String password) {
        BotToken cached = cache.get(personaId);
        if (cached != null && !cached.isExpired()) {
            return Optional.of(cached.getAccessToken());
        }
        // Login (or refresh)
        return login(personaId, email, password);
    }

    /** Force re-login (call after 401 response) */
    public Optional<String> invalidateAndRefresh(String personaId, String email, String password) {
        cache.remove(personaId);
        return login(personaId, email, password);
    }

    private Optional<String> login(String personaId, String email, String password) {
        Optional<String> token = backendBotClient.login(email, password);
        token.ifPresentOrElse(
            t -> {
                cache.put(personaId, new BotToken(t, 86400L)); // 24h
                log.debug("Bot token obtained for personaId={}", personaId);
            },
            () -> log.error("Failed to obtain token for personaId={} email={}", personaId, email)
        );
        return token;
    }

    public void evict(String personaId) {
        cache.remove(personaId);
    }

    public int size() {
        return cache.size();
    }
}
