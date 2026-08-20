package com.againspring.api.internal;

import com.againspring.domain.marketing.MarketingJob;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
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

    // ─────────────────────────────────────────────────────────────
    // Internal Redrive Tests (Identity Validation)
    // ─────────────────────────────────────────────────────────────

    @Test
    void redrive_missingToken_returns401() {
        MarketingCallbackController.InternalRedriveRequest req =
            new MarketingCallbackController.InternalRedriveRequest(698L, "01M07K...", false);

        ResponseEntity<Map<String, Object>> response = controller.redrive(null, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(marketingJobService, never()).findJobById(any());
    }

    @Test
    void redrive_invalidToken_returns401() {
        MarketingCallbackController.InternalRedriveRequest req =
            new MarketingCallbackController.InternalRedriveRequest(698L, "01M07K...", false);

        ResponseEntity<Map<String, Object>> response = controller.redrive("Bearer wrong-token", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(marketingJobService, never()).findJobById(any());
    }

    @Test
    void redrive_missingRemoteJobId_returns400() {
        MarketingCallbackController.InternalRedriveRequest req =
            new MarketingCallbackController.InternalRedriveRequest(698L, null, false);

        ResponseEntity<Map<String, Object>> response = controller.redrive("Bearer " + VALID_TOKEN, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(marketingJobService, never()).findJobById(any());
    }

    @Test
    void redrive_blankRemoteJobId_returns400() {
        MarketingCallbackController.InternalRedriveRequest req =
            new MarketingCallbackController.InternalRedriveRequest(698L, "   ", false);

        ResponseEntity<Map<String, Object>> response = controller.redrive("Bearer " + VALID_TOKEN, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(marketingJobService, never()).findJobById(any());
    }

    @Test
    void redrive_jobNotFound_returns404() {
        MarketingCallbackController.InternalRedriveRequest req =
            new MarketingCallbackController.InternalRedriveRequest(999L, "01M07K...", false);
        when(marketingJobService.findJobById(999L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.redrive("Bearer " + VALID_TOKEN, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(marketingJobService, never()).redriveJobs(anyList(), anyBoolean(), anyString());
    }

    @Test
    void redrive_identityMismatch_returns409() {
        MarketingJob job = MarketingJob.builder()
            .id(698L)
            .remoteJobId("01KZZ30T74V3SB8WQW7VA5AEC1")  // Expected
            .postId("post123")
            .status("FAILED")
            .build();
        when(marketingJobService.findJobById(698L)).thenReturn(Optional.of(job));

        MarketingCallbackController.InternalRedriveRequest req =
            new MarketingCallbackController.InternalRedriveRequest(698L, "01M07K_DIFFERENT_ID", false);

        ResponseEntity<Map<String, Object>> response = controller.redrive("Bearer " + VALID_TOKEN, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(marketingJobService, never()).redriveJobs(anyList(), anyBoolean(), anyString());
    }

    @Test
    void redrive_validIdentity_procedsAndReturnsResults() {
        MarketingJob job = MarketingJob.builder()
            .id(698L)
            .remoteJobId("01M07K74V3SB8WQW7VA5AEC1")
            .postId("post123")
            .status("FAILED")
            .build();
        when(marketingJobService.findJobById(698L)).thenReturn(Optional.of(job));

        List<Map<String, Object>> mockResults = List.of(
            Map.of("sourceId", 698L, "targetId", 699L, "action", "REGENERATED")
        );
        when(marketingJobService.redriveJobs(List.of(698L), false, "asm:telegram-redrive"))
            .thenReturn(mockResults);

        MarketingCallbackController.InternalRedriveRequest req =
            new MarketingCallbackController.InternalRedriveRequest(698L, "01M07K74V3SB8WQW7VA5AEC1", false);

        ResponseEntity<Map<String, Object>> response = controller.redrive("Bearer " + VALID_TOKEN, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("requested", "results");
        assertThat(response.getBody().get("requested")).isEqualTo(1);
        assertThat(response.getBody().get("results")).isEqualTo(mockResults);
        verify(marketingJobService).redriveJobs(List.of(698L), false, "asm:telegram-redrive");
    }

    @Test
    void redrive_skipExistingTrue_passesFlagToService() {
        MarketingJob job = MarketingJob.builder()
            .id(700L)
            .remoteJobId("01M07K74V3SB8WQW7VA5AEC2")
            .postId("post456")
            .status("READY")
            .build();
        when(marketingJobService.findJobById(700L)).thenReturn(Optional.of(job));

        List<Map<String, Object>> mockResults = List.of(
            Map.of("sourceId", 700L, "action", "SKIPPED", "reason", "Already published")
        );
        when(marketingJobService.redriveJobs(List.of(700L), true, "asm:telegram-redrive"))
            .thenReturn(mockResults);

        MarketingCallbackController.InternalRedriveRequest req =
            new MarketingCallbackController.InternalRedriveRequest(700L, "01M07K74V3SB8WQW7VA5AEC2", true);

        ResponseEntity<Map<String, Object>> response = controller.redrive("Bearer " + VALID_TOKEN, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(marketingJobService).redriveJobs(List.of(700L), true, "asm:telegram-redrive");
    }
}
