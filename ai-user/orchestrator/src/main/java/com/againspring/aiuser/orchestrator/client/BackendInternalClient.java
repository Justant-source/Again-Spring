package com.againspring.aiuser.orchestrator.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/** backend 내부 API(/api/internal/ai-user). users 테이블 직접 쓰기를 대체한다. */
@Slf4j
@Component
public class BackendInternalClient {
    private final RestClient restClient;
    private final String token;

    public BackendInternalClient(@Qualifier("backendRestClient") RestClient restClient,
                                 @Value("${ai-user.internal-token:}") String token) {
        this.restClient = restClient;
        this.token = token;
    }

    public Optional<String> upsertPersona(String id, String email, String nickname, String password) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.post().uri("/api/internal/ai-user/personas/upsert")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("id", id, "email", email, "nickname", nickname, "password", password))
                .retrieve().body(Map.class);
            return Optional.ofNullable(body == null ? null : (String) body.get("status"));
        } catch (Exception e) {
            log.error("upsertPersona failed for {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Integer> rotatePassword(String password) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.post().uri("/api/internal/ai-user/personas/rotate-password")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("password", password))
                .retrieve().body(Map.class);
            return Optional.ofNullable(body == null ? null : ((Number) body.get("updated")).intValue());
        } catch (Exception e) {
            log.error("rotatePassword failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Integer> reconcileViews() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.post().uri("/api/internal/ai-user/views/reconcile")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(Map.class);
            return Optional.ofNullable(body == null ? null : ((Number) body.get("updated")).intValue());
        } catch (Exception e) {
            log.error("reconcileViews failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
