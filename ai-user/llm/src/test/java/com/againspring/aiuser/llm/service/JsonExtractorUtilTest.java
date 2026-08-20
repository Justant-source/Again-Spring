package com.againspring.aiuser.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonExtractorUtilTest {

    @Test
    void extractCleanJson() {
        String json = """
                {
                  "post": {"title": "test"},
                  "comments": []
                }
                """;
        JsonNode node = JsonExtractorUtil.extract(json);
        assertTrue(node.isObject());
        assertEquals("test", node.path("post").path("title").asText());
    }

    @Test
    void extractJsonWithCodeFences() {
        String text = """
                ```json
                {
                  "post": {"title": "test"},
                  "comments": []
                }
                ```
                """;
        JsonNode node = JsonExtractorUtil.extract(text);
        assertTrue(node.isObject());
        assertEquals("test", node.path("post").path("title").asText());
    }

    @Test
    void extractJsonWithTripleBacktickFences() {
        String text = """
                ```
                {
                  "post": {"title": "test"},
                  "comments": []
                }
                ```
                """;
        JsonNode node = JsonExtractorUtil.extract(text);
        assertTrue(node.isObject());
        assertEquals("test", node.path("post").path("title").asText());
    }

    @Test
    void extractJsonWithLeadingProse() {
        String text = """
                Here's the JSON response:

                {
                  "post": {"title": "test"},
                  "comments": []
                }
                """;
        JsonNode node = JsonExtractorUtil.extract(text);
        assertTrue(node.isObject());
        assertEquals("test", node.path("post").path("title").asText());
    }

    @Test
    void extractJsonWithTrailingCommentary() {
        String text = """
                {
                  "post": {"title": "test"},
                  "comments": []
                }

                This is additional commentary about the response.
                """;
        JsonNode node = JsonExtractorUtil.extract(text);
        assertTrue(node.isObject());
        assertEquals("test", node.path("post").path("title").asText());
    }

    @Test
    void extractJsonWithProseAndFences() {
        String text = """
                Based on your request, here's the response:

                ```json
                {
                  "post": {"title": "conflict"},
                  "comments": [{"ref": "c1", "personaId": "p1"}]
                }
                ```

                Feel free to modify as needed.
                """;
        JsonNode node = JsonExtractorUtil.extract(text);
        assertTrue(node.isObject());
        assertEquals("conflict", node.path("post").path("title").asText());
        assertTrue(node.path("comments").isArray());
    }

    @Test
    void extractArrayRoot() {
        String text = """
                ```json
                [
                  {"id": 1, "name": "test"}
                ]
                ```
                """;
        JsonNode node = JsonExtractorUtil.extract(text);
        assertTrue(node.isArray());
        assertEquals("test", node.get(0).path("name").asText());
    }

    @Test
    void extractArrayWithoutFences() {
        String text = """
                Here's your array:
                [
                  {"humanCommentId": 1, "personaId": "p1", "body": "reply"}
                ]
                """;
        JsonNode node = JsonExtractorUtil.extract(text);
        assertTrue(node.isArray());
        assertEquals(1, node.get(0).path("humanCommentId").asInt());
    }

    @Test
    void failOnInvalidJson() {
        String text = "{ this is not valid json }";
        assertThrows(RuntimeException.class, () -> JsonExtractorUtil.extract(text));
    }

    @Test
    void failOnNoJsonMarkers() {
        String text = "This is just plain text with no JSON at all.";
        assertThrows(RuntimeException.class, () -> JsonExtractorUtil.extract(text));
    }

    @Test
    void failOnEmpty() {
        assertThrows(RuntimeException.class, () -> JsonExtractorUtil.extract(""));
        assertThrows(RuntimeException.class, () -> JsonExtractorUtil.extract(null));
        assertThrows(RuntimeException.class, () -> JsonExtractorUtil.extract("   "));
    }

    @Test
    void extractComplexNestedStructure() {
        String text = """
                Model output:
                ```json
                {
                  "post": {
                    "title": "A vs B",
                    "body": "Long story...",
                    "promo_title": "Hook line",
                    "hook_emotion": "tension",
                    "capture_split_after_lines": [5, 10],
                    "metaphor_ids": ["id1", "id2"]
                  },
                  "comments": [
                    {
                      "ref": "c1",
                      "parentRef": null,
                      "personaId": "p_123",
                      "body": "Comment text",
                      "stance": "neutral",
                      "priority": 1
                    },
                    {
                      "ref": "c2",
                      "parentRef": "c1",
                      "personaId": "p_456",
                      "body": "Reply"
                    }
                  ]
                }
                ```

                That's the full thread plan.
                """;
        JsonNode node = JsonExtractorUtil.extract(text);
        assertTrue(node.isObject());
        assertEquals("A vs B", node.path("post").path("title").asText());
        assertTrue(node.path("comments").isArray());
        assertEquals(2, node.path("comments").size());
        assertEquals("c2", node.path("comments").get(1).path("ref").asText());
        assertEquals("c1", node.path("comments").get(1).path("parentRef").asText());
    }

    @Test
    void extractWithNestedObject() {
        String text = """
                {
                  "key": "value with text",
                  "nested": {"inner": "test"}
                }
                """;
        JsonNode node = JsonExtractorUtil.extract(text);
        assertTrue(node.isObject());
        assertEquals("test", node.path("nested").path("inner").asText());
    }
}
