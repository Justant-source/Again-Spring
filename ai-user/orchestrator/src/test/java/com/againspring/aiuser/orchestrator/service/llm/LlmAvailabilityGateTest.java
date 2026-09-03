package com.againspring.aiuser.orchestrator.service.llm;

import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.domain.LlmGenerationGate;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

class LlmAvailabilityGateTest {

    private final LlmAiUserClient client = mock(LlmAiUserClient.class);
    private final LlmGenerationGateService gate = mock(LlmGenerationGateService.class);

    private static Map<String, Object> status(String claude) {
        return Map.of("claude", Map.of("state", claude, "reason", "Not logged in"),
                      "codex", Map.of("state", "UP"), "api", Map.of("state", "UP"), "stub", Map.of("state", "UP"));
    }

    @Test
    void authDownHoldsWithAutoPrefix() {
        when(client.providersStatus()).thenReturn(Optional.of(status("AUTH_DOWN")));
        when(gate.getCurrentState()).thenReturn(LlmGenerationGate.builder().id(1).state("ACTIVE").build());
        new LlmAvailabilityGate(client, gate).check();
        verify(gate).hold(startsWith("auto:llm-auth-down"));
    }

    @Test
    void upResumesOnlyAutoHold() {
        when(client.providersStatus()).thenReturn(Optional.of(status("UP")));
        when(gate.getCurrentState()).thenReturn(LlmGenerationGate.builder().id(1).state("HELD").reason("auto:llm-auth-down: x").build());
        new LlmAvailabilityGate(client, gate).check();
        verify(gate).resume();
    }

    @Test
    void upDoesNotResumeManualHold() {
        when(client.providersStatus()).thenReturn(Optional.of(status("UP")));
        when(gate.getCurrentState()).thenReturn(LlmGenerationGate.builder().id(1).state("HELD").reason("Manual admin hold").build());
        new LlmAvailabilityGate(client, gate).check();
        verify(gate, never()).resume();
    }

    @Test
    void unreachableWorkerDoesNothing() {
        when(client.providersStatus()).thenReturn(Optional.empty());
        new LlmAvailabilityGate(client, gate).check();
        verifyNoInteractions(gate);
    }
}
