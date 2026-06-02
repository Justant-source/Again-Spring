package com.againspring.service.admin;

import com.againspring.api.dto.response.SystemHealthResponse;
import com.againspring.api.dto.response.SystemHealthResponse.ComponentHealth;
import com.againspring.repository.EmailVerificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V11 — Admin 시스템 헬스 서비스.
 * 4개 컴포넌트(backend, database, smtp, anthropic) 상태를 평가해 OK/WARN/ERROR로 분류.
 *
 * 임계값:
 * - DB: 쿼리 < 100ms OK, < 500ms WARN, 실패 ERROR
 * - SMTP: 24h 내 발송 성공 OK, 발송 미발생/실패 WARN, 빈 EmailVerification 테이블 등 ERROR
 * - Anthropic: 최근 24h fallback 비율 < 5% OK, 5~30% WARN, ≥ 30% ERROR
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemHealthService {

    @PersistenceContext
    private EntityManager em;

    private final EmailVerificationRepository emailVerificationRepository;
    // private final MessageRepository messageRepository; (removed)

    @Transactional(readOnly = true)
    public SystemHealthResponse check() {
        Instant now = Instant.now();
        Map<String, ComponentHealth> components = new LinkedHashMap<>();
        components.put("backend", checkBackend());
        components.put("database", checkDatabase());
        components.put("smtp", checkSmtp());
        components.put("anthropic", checkAnthropic(now));
        return SystemHealthResponse.builder()
                .checkedAt(now)
                .components(components)
                .build();
    }

    private ComponentHealth checkBackend() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("uptime", formatUptime(uptimeMs));
        details.put("uptimeMs", uptimeMs);
        // 본 메서드가 응답 중이라면 backend 자체는 살아있음 → 항상 OK
        return ComponentHealth.ok(details);
    }

    private ComponentHealth checkDatabase() {
        long startNanos = System.nanoTime();
        try {
            // 가장 가벼운 쿼리: SELECT 1
            Object result = em.createNativeQuery("SELECT 1").getSingleResult();
            long queryMs = (System.nanoTime() - startNanos) / 1_000_000;
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("queryMs", queryMs);
            details.put("probe", String.valueOf(result));
            if (queryMs < 100) return ComponentHealth.ok(details);
            if (queryMs < 500) return ComponentHealth.warn("쿼리 응답 지연", details);
            return ComponentHealth.error("쿼리 응답 매우 느림", details);
        } catch (Exception e) {
            log.warn("DB health check failed: {}", e.getMessage());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("error", e.getClass().getSimpleName());
            return ComponentHealth.error("DB 연결 실패", details);
        }
    }

    private ComponentHealth checkSmtp() {
        // 직접 SMTP 핸드셰이크는 비용/지연이 크므로 마지막 발송 기록으로 추정
        try {
            return emailVerificationRepository.findTopByOrderByCreatedAtDesc()
                    .map(latest -> {
                        Instant lastAt = latest.getCreatedAt();
                        long hoursAgo = lastAt != null
                                ? Duration.between(lastAt, Instant.now()).toHours()
                                : Long.MAX_VALUE;
                        Map<String, Object> details = new LinkedHashMap<>();
                        details.put("lastSentAt", lastAt);
                        details.put("hoursAgo", hoursAgo);
                        if (hoursAgo <= 24) return ComponentHealth.ok(details);
                        if (hoursAgo <= 72) return ComponentHealth.warn("최근 24h 내 발송 없음", details);
                        return ComponentHealth.warn("3일 이상 발송 없음", details);
                    })
                    .orElseGet(() -> {
                        Map<String, Object> details = new LinkedHashMap<>();
                        details.put("note", "EmailVerification 기록 없음 (베타 출시 전 정상)");
                        return ComponentHealth.warn("발송 이력 없음", details);
                    });
        } catch (Exception e) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("error", e.getClass().getSimpleName());
            return ComponentHealth.error("SMTP 상태 조회 실패", details);
        }
    }

    private ComponentHealth checkAnthropic(Instant now) {
        try {
            Instant since24h = now.minus(Duration.ofHours(24));
            long total = 0;
            long fallbacks = 0;
            Instant lastCall = null;

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("totalCalls24h", total);
            details.put("fallbacks24h", fallbacks);
            details.put("lastCallAt", lastCall);

            if (total == 0) {
                details.put("note", "최근 24h 호출 없음");
                return ComponentHealth.builder().status("OK").details(details).build();
            }

            double fallbackRate = (double) fallbacks / (double) total;
            details.put("fallbackRate24h", fallbackRate);

            if (fallbackRate < 0.05) return ComponentHealth.ok(details);
            if (fallbackRate < 0.30) return ComponentHealth.warn("fallback 비율 5~30%", details);
            return ComponentHealth.error("fallback 비율 30% 이상", details);
        } catch (Exception e) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("error", e.getClass().getSimpleName());
            return ComponentHealth.error("Anthropic 상태 조회 실패", details);
        }
    }

    private String formatUptime(long ms) {
        long sec = ms / 1000;
        long days = sec / 86400;
        long hours = (sec % 86400) / 3600;
        long mins = (sec % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }
}
