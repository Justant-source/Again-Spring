package com.againspring.llm.remote.dto;

import com.againspring.llm.LlmImage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerInvokeRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesImagesWhenPresent() throws Exception {
        WorkerInvokeRequest req = WorkerInvokeRequest.builder()
                .prompt("describe")
                .model("claude-haiku-4-5-20251001")
                .timeoutMs(1000L)
                .images(List.of(new LlmImage("image/jpeg", "abc123")))
                .build();
        JsonNode node = mapper.readTree(mapper.writeValueAsString(req));
        assertEquals("describe", node.get("prompt").asText());
        assertEquals(1, node.get("images").size());
        assertEquals("image/jpeg", node.get("images").get(0).get("mime").asText());
        assertEquals("abc123", node.get("images").get(0).get("base64").asText());
    }

    @Test
    void omitsImagesWhenAbsent() throws Exception {
        WorkerInvokeRequest req = WorkerInvokeRequest.builder()
                .prompt("hello")
                .model("claude-haiku-4-5-20251001")
                .timeoutMs(1000L)
                .build();
        String json = mapper.writeValueAsString(req);
        assertFalse(json.contains("images"));
        assertTrue(json.contains("\"prompt\""));
    }
}
