package com.againspring.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sends operational alerts to the shared @WaggleBot_bot Telegram chat. Token/chat id come from
 * the encrypted_secret vault (telegram.bot_token / telegram.chat_id) via {@code @Value} injection
 * — see {@code SecretVaultKeys}. Silently no-ops if unconfigured (never blocks the caller).
 *
 * Supports optional inline keyboard (reply_markup) for interactive buttons.
 */
@Slf4j
@Component
public class TelegramNotifier {

    public static final int CAPTION_MAX = 1024;

    private final String botToken;
    private final String chatId;
    private final boolean buttonsEnabled;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TelegramNotifier(
            @Value("${TELEGRAM_BOT_TOKEN:}") String botToken,
            @Value("${TELEGRAM_CHAT_ID:}") String chatId,
            @Value("${MARKETING_TELEGRAM_BUTTONS_ENABLED:false}") boolean buttonsEnabled,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.buttonsEnabled = buttonsEnabled;
        this.restClient = restClientBuilder.baseUrl("https://api.telegram.org").build();
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
    }

    public String configuredChatId() {
        return chatId;
    }

    public boolean areButtonsEnabled() {
        return buttonsEnabled && isConfigured();
    }

    /** Fire-and-forget — logs and swallows failures so alerting never breaks the caller's flow. */
    public void send(String text) {
        sendAndGetMessageId(text);
    }

    /**
     * Send message with optional inline keyboard (reply_markup).
     * reply_markup is a JSON structure like:
     * {@code
     * {
     *   "inline_keyboard": [
     *     [
     *       {"text": "Button 1", "callback_data": "action_1"},
     *       {"text": "Button 2", "callback_data": "action_2"}
     *     ]
     *   ]
     * }
     * }
     *
     * @param text message text
     * @param replyMarkup inline keyboard structure (Map of Maps/Lists), or null for no keyboard
     */
    public void sendWithMarkup(String text, Map<String, Object> replyMarkup) {
        if (!isConfigured()) {
            log.debug("[telegram] not configured, skipping alert: {}", text);
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            if (replyMarkup != null && areButtonsEnabled()) {
                body.put("reply_markup", replyMarkup);
            }
            restClient
                    .post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[telegram] alert sent");
        } catch (Exception e) {
            log.warn("[telegram] failed to send alert: {}", e.getMessage());
        }
    }

    public Optional<Long> sendAndGetMessageId(String text) {
        if (!isConfigured()) {
            log.debug("[telegram] not configured, skipping alert: {}", text);
            return Optional.empty();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            String raw = restClient
                    .post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            Optional<Long> id = messageIdFromBody(raw);
            log.info("[telegram] alert sent messageId={}", id.orElse(null));
            return id;
        } catch (Exception e) {
            log.warn("[telegram] failed to send alert: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Long> sendPhoto(byte[] jpeg, String caption) {
        if (!isConfigured() || jpeg == null || jpeg.length == 0) {
            return Optional.empty();
        }
        try {
            String cap = truncateCaption(caption);
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("chat_id", chatId);
            if (cap != null && !cap.isBlank()) {
                parts.add("caption", cap);
            }
            parts.add("photo", new ByteArrayResource(jpeg) {
                @Override
                public String getFilename() {
                    return "drill.jpg";
                }
            });
            String raw = restClient
                    .post()
                    .uri("/bot{token}/sendPhoto", botToken)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(String.class);
            Optional<Long> id = messageIdFromBody(raw);
            log.info("[telegram] photo sent messageId={}", id.orElse(null));
            return id;
        } catch (Exception e) {
            log.warn("[telegram] failed to send photo: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void setWebhook(String url, String secretToken) {
        if (!isConfigured() || url == null || url.isBlank()) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("url", url);
            if (secretToken != null && !secretToken.isBlank()) {
                body.put("secret_token", secretToken);
            }
            body.put("allowed_updates", List.of("message"));
            restClient
                    .post()
                    .uri("/bot{token}/setWebhook", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[telegram] webhook set url={}", url);
        } catch (Exception e) {
            log.warn("[telegram] setWebhook failed: {}", e.getMessage());
        }
    }

    public Optional<Long> messageIdFromBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode n = objectMapper.readTree(raw);
            if (!n.path("ok").asBoolean(false)) {
                return Optional.empty();
            }
            long id = n.path("result").path("message_id").asLong(0);
            return id > 0 ? Optional.of(id) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static String truncateCaption(String caption) {
        if (caption == null) {
            return "";
        }
        if (caption.length() <= CAPTION_MAX) {
            return caption;
        }
        return caption.substring(0, CAPTION_MAX - 1) + "…";
    }
}
