package com.againspring.aiuser.orchestrator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * orchestrator는 자기 환경을 반드시 안다. AI_USER_ENV 누락·DB/backend 호스트 불일치 = 기동 실패.
 * 이유: 2026-09-03 감사 — SPRING_PROFILES_ACTIVE는 프로필 파일이 없어 무효였고,
 * backend URL 기본값이 prod라 dev 컨테이너가 prod로 게시할 수 있었다.
 *
 * <p>기동 순서 보장: 이 빈은 생성자(필드 주입)에서 검증을 수행하므로, Spring이 이 빈을
 * 만드는 시점(= ApplicationContext refresh 중, 스케줄러/@PostConstruct가 도는 시점보다 앞)에
 * 실패하면 컨텍스트 refresh 자체가 중단된다. {@code @Scheduled} 메서드는 컨텍스트가 완전히
 * refresh된 뒤에만 스케줄링되므로, 이 생성자에서 던지는 예외는 어떤 스케줄러 tick보다도
 * 반드시 먼저 실행되어 부팅을 막는다(별도 ApplicationRunner/@PostConstruct 불필요).
 */
@Slf4j
@Component
public final class EnvironmentGuard {

    public enum Env { PROD, DEV }

    private final Env env;

    public EnvironmentGuard(@Value("${ai-user.env:}") String env,
                            @Value("${spring.datasource.url}") String dbUrl,
                            @Value("${ai-user.backend-base-url:}") String backendBaseUrl) {
        this.env = validate(env, dbUrl, backendBaseUrl);
        log.info("[EnvironmentGuard] env={} db={} backend={}", this.env, hostOfJdbc(dbUrl), hostOf(backendBaseUrl));
    }

    public Env env() { return env; }

    static Env validate(String env, String dbUrl, String backendBaseUrl) {
        if (env == null || env.isBlank()) {
            throw new IllegalStateException("AI_USER_ENV is required (prod|dev). Refusing to start.");
        }
        Env parsed;
        try {
            parsed = Env.valueOf(env.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("AI_USER_ENV must be prod|dev, got: " + env);
        }
        if (backendBaseUrl == null || backendBaseUrl.isBlank()) {
            throw new IllegalStateException("BACKEND_BASE_URL is required (no default). Refusing to start.");
        }
        String suffix = "-" + parsed.name().toLowerCase(Locale.ROOT);
        String dbHost = hostOfJdbc(dbUrl);
        String beHost = hostOf(backendBaseUrl);
        if (!dbHost.endsWith("mariadb" + suffix)) {
            throw new IllegalStateException("AI_USER_ENV=" + parsed + " but DB host is " + dbHost);
        }
        if (!beHost.endsWith("backend" + suffix)) {
            throw new IllegalStateException("AI_USER_ENV=" + parsed + " but backend host is " + beHost);
        }
        return parsed;
    }

    private static String hostOfJdbc(String jdbcUrl) {
        // jdbc:mariadb://host:port/db?... → host
        String s = jdbcUrl.replaceFirst("^jdbc:", "");
        return hostOf(s);
    }

    private static String hostOf(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? "" : h;
        } catch (Exception e) {
            return "";
        }
    }
}
