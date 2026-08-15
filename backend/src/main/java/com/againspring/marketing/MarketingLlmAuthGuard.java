package com.againspring.marketing;

import com.againspring.notification.TelegramNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker for LLM authentication errors in marketing pipeline.
 *
 * <p>Decision #6: Authentication errors are the only exception to the retry-all rule (Decision #1).
 * If the Claude session expires, no amount of retrying will fix it — only manual re-authentication.
 * This guard detects consecutive authentication failures and immediately trips a circuit that:
 *
 * <p>1. Sends urgent Telegram notification after 2 consecutive failures
 * 2. Blocks further LLM calls for a cooldown period or until manual reset
 * 3. Allows recovery via manual re-auth or automatic timeout
 */
@Slf4j
@Component
public class MarketingLlmAuthGuard {

    private final TelegramNotifier telegramNotifier;

    private final AtomicInteger consecutiveAuthErrors = new AtomicInteger(0);
    private final AtomicReference<Instant> circuitOpenedAt = new AtomicReference<>(null);
    private final AtomicReference<Instant> lastAuthErrorAt = new AtomicReference<>(null);

    // 회로가 열려 있는 기간: 5분 (수동 해제될 때까지, 또는 자동 복구)
    private static final long CIRCUIT_OPEN_COOLDOWN_MS = 5 * 60 * 1000L;
    // 연속 카운트 타임아웃: 2분 (이 시간 이상 경과하면 카운트 리셋)
    private static final long CONSECUTIVE_ERROR_TIMEOUT_MS = 2 * 60 * 1000L;
    // 긴급 알림을 보낼 임계값: 2회 연속
    private static final int AUTH_ERROR_THRESHOLD = 2;

    public MarketingLlmAuthGuard(TelegramNotifier telegramNotifier) {
        this.telegramNotifier = telegramNotifier;
    }

    /**
     * Check if an error message indicates authentication/session failure.
     * Uses LlmErrorSignature-compatible pattern matching.
     */
    public boolean isAuthenticationError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return false;
        }
        String lower = errorMessage.toLowerCase();
        // Match LlmErrorSignature's authentication patterns
        return lower.contains("authentication_error") ||
               lower.contains("authentication") ||
               lower.contains("session") ||
               lower.contains("unauthorized") ||
               lower.contains("forbidden") ||
               lower.contains("authentication failed") ||
               lower.contains("invalid credentials") ||
               lower.contains("not authenticated");
    }

    /**
     * Record an authentication error and return true if circuit should open (2+ consecutive).
     * Also sends urgent alert when threshold is crossed.
     */
    public boolean recordAuthError(String errorDetail) {
        Instant now = Instant.now();
        Instant lastError = lastAuthErrorAt.get();

        // If more than timeout has passed since last error, reset counter
        if (lastError != null && now.toEpochMilli() - lastError.toEpochMilli() > CONSECUTIVE_ERROR_TIMEOUT_MS) {
            consecutiveAuthErrors.set(0);
            log.info("Authentication error counter reset (timeout)");
        }

        int newCount = consecutiveAuthErrors.incrementAndGet();
        lastAuthErrorAt.set(now);

        log.warn("Authentication error #{}: {}", newCount, errorDetail);

        // Open circuit on threshold (2)
        if (newCount >= AUTH_ERROR_THRESHOLD) {
            Instant openedAt = circuitOpenedAt.get();
            if (openedAt == null) {
                circuitOpenedAt.set(now);
                log.error("🚨 LLM authentication circuit OPENED — marketing LLM calls will be blocked");

                // Send urgent Telegram alert
                sendAuthenticationAlert(newCount, errorDetail, now);
            }
            return true;
        }

        return false;
    }

    /**
     * Check if the circuit is currently open (blocking LLM calls).
     */
    public boolean isCircuitOpen() {
        Instant openedAt = circuitOpenedAt.get();
        if (openedAt == null) {
            return false;
        }

        Instant now = Instant.now();
        long elapsedMs = now.toEpochMilli() - openedAt.toEpochMilli();

        // Auto-recover after cooldown period
        if (elapsedMs > CIRCUIT_OPEN_COOLDOWN_MS) {
            log.info("LLM authentication circuit auto-recovered after cooldown ({}ms)", elapsedMs);
            reset();
            return false;
        }

        return true;
    }

    /**
     * Manually reset the circuit (called after successful re-authentication).
     */
    public void reset() {
        consecutiveAuthErrors.set(0);
        circuitOpenedAt.set(null);
        lastAuthErrorAt.set(null);
        log.info("LLM authentication circuit manually RESET");
    }

    /**
     * Send urgent Telegram notification for authentication failure.
     */
    private void sendAuthenticationAlert(int errorCount, String errorDetail, Instant detectedAt) {
        String message = String.format(
            "🚨 [긴급] Claude 세션 만료 — 수동 재인증 필요%n" +
            "%n" +
            "감지    %s KST · 연속 %d회%n" +
            "경로    마케팅 LLM (againspring-llm:8090)%n" +
            "오류    %s%n" +
            "영향    마케팅 발행 + AI-user 동시 중단 (계정 공유)%n" +
            "%n" +
            "조치%n" +
            " 1) WSL 터미널에서: claude   (브라우저 로그인)%n" +
            " 2) cd env && docker compose restart againspring-llm",
            formatKstTime(detectedAt),
            errorCount,
            compact(errorDetail, 100));

        telegramNotifier.send(message);
    }

    private static String formatKstTime(Instant instant) {
        java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.of("Asia/Seoul"));
        return java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(zdt);
    }

    private static String compact(String value, int maxLength) {
        if (value == null) return null;
        String compacted = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compacted.length() <= maxLength ? compacted : compacted.substring(0, maxLength) + "…";
    }

    /**
     * Get current guard state (for monitoring/testing).
     */
    public GuardState getState() {
        return new GuardState(
            consecutiveAuthErrors.get(),
            circuitOpenedAt.get(),
            isCircuitOpen()
        );
    }

    /**
     * Immutable guard state snapshot.
     */
    public record GuardState(int consecutiveErrors, Instant circuitOpenedAt, boolean isCircuitOpen) {}
}
