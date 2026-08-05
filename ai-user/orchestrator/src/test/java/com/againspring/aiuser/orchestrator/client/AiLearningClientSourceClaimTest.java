package com.againspring.aiuser.orchestrator.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard / degrade paths for popular-source claim lifecycle.
 * RestTemplate is private — network success paths are covered when learning is up;
 * here we assert silent empty/false when disabled or unreachable.
 */
class AiLearningClientSourceClaimTest {

    private AiLearningClient client;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        client = new AiLearningClient("http://localhost:9999", objectMapper);
    }

    @Test
    void claimPopularSource_whenDisabled_returnsEmpty() {
        ReflectionTestUtils.setField(client, "enabled", false);
        assertThat(client.claimPopularSource("blind", "rk-1", Instant.now().plusSeconds(600)))
                .isEmpty();
    }

    @Test
    void claimPopularSource_withBlankArgs_returnsEmpty() {
        ReflectionTestUtils.setField(client, "enabled", true);
        Instant until = Instant.now().plusSeconds(600);
        assertThat(client.claimPopularSource(null, "rk", until)).isEmpty();
        assertThat(client.claimPopularSource("  ", "rk", until)).isEmpty();
        assertThat(client.claimPopularSource("blind", null, until)).isEmpty();
        assertThat(client.claimPopularSource("blind", "  ", until)).isEmpty();
        assertThat(client.claimPopularSource("blind", "rk", null)).isEmpty();
    }

    @Test
    void claimPopularSource_whenNetworkFails_returnsEmpty() {
        ReflectionTestUtils.setField(client, "enabled", true);
        Optional<AiLearningClient.ExampleItem> result =
                client.claimPopularSource("natepan", "rk-net", Instant.now().plusSeconds(600));
        assertThat(result).isEmpty();
    }

    @Test
    void commitAndRelease_whenDisabled_returnFalse() {
        ReflectionTestUtils.setField(client, "enabled", false);
        assertThat(client.commitSource(1L, "rk")).isFalse();
        assertThat(client.releaseSource(1L, "rk")).isFalse();
    }

    @Test
    void commitAndRelease_withBlankKey_returnFalse() {
        ReflectionTestUtils.setField(client, "enabled", true);
        assertThat(client.commitSource(1L, null)).isFalse();
        assertThat(client.commitSource(1L, "  ")).isFalse();
        assertThat(client.releaseSource(1L, null)).isFalse();
        assertThat(client.releaseSource(1L, "  ")).isFalse();
    }

    @Test
    void commitAndRelease_whenNetworkFails_returnFalse() {
        ReflectionTestUtils.setField(client, "enabled", true);
        assertThat(client.commitSource(42L, "rk-net")).isFalse();
        assertThat(client.releaseSource(42L, "rk-net")).isFalse();
    }

    @Test
    void parseClaimedExample_emptyStatus_isEmpty() throws Exception {
        ReflectionTestUtils.setField(client, "enabled", true);
        // exercise private parser via ReflectionTestUtils
        @SuppressWarnings("unchecked")
        Optional<AiLearningClient.ExampleItem> emptyStatus =
                (Optional<AiLearningClient.ExampleItem>) ReflectionTestUtils.invokeMethod(
                        client, "parseClaimedExample", "{\"status\":\"empty\"}");
        assertThat(emptyStatus).isEmpty();

        @SuppressWarnings("unchecked")
        Optional<AiLearningClient.ExampleItem> blank =
                (Optional<AiLearningClient.ExampleItem>) ReflectionTestUtils.invokeMethod(
                        client, "parseClaimedExample", "  ");
        assertThat(blank).isEmpty();

        @SuppressWarnings("unchecked")
        Optional<AiLearningClient.ExampleItem> ok =
                (Optional<AiLearningClient.ExampleItem>) ReflectionTestUtils.invokeMethod(
                        client, "parseClaimedExample",
                        "{\"id\":9,\"content\":\"본문\",\"source\":\"blind\",\"popularityPct\":0.82}");
        assertThat(ok).isPresent();
        assertThat(ok.get().getId()).isEqualTo(9L);
        assertThat(ok.get().getPopularityPct()).isEqualTo(0.82);
    }
}
