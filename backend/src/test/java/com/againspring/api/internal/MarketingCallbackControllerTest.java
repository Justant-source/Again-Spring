package com.againspring.api.internal;

import com.againspring.marketing.AsmProperties;
import com.againspring.marketing.MarketingJobService;
import com.againspring.marketing.dto.JobCallbackPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MarketingCallbackController}.
 * Tests the POST /api/internal/marketing/callback endpoint's token-based authentication.
 */
@ExtendWith(MockitoExtension.class)
class MarketingCallbackControllerTest {

    @Mock
    MarketingJobService marketingJobService;

    @Mock
    AsmProperties asmProperties;

    @InjectMocks
    MarketingCallbackController controller;

    private static final String VALID_TOKEN = "test-callback-token";

    @BeforeEach
    void setUp() {
        when(asmProperties.getCallbackToken()).thenReturn(VALID_TOKEN);
    }

    @Test
    void callback_validToken_returns204AndAppliesCallback() {
        JobCallbackPayload payload = JobCallbackPayload.builder()
            .jobId("remote-123")
            .status("PUBLISHED")
            .event("PUBLISHED")
            .build();

        ResponseEntity<Void> response = controller.receiveCallback(
            "Bearer " + VALID_TOKEN, payload
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(marketingJobService).applyCallback(payload);
    }

    @Test
    void callback_invalidToken_returns401() {
        JobCallbackPayload payload = JobCallbackPayload.builder()
            .jobId("remote-123")
            .status("PUBLISHED")
            .event("PUBLISHED")
            .build();

        ResponseEntity<Void> response = controller.receiveCallback(
            "Bearer wrong-token", payload
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(marketingJobService, never()).applyCallback(any());
    }

    @Test
    void callback_missingToken_returns401() {
        JobCallbackPayload payload = JobCallbackPayload.builder()
            .jobId("remote-123")
            .status("PUBLISHED")
            .event("PUBLISHED")
            .build();

        ResponseEntity<Void> response = controller.receiveCallback(null, payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(marketingJobService, never()).applyCallback(any());
    }
}
