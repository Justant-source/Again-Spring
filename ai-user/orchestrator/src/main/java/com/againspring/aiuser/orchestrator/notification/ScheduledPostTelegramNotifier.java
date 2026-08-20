package com.againspring.aiuser.orchestrator.notification;

import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Operational alerts for posts actually published by the scheduled-post worker. */
@Slf4j
@Component
public class ScheduledPostTelegramNotifier {
    private static final String COMMUNITY_BASE_URL = "https://againspring.net/community/";

    private final String botToken;
    private final String chatId;
    private final RestClient restClient;

    public ScheduledPostTelegramNotifier(
            @Value("${TELEGRAM_BOT_TOKEN:}") String botToken,
            @Value("${TELEGRAM_CHAT_ID:}") String chatId,
            RestClient.Builder restClientBuilder) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.restClient = restClientBuilder.baseUrl("https://api.telegram.org").build();
    }

    public void published(AiScheduledPost scheduled, String postId) {
        send(String.format(
            "✅ [Again-Spring] 예약 글 게시 완료%n제목: %s%nURL: %s%s%n예약 ID: %s",
            text(scheduled.getTitle()), COMMUNITY_BASE_URL, postId, scheduled.getId()));
    }

    public void failed(AiScheduledPost scheduled, String reason, Throwable error) {
        String errorLog = error == null ? "없음" : error.getClass().getSimpleName() + ": " + text(error.getMessage());
        send(String.format(
            "❌ [Again-Spring] 예약 글 게시 실패%n제목: %s%n예약 ID: %s%n원인: %s%n에러 로그: %s",
            text(scheduled.getTitle()), scheduled.getId(), text(reason), limit(errorLog, 1200)));
    }

    /**
     * Nightly fill saved fewer than target N. Includes each slot failure reason (truncated).
     */
    public void nightlyShortfall(int targetN, int saved, int llmUsed, int llmMax, java.util.List<String> failureReasons) {
        StringBuilder body = new StringBuilder();
        body.append(String.format(
                "⚠️ [Again-Spring] 새벽 예약글 부족%n목표 N=%d, 저장=%d, LLM=%d/%d%n실패:",
                targetN, saved, llmUsed, llmMax));
        if (failureReasons == null || failureReasons.isEmpty()) {
            body.append("\n(상세 사유 없음)");
        } else {
            int i = 1;
            for (String reason : failureReasons) {
                body.append(String.format("%n%d. %s", i++, reason == null ? "-" : reason));
            }
        }
        send(limit(body.toString(), 3500));
    }

    private void send(String message) {
        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            log.debug("[telegram] Scheduled-post alert skipped: Telegram is not configured");
            return;
        }
        try {
            restClient.post().uri("/bot{token}/sendMessage", botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", message))
                .retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("[telegram] Scheduled-post alert delivery failed: {}", e.getMessage());
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "없음" : limit(value.replaceAll("[\\r\\n\\t]+", " ").trim(), 500);
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
