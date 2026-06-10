package com.againspring.api.admin;

import com.againspring.marketing.AsmClient;
import com.againspring.marketing.MarketingJobService;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the credential proxy endpoints on {@link AdminMarketingController}.
 * The endpoints are thin pass-throughs to ASM (which owns encryption + validation),
 * so this guards the wiring/delegation rather than business logic.
 */
@ExtendWith(MockitoExtension.class)
class AdminMarketingCredentialControllerTest {

    @Mock
    MarketingJobService marketingJobService;
    @Mock
    MarketingJobRepository marketingJobRepository;
    @Mock
    AsmClient asmClient;

    @InjectMocks
    AdminMarketingController controller;

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void listCredentials_delegatesToAsmAndReturnsPayload() {
        JsonNode payload = om.createArrayNode();
        when(asmClient.listCredentials()).thenReturn(payload);

        ResponseEntity<JsonNode> resp = controller.listCredentials();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isSameAs(payload);
        verify(asmClient).listCredentials();
    }

    @Test
    void upsertCredential_forwardsPlatformAndBody() {
        ObjectNode body = om.createObjectNode();
        body.set("values", om.createObjectNode().put("handle", "@me"));
        JsonNode result = om.createObjectNode().put("configured", true);
        when(asmClient.upsertCredential(eq("x"), any(JsonNode.class))).thenReturn(result);

        ResponseEntity<JsonNode> resp = controller.upsertCredential("x", body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isSameAs(result);
        verify(asmClient).upsertCredential("x", body);
    }

    @Test
    void deleteCredential_returns204AndDelegates() {
        ResponseEntity<Void> resp = controller.deleteCredential("threads");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(asmClient).deleteCredential("threads");
    }
}
