package com.againspring.notification;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends operational alerts to the shared @WaggleBot_bot Telegram chat. Token/chat id come from
 * the encrypted_secret vault (telegram.bot_token / telegram.chat_id) via {@code @Value} injection
 * — see {@code SecretVaultKeys}. Silently no-ops if unconfigured (never blocks the caller).
 */
@Slf4j
@Component
public class TelegramNotifier {

    private final String botToken;
    private final String chatId;
    private final RestClient restClient;

    public TelegramNotifier(
            @Value("${TELEGRAM_BOT_TOKEN:}") String botToken,
            @Value("${TELEGRAM_CHAT_ID:}") String chatId,
            RestClient.Builder restClientBuilder) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.restClient = restClientBuilder.baseUrl("https://api.telegram.org").build();
    }

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
    }

    /** Fire-and-forget — logs and swallows failures so alerting never breaks the caller's flow. */
    public void send(String text) {
        if (!isConfigured()) {
            log.debug("[telegram] not configured, skipping alert: {}", text);
            return;
        }
        try {
            restClient
                    .post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", chatId, "text", text))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[telegram] alert sent");
        } catch (Exception e) {
            log.warn("[telegram] failed to send alert: {}", e.getMessage());
        }
    }
}
