package com.againspring.aiuser.orchestrator.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Operational alerts for structured-generation bundle losses and parse-failure rate spikes.
 *
 * <p>Two failure modes are alerted:
 * <ul>
 *   <li><strong>Bundle Lost (hard failure)</strong>: Structured call failed after retry → no post/comments.
 *       Alert immediately.</li>
 *   <li><strong>PARSE_FAIL (recoverable)</strong>: First attempt failed to parse but retry succeeded.
 *       Alert when count crosses threshold (e.g. 3 within 30 min), then cooldown (6h).</li>
 * </ul>
 *
 * <p>Reuses {@link ScheduledPostTelegramNotifier} pattern: RestClient + env var injection + silent skip if unconfigured.
 */
@Slf4j
@Component
public class StructuredGenerationFailureTelegramNotifier {

    private final String botToken;
    private final String chatId;
    private final RestClient restClient;

    public StructuredGenerationFailureTelegramNotifier(
            @Value("${TELEGRAM_BOT_TOKEN:}") String botToken,
            @Value("${TELEGRAM_CHAT_ID:}") String chatId,
            RestClient.Builder restClientBuilder) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.restClient = restClientBuilder.baseUrl("https://api.telegram.org").build();
    }

    /**
     * Alert on bundle loss (hard failure): structured call failed after retry → no post/comments.
     *
     * @param correlationId Correlation ID for tracing
     * @param detail Failure reason (e.g. "LLM or safety rejected")
     * @param env Environment (dev/prod)
     * @param snippet Truncated snippet of attempted generation (if available)
     */
    public void bundleLost(String correlationId, String detail, String env, String snippet) {
        String body = String.format(
            "❌ [Again-Spring] 구조화 생성 번들 손실%n"
                + "환경: %s%n"
                + "상태: 최종 실패 (재시도 후)%n"
                + "상관ID: %s%n"
                + "사유: %s%n"
                + "일부내용: %s%n"
                + "복구: LLM_STRUCTURED_PROMPT_MODE=false + 재빌드/재시작",
            env != null ? env : "unknown",
            text(correlationId),
            text(detail),
            text(snippet));
        send(limit(body, 3500));
    }

    /**
     * Alert on PARSE_FAIL rate spike: first attempt failed to parse but retry succeeded.
     * Alerts once when count crosses threshold, then respects cooldown period.
     *
     * @param threshold Alert threshold (e.g. 3 failures)
     * @param count Current count in window
     * @param windowMinutes Window duration in minutes
     * @param cooldownMinutes Cooldown duration in minutes (next alert suppressed)
     * @param env Environment (dev/prod)
     * @param snippet Truncated snippet from failed parses
     */
    public void parseFailureRateAlert(int threshold, int count, int windowMinutes, int cooldownMinutes,
                                      String env, String snippet) {
        String body = String.format(
            "⚠️ [Again-Spring] 구조화 생성 PARSE_FAIL 급증%n"
                + "환경: %s%n"
                + "임계값: %d건/%d분 도달%n"
                + "현재: %d건/%d분%n"
                + "마지막 %d분 스니펫: %s%n"
                + "복구: LLM_STRUCTURED_PROMPT_MODE=false + 재빌드/재시작%n"
                + "쿨다운: %d시간 (재알림 억제됨)",
            env != null ? env : "unknown",
            threshold,
            windowMinutes,
            count,
            windowMinutes,
            windowMinutes,
            text(snippet),
            cooldownMinutes / 60);
        send(limit(body, 3500));
    }

    private void send(String message) {
        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            log.debug("[telegram] Structured-generation alert skipped: Telegram is not configured");
            return;
        }
        try {
            restClient.post().uri("/bot{token}/sendMessage", botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", message))
                .retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("[telegram] Structured-generation alert delivery failed: {}", e.getMessage());
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "없음" : limit(value.replaceAll("[\\r\\n\\t]+", " ").trim(), 500);
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
