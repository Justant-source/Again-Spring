package com.againspring.api.internal;

import com.againspring.marketing.AsmProperties;
import com.againspring.marketing.MarketingJobService;
import com.againspring.marketing.dto.JobCallbackPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring MVC tests for {@link MarketingCallbackController}.
 * Tests the POST /api/internal/marketing/callback endpoint with token-based authentication.
 */
@WebMvcTest(MarketingCallbackController.class)
class MarketingCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketingJobService marketingJobService;

    @MockBean
    private AsmProperties asmProperties;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String CALLBACK_URL = "/api/internal/marketing/callback";
    private static final String VALID_TOKEN = "test-callback-token";

    // ── Test 1: callback_validToken_returns204 ─────────────────────────────

    @Test
    void callback_validToken_returns204() throws Exception {
        // Given
        when(asmProperties.getCallbackToken()).thenReturn(VALID_TOKEN);

        JobCallbackPayload payload = JobCallbackPayload.builder()
            .jobId("remote-123")
            .status("PUBLISHED")
            .event("PUBLISHED")
            .build();

        String requestBody = objectMapper.writeValueAsString(payload);

        // When / Then
        mockMvc.perform(post(CALLBACK_URL)
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isNoContent());

        verify(marketingJobService).applyCallback(any(JobCallbackPayload.class));
    }

    // ── Test 2: callback_invalidToken_returns401 ───────────────────────────

    @Test
    void callback_invalidToken_returns401() throws Exception {
        // Given
        when(asmProperties.getCallbackToken()).thenReturn(VALID_TOKEN);

        JobCallbackPayload payload = JobCallbackPayload.builder()
            .jobId("remote-123")
            .status("PUBLISHED")
            .event("PUBLISHED")
            .build();

        String requestBody = objectMapper.writeValueAsString(payload);

        // When / Then — wrong token should be rejected
        mockMvc.perform(post(CALLBACK_URL)
                .header("Authorization", "Bearer " + "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized());
    }

    // ── Test 3: callback_missingToken_returns401 ───────────────────────────

    @Test
    void callback_missingToken_returns401() throws Exception {
        // Given
        when(asmProperties.getCallbackToken()).thenReturn(VALID_TOKEN);

        JobCallbackPayload payload = JobCallbackPayload.builder()
            .jobId("remote-123")
            .status("PUBLISHED")
            .event("PUBLISHED")
            .build();

        String requestBody = objectMapper.writeValueAsString(payload);

        // When / Then — missing Authorization header should be rejected
        mockMvc.perform(post(CALLBACK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized());
    }
}
