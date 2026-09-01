package com.againspring.api.internal;

import com.againspring.marketing.XPersonaDrillService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TelegramWebhookControllerTest {

    private XPersonaDrillService drillService;
    private TelegramWebhookController controller;

    @BeforeEach
    void setUp() {
        drillService = mock(XPersonaDrillService.class);
        controller = new TelegramWebhookController(drillService, "s3cret");
    }

    @Test
    void missingOrWrongSecret_isUnauthorized() throws Exception {
        var body = new ObjectMapper().readTree("{}");
        ResponseEntity<Void> missing = controller.receive(null, body);
        ResponseEntity<Void> wrong = controller.receive("nope", body);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(drillService);
    }

    @Test
    void matchingSecret_returns200AndHandlesAsync() throws Exception {
        var body = new ObjectMapper().readTree("{\"update_id\":1}");
        ResponseEntity<Void> ok = controller.receive("s3cret", body);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(drillService, timeout(2000)).handleUpdate(any());
    }

    @Test
    void blankConfiguredSecret_neverMatches() {
        TelegramWebhookController empty = new TelegramWebhookController(drillService, "");
        assertThat(empty.secretMatches("")).isFalse();
        assertThat(empty.secretMatches("x")).isFalse();
    }
}
