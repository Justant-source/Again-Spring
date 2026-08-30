package com.againspring.marketing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Public FxTwitter v2 timeline. Used only to read {@code @againspring_net} posts
 * for persona learning — never to publish.
 */
@Slf4j
@Component
public class FxTwitterXTimelineClient {

    private static final String DEFAULT_BASE = "https://api.fxtwitter.com";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public FxTwitterXTimelineClient(
            ObjectMapper objectMapper,
            @Value("${marketing.x.timeline-base-url:https://api.fxtwitter.com}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE : baseUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, "AgainSpringXPersonaLearn/1.0")
            .defaultHeader(HttpHeaders.ACCEPT, "application/json")
            .build();
    }

    public List<XManualStatusClassifier.Status> fetchRecent(String handle, int maxPages) {
        List<XManualStatusClassifier.Status> out = new ArrayList<>();
        String cursor = null;
        int pages = Math.max(1, Math.min(maxPages, 8));
        for (int i = 0; i < pages; i++) {
            String path = "/2/profile/" + handle + "/statuses?count=40&with_replies=1";
            if (cursor != null && !cursor.isBlank()) {
                path += "&cursor=" + cursor;
            }
            String body;
            try {
                body = restClient.get().uri(path).retrieve().body(String.class);
            } catch (Exception e) {
                log.warn("[x-persona] timeline fetch failed page={}: {}", i, e.getMessage());
                break;
            }
            if (body == null || body.isBlank()) {
                break;
            }
            try {
                JsonNode root = objectMapper.readTree(body);
                out.addAll(parsePage(root));
                JsonNode bottom = root.path("cursor").path("bottom");
                if (!bottom.isTextual() || bottom.asText().isBlank()) {
                    break;
                }
                cursor = bottom.asText();
                if (root.path("results").isEmpty()) {
                    break;
                }
            } catch (Exception e) {
                log.warn("[x-persona] timeline parse failed: {}", e.getMessage());
                break;
            }
        }
        return out;
    }

    static List<XManualStatusClassifier.Status> parsePage(JsonNode root) {
        List<XManualStatusClassifier.Status> out = new ArrayList<>();
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            return out;
        }
        for (JsonNode item : results) {
            if (!item.isObject()) {
                continue;
            }
            String id = text(item, "id");
            if (id.isBlank()) {
                continue;
            }
            String rawText = item.path("text").isTextual() ? item.path("text").asText() : "";
            JsonNode reply = item.path("replying_to");
            String replyTo = reply.isTextual()
                ? reply.asText(null)
                : reply.path("screen_name").asText(null);
            boolean quote = item.has("quote") && !item.path("quote").isNull()
                && !item.path("quote").isMissingNode()
                && item.path("quote").isObject();
            out.add(new XManualStatusClassifier.Status(id, rawText, replyTo, quote));
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isTextual() || v.isNumber() ? v.asText("") : "";
    }
}
