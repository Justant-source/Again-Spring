package com.againspring.api.internal;

import com.againspring.marketing.XPersonaDrillService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Telegram Bot API webhook. Secret header is compared in constant time; chat allowlist
 * is enforced inside {@link XPersonaDrillService}. Returns 200 immediately so /drill
 * ASM scrapes do not trip Telegram's retry timer.
 */
@RestController
@RequestMapping("/api/internal/telegram")
@Slf4j
public class TelegramWebhookController {

    public static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final XPersonaDrillService xPersonaDrillService;
    private final String webhookSecret;
    private final Executor drillExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "x-persona-drill");
        t.setDaemon(true);
        return t;
    });

    public TelegramWebhookController(
            XPersonaDrillService xPersonaDrillService,
            @Value("${TELEGRAM_WEBHOOK_SECRET:}") String webhookSecret) {
        this.xPersonaDrillService = xPersonaDrillService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody JsonNode body) {
        if (!secretMatches(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        drillExecutor.execute(() -> {
            try {
                xPersonaDrillService.handleUpdate(body);
            } catch (Exception e) {
                log.warn("[telegram-webhook] handle failed: {}", e.getMessage());
            }
        });
        return ResponseEntity.ok().build();
    }

    boolean secretMatches(String incoming) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        byte[] a = webhookSecret.getBytes(StandardCharsets.UTF_8);
        byte[] b = incoming == null ? new byte[0] : incoming.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        byte result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
