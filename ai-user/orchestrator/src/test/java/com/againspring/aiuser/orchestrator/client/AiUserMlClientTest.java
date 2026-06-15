package com.againspring.aiuser.orchestrator.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class AiUserMlClientTest {

    private AiUserMlClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = new AiUserMlClient("http://localhost:9999", "test-token", objectMapper);
        // @Value fields default to 0/false without Spring — init explicitly
        ReflectionTestUtils.setField(client, "bestOfN", 4);
    }

    // ── disabled path ─────────────────────────────────────────────────────────

    @Test
    void rerank_whenDisabled_returnsEmpty() {
        ReflectionTestUtils.setField(client, "enabled", false);
        Optional<AiUserMlClient.RerankResponse> result = client.rerank(
            "NATEPAN", "POST", List.of(new AiUserMlClient.CandidateItem("d0", "테스트 글")));
        assertThat(result).isEmpty();
    }

    @Test
    void pushNegative_whenCollectOff_isNoOp() {
        ReflectionTestUtils.setField(client, "collect", false);
        client.pushNegative("NATEPAN", "POST", "테스트 글");
    }

    // ── guard paths (enabled but invalid input) ───────────────────────────────

    @Test
    void rerank_withEmptyCandidates_returnsEmpty() {
        ReflectionTestUtils.setField(client, "enabled", true);
        assertThat(client.rerank("NATEPAN", "POST", List.of())).isEmpty();
    }

    @Test
    void rerank_withNullCandidates_returnsEmpty() {
        ReflectionTestUtils.setField(client, "enabled", true);
        assertThat(client.rerank("NATEPAN", "POST", null)).isEmpty();
    }

    @Test
    void pushNegative_withBlankText_isNoOp() {
        ReflectionTestUtils.setField(client, "enabled", true);
        client.pushNegative("NATEPAN", "POST", "  ");
        client.pushNegative("NATEPAN", "POST", null);
    }

    // ── graceful degradation when network unreachable ─────────────────────────

    @Test
    void rerank_whenNetworkFails_returnsEmpty() {
        ReflectionTestUtils.setField(client, "enabled", true);
        // port 9999 not listening → ConnectionRefused caught → Optional.empty()
        Optional<AiUserMlClient.RerankResponse> result = client.rerank(
            "NATEPAN", "POST", List.of(new AiUserMlClient.CandidateItem("d0", "테스트 글")));
        assertThat(result).isEmpty();
    }

    @Test
    void pushNegative_whenNetworkFails_doesNotThrow() {
        ReflectionTestUtils.setField(client, "enabled", true);
        // port 9999 not listening → caught silently
        client.pushNegative("NATEPAN", "POST", "테스트 글");
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    @Test
    void rerankResponse_parsedFromCamelCaseJson() throws Exception {
        String json = """
                {
                  "winnerId": "draft-2",
                  "ranked": [
                    {"id": "draft-2", "humanProb": 0.87},
                    {"id": "draft-0", "humanProb": 0.62},
                    {"id": "draft-1", "humanProb": 0.45}
                  ],
                  "degraded": false
                }
                """;
        AiUserMlClient.RerankResponse resp = objectMapper.readValue(json, AiUserMlClient.RerankResponse.class);
        assertThat(resp.getWinnerId()).isEqualTo("draft-2");
        assertThat(resp.getRanked()).hasSize(3);
        assertThat(resp.getRanked().get(0).getHumanProb()).isCloseTo(0.87, offset(0.001));
        assertThat(resp.isDegraded()).isFalse();
    }

    @Test
    void rerankResponse_withDegraded_parsedCorrectly() throws Exception {
        String json = """
                {"winnerId": "draft-0", "ranked": [{"id":"draft-0","humanProb":0.5}], "degraded": true}
                """;
        AiUserMlClient.RerankResponse resp = objectMapper.readValue(json, AiUserMlClient.RerankResponse.class);
        assertThat(resp.isDegraded()).isTrue();
        assertThat(resp.getWinnerId()).isEqualTo("draft-0");
    }

    @Test
    void rerankResponse_ignoresUnknownFields() throws Exception {
        String json = """
                {
                  "winnerId": "draft-0",
                  "ranked": [],
                  "modelVersion": "v123",
                  "extraField": "should be ignored",
                  "degraded": false
                }
                """;
        AiUserMlClient.RerankResponse resp = objectMapper.readValue(json, AiUserMlClient.RerankResponse.class);
        assertThat(resp.getWinnerId()).isEqualTo("draft-0");
    }

    // ── DTO construction ──────────────────────────────────────────────────────

    @Test
    void candidateItem_constructsCorrectly() {
        AiUserMlClient.CandidateItem item = new AiUserMlClient.CandidateItem("draft-0", "어제 회사에서 팀장이");
        assertThat(item.getId()).isEqualTo("draft-0");
        assertThat(item.getText()).isEqualTo("어제 회사에서 팀장이");
    }

    @Test
    void isEnabled_defaultFalse() {
        // @Value field not injected without Spring → false (int zero)
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void isCollectEnabled_defaultFalse() {
        // @Value not injected → false
        assertThat(client.isCollectEnabled()).isFalse();
    }

    @Test
    void pushNegative_collectOnRerankOff_attemptsSilentlyNoThrow() {
        // collect=true, enabled=false: rerank는 동작 안 하지만 수집은 시도(네트워크 실패시 무에러)
        ReflectionTestUtils.setField(client, "collect", true);
        ReflectionTestUtils.setField(client, "enabled", false);
        // port 9999 not listening → caught silently inside pushNegative
        client.pushNegative("NATEPAN", "POST", "테스트 글");
    }

    @Test
    void rerank_enabledOffCollectOn_returnsEmpty() {
        // collect=true여도 rerank는 enabled=false면 동작 안 함 (분리 검증)
        ReflectionTestUtils.setField(client, "collect", true);
        ReflectionTestUtils.setField(client, "enabled", false);
        Optional<AiUserMlClient.RerankResponse> result = client.rerank(
            "NATEPAN", "POST", List.of(new AiUserMlClient.CandidateItem("d0", "테스트 글")));
        assertThat(result).isEmpty();
    }

    @Test
    void getBestOfN_reflectionSet_returnsValue() {
        ReflectionTestUtils.setField(client, "bestOfN", 4);
        assertThat(client.getBestOfN()).isEqualTo(4);
    }
}
