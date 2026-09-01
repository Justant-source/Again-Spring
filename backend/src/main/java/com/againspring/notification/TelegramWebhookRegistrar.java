package com.againspring.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registers the Telegram webhook on boot when URL and secret are set (prod).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramWebhookRegistrar {

    private final TelegramNotifier telegramNotifier;

    @Value("${TELEGRAM_WEBHOOK_URL:}")
    private String webhookUrl;

    @Value("${TELEGRAM_WEBHOOK_SECRET:}")
    private String webhookSecret;

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        if (webhookUrl == null || webhookUrl.isBlank() || webhookSecret == null || webhookSecret.isBlank()) {
            log.debug("[telegram] webhook not registered (url/secret unset)");
            return;
        }
        telegramNotifier.setWebhook(webhookUrl.trim(), webhookSecret);
    }
}
