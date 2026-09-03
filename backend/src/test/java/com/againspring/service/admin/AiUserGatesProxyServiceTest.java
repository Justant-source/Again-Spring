package com.againspring.service.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiUserGatesProxyServiceTest {

    private static final String ORCHESTRATOR_URL = "http://ai-user-orchestrator-test";

    @Test
    void unreachableOrchestratorMapsToBadGateway() {
        AiUserGatesProxyService svc = new AiUserGatesProxyService("http://127.0.0.1:1");
        assertThatThrownBy(svc::effectiveGates)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void orchestrator404MapsToBadGateway() {
        RestClient.Builder builder = RestClient.builder().baseUrl(ORCHESTRATOR_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        server.expect(requestTo(ORCHESTRATOR_URL + "/admin/trigger/effective-gates"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        AiUserGatesProxyService svc = new AiUserGatesProxyService(ORCHESTRATOR_URL, restClient);
        assertThatThrownBy(svc::effectiveGates)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void passesThroughOrchestratorBodyVerbatim() {
        RestClient.Builder builder = RestClient.builder().baseUrl(ORCHESTRATOR_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        String json = "{"
                + "\"generationAllowed\":false,"
                + "\"publishingAllowed\":true,"
                + "\"reasons\":[\"kill switch on\"],"
                + "\"gates\":[{\"name\":\"aiUserKillSwitch\",\"source\":\"db\",\"value\":true,\"blocks\":\"generation\"}],"
                + "\"stale\":false"
                + "}";
        server.expect(requestTo(ORCHESTRATOR_URL + "/admin/trigger/effective-gates"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        AiUserGatesProxyService svc = new AiUserGatesProxyService(ORCHESTRATOR_URL, restClient);
        Map<String, Object> result = svc.effectiveGates();

        assertThat(result.get("generationAllowed")).isEqualTo(false);
        assertThat(result.get("publishingAllowed")).isEqualTo(true);
        assertThat(result).containsKeys("reasons", "gates", "stale");
    }
}
