package com.againspring.aiuser.orchestrator.admin;

import com.againspring.aiuser.orchestrator.service.llm.LlmCircuitBreaker;
import com.againspring.aiuser.orchestrator.service.llm.LlmStatsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminMetricsController.class)
class AdminMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LlmStatsRecorder statsRecorder;

    @MockBean
    private LlmCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // Reset and prepare test data
        statsRecorder.reset();
        // Set up default circuit breaker telemetry
        LlmCircuitBreaker.Telemetry telemetry = new LlmCircuitBreaker.Telemetry();
        telemetry.setState(LlmCircuitBreaker.State.CLOSED);
        telemetry.setConsecutiveFailures(0);
        org.mockito.Mockito.when(circuitBreaker.getTelemetry()).thenReturn(telemetry);
    }

    @Test
    void testGetLlmStatsEndpoint() throws Exception {
        // Record some test stats
        statsRecorder.recordCall("POST", 100, 50, 10, 5, "OK", "NONE");
        statsRecorder.recordCall("COMMENT", 80, 40, 5, 2, "RETRY", "PARSE_FAIL");

        mockMvc.perform(get("/admin/metrics/llm-today"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.scope").value("in-memory 24h rolling"))
            .andExpect(jsonPath("$.circuitBreaker").exists())
            .andExpect(jsonPath("$.circuitBreaker.state").value("CLOSED"))
            .andExpect(jsonPath("$.stats").isMap());
    }

    @Test
    void testGetLlmStatsWithEmptyData() throws Exception {
        statsRecorder.reset();

        mockMvc.perform(get("/admin/metrics/llm-today"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.circuitBreaker").exists())
            .andExpect(jsonPath("$.stats").isMap());
    }
}
