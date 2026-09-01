package com.againspring.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramNotifierTest {

    @Test
    void truncateCaption_capsAt1024() {
        String longText = "가".repeat(1100);
        String out = TelegramNotifier.truncateCaption(longText);
        assertThat(out.length()).isEqualTo(TelegramNotifier.CAPTION_MAX);
        assertThat(out).endsWith("…");
    }

    @Test
    void messageIdFromOkBody() {
        TelegramNotifier n = new TelegramNotifier("t", "1", false, org.springframework.web.client.RestClient.builder(), new ObjectMapper());
        Optional<Long> id = n.messageIdFromBody("{\"ok\":true,\"result\":{\"message_id\":42}}");
        assertThat(id).contains(42L);
        assertThat(n.messageIdFromBody("{\"ok\":false}")).isEmpty();
    }
}
