package com.againspring.aiuser.orchestrator.persona;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * llm 워커 {@code /generate/persona-profile} 호출 결과를 {@link PersonaProfileLlmClient.ProfileResult}로
 * 감싸 실패 원인 텍스트를 보존하는지 검증한다 — PersonaProfileRegenerator가 이 텍스트로
 * LlmErrorSignatures 매칭(한도·인증·거절 판별)을 하므로 여기서 사라지면 안 된다.
 */
class PersonaProfileLlmClientTest {

    private static PersonaQuotaPlanner.IdentityAxes axes() {
        return new PersonaQuotaPlanner.IdentityAxes(30, "F", "SINGLE", null, false, "CORP_LARGE", "REGULAR",
                Map.of("speech", "BANMAL"));
    }

    @Test
    void successResponseIsWrappedWithoutErrorText() {
        RestClient.Builder b = RestClient.builder().baseUrl("http://llm");
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://llm/generate/persona-profile"))
                .andRespond(withSuccess("{\"job_title\":\"디자이너\"}", MediaType.APPLICATION_JSON));
        PersonaProfileLlmClient client = new PersonaProfileLlmClient(b.build());

        PersonaProfileLlmClient.ProfileResult result = client.generatePersonaProfile(
                "p1", "nick", axes(), "", "", List.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.response().get("job_title")).isEqualTo("디자이너");
        assertThat(result.errorText()).isNull();
        server.verify();
    }

    @Test
    void okResponseWithErrorCodeFieldIsTreatedAsFailureWithMessage() {
        RestClient.Builder b = RestClient.builder().baseUrl("http://llm");
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://llm/generate/persona-profile"))
                .andRespond(withSuccess(
                        "{\"errorCode\":\"PERSONA_PROFILE_INVALID\",\"message\":\"signature_phrases must have >= 6 items\"}",
                        MediaType.APPLICATION_JSON));
        PersonaProfileLlmClient client = new PersonaProfileLlmClient(b.build());

        PersonaProfileLlmClient.ProfileResult result = client.generatePersonaProfile(
                "p1", "nick", axes(), "", "", List.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorText()).contains("PERSONA_PROFILE_INVALID").contains("signature_phrases");
        server.verify();
    }

    @Test
    void httpErrorStatusPreservesResponseBodyAsErrorText() {
        RestClient.Builder b = RestClient.builder().baseUrl("http://llm");
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://llm/generate/persona-profile"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"errorCode\":\"CAPACITY\",\"message\":\"You've hit your session limit\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        PersonaProfileLlmClient client = new PersonaProfileLlmClient(b.build());

        PersonaProfileLlmClient.ProfileResult result = client.generatePersonaProfile(
                "p1", "nick", axes(), "", "", List.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorText()).contains("hit your session limit");
        server.verify();
    }
}
