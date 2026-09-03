package com.againspring.aiuser.orchestrator.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class BackendInternalClientTest {
    @Test
    void upsertSendsBearerAndParsesStatus() {
        RestClient.Builder b = RestClient.builder().baseUrl("http://be");
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://be/api/internal/ai-user/personas/upsert"))
              .andExpect(header("Authorization", "Bearer tok"))
              .andExpect(jsonPath("$.id").value("p1"))
              .andRespond(withSuccess("{\"id\":\"p1\",\"status\":\"CREATED\"}", MediaType.APPLICATION_JSON));
        BackendInternalClient c = new BackendInternalClient(b.build(), "tok");
        assertEquals("CREATED", c.upsertPersona("p1", "e", "n", "pw").orElseThrow());
        server.verify();
    }

    @Test
    void upsertReturnsDeletedSkippedStatus() {
        RestClient.Builder b = RestClient.builder().baseUrl("http://be");
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://be/api/internal/ai-user/personas/upsert"))
              .andRespond(withSuccess("{\"id\":\"p1\",\"status\":\"DELETED_SKIPPED\"}", MediaType.APPLICATION_JSON));
        BackendInternalClient c = new BackendInternalClient(b.build(), "tok");
        assertEquals("DELETED_SKIPPED", c.upsertPersona("p1", "e", "n", "pw").orElseThrow());
        server.verify();
    }

    @Test
    void upsertReturnsEmptyOnHttpFailure() {
        RestClient.Builder b = RestClient.builder().baseUrl("http://be");
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://be/api/internal/ai-user/personas/upsert"))
              .andRespond(withServerError());
        BackendInternalClient c = new BackendInternalClient(b.build(), "tok");
        assertTrue(c.upsertPersona("p1", "e", "n", "pw").isEmpty());
        server.verify();
    }

    @Test
    void rotatePasswordSendsBearerAndParsesUpdatedCount() {
        RestClient.Builder b = RestClient.builder().baseUrl("http://be");
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://be/api/internal/ai-user/personas/rotate-password"))
              .andExpect(header("Authorization", "Bearer tok"))
              .andExpect(jsonPath("$.password").value("pw"))
              .andRespond(withSuccess("{\"updated\":5}", MediaType.APPLICATION_JSON));
        BackendInternalClient c = new BackendInternalClient(b.build(), "tok");
        assertEquals(5, c.rotatePassword("pw").orElseThrow());
        server.verify();
    }

    @Test
    void rotatePasswordReturnsEmptyOnHttpFailure() {
        RestClient.Builder b = RestClient.builder().baseUrl("http://be");
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://be/api/internal/ai-user/personas/rotate-password"))
              .andRespond(withServerError());
        BackendInternalClient c = new BackendInternalClient(b.build(), "tok");
        Optional<Integer> result = c.rotatePassword("pw");
        assertTrue(result.isEmpty());
        server.verify();
    }
}
