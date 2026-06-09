package com.againspring.aiuser.llm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * ANTHROPIC_API_KEY 조회 우선순위:
 *   1) DB system_setting.ANTHROPIC_API_KEY  (admin UI에서 저장한 값)
 *   2) 환경변수 anthropic.api-key            (docker-compose .env 파일)
 * DB 값은 5분 캐시 후 재조회.
 */
@Slf4j
@Service
public class ApiKeyProvider {

    private static final String DB_KEY     = "ANTHROPIC_API_KEY";
    private static final long   CACHE_MS   = 5 * 60 * 1_000L;

    private final JdbcTemplate jdbc;

    @Value("${anthropic.api-key:}")
    private String envApiKey;

    private volatile String cachedKey    = null;
    private volatile Instant cacheExpiry = Instant.EPOCH;

    public ApiKeyProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String getKey() {
        Instant now = Instant.now();
        if (cachedKey != null && now.isBefore(cacheExpiry)) {
            return cachedKey;
        }
        String dbKey = fetchFromDb();
        if (dbKey != null && !dbKey.isBlank()) {
            log.debug("[ApiKeyProvider] DB에서 ANTHROPIC_API_KEY 로드 (캐시 5분)");
            cachedKey    = dbKey;
            cacheExpiry  = now.plusMillis(CACHE_MS);
            return cachedKey;
        }
        if (envApiKey != null && !envApiKey.isBlank()) {
            log.debug("[ApiKeyProvider] 환경변수 ANTHROPIC_API_KEY 사용");
            cachedKey    = envApiKey;
            cacheExpiry  = now.plusMillis(CACHE_MS);
            return cachedKey;
        }
        return null;
    }

    /** DB system_setting 에서 키 조회. 테이블 미존재나 값 없으면 null 반환. */
    private String fetchFromDb() {
        try {
            return jdbc.queryForObject(
                "SELECT setting_value FROM system_setting WHERE setting_key = ?",
                String.class, DB_KEY
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            log.warn("[ApiKeyProvider] system_setting 조회 실패 (환경변수 폴백): {}", e.getMessage());
            return null;
        }
    }
}
