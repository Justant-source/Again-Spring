package com.againspring.aiuser.llm.dto;

import com.againspring.aiuser.llm.service.LlmProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderFieldCompatTest {
    private final ObjectMapper om = new ObjectMapper();

    @Test
    void legacyBackendFieldStillResolves() throws Exception {
        PostGenRequest r = om.readValue("{\"backend\":\"API\"}", PostGenRequest.class);
        assertEquals(LlmProvider.API, r.resolveProvider());
    }

    @Test
    void providerFieldWins() throws Exception {
        PostGenRequest r = om.readValue("{\"backend\":\"API\",\"provider\":\"CODEX\"}", PostGenRequest.class);
        assertEquals(LlmProvider.CODEX, r.resolveProvider());
    }

    @Test
    void rewriteDefaultsToApiWhenBothBlank() throws Exception {
        PostRewriteRequest r = om.readValue("{}", PostRewriteRequest.class);
        assertEquals(LlmProvider.API, r.resolveProvider());
    }
}
