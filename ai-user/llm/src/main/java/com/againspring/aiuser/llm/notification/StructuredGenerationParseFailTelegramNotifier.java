package com.againspring.aiuser.llm.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Operational alerts for PARSE_FAIL rate spikes in structured generation (worker side).
 *
 * <p>Reuses the same pattern as {@link com.againspring.aiuser.orchestrator.notification.StructuredGenerationFailureTelegramNotifier}:
 * RestClient + env var injection + silent skip if unconfigured.
 *
 * <p>Note: This is a duplicate of the orchestrator-side notifier, following the LlmStatsLogger precedent
 * of not crossing module boundaries. Worker-side rate limiting + notification are independent.
 */
@Slf4j
@Component
public class StructuredGenerationParseFailTelegramNotifier {

    private final String botToken;
    private final String chatId;
    private final RestClient restClient;

    public StructuredGenerationParseFailTelegramNotifier(
            @Value("${TELEGRAM_BOT_TOKEN:}") String botToken,
            @Value("${TELEGRAM_CHAT_ID:}") String chatId,
            RestClient.Builder restClientBuilder) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.restClient = restClientBuilder.baseUrl("https://api.telegram.org").build();
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
            "⚠️ [Again-Spring] 구조화 생성 PARSE_FAIL 급증 (워커)%n"
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
            log.debug("[telegram] PARSE_FAIL alert skipped: Telegram is not configured");
            return;
        }
        try {
            restClient.post().uri("/bot{token}/sendMessage", botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", message))
                .retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("[telegram] PARSE_FAIL alert delivery failed: {}", e.getMessage());
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "없음" : limit(value.replaceAll("[\\r\\n\\t]+", " ").trim(), 500);
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
