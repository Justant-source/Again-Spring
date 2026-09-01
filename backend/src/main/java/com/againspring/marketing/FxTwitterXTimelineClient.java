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

    public record ParentStatus(String text, boolean hasPhoto) {}

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

    /**
     * Best-effort parent tweet. Failure or 404 → {@code null}.
     */
    public ParentStatus fetchStatus(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            String body = restClient.get()
                .uri("/i/status/{id}", id)
                .retrieve()
                .body(String.class);
            if (body == null || body.isBlank()) {
                return null;
            }
            return parseStatusResponse(objectMapper.readTree(body));
        } catch (Exception e) {
            log.warn("[x-persona] status fetch failed id={}: {}", id, e.getMessage());
            return null;
        }
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
            String replyTo = parseReplyToHandle(reply);
            String replyToStatusId = parseReplyToStatusId(item, reply);
            JsonNode quoteNode = item.path("quote");
            boolean quote = quoteNode.isObject();
            String quoteText = quote && quoteNode.path("text").isTextual()
                ? quoteNode.path("text").asText()
                : null;
            boolean hasMedia = hasPhotoMedia(item.path("media"));
            out.add(new XManualStatusClassifier.Status(
                id, rawText, replyTo, quote, replyToStatusId, quoteText, hasMedia));
        }
        return out;
    }

    static ParentStatus parseStatusResponse(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        if (root.path("code").asInt(0) == 404) {
            return null;
        }
        JsonNode tweet = root.path("tweet");
        if (!tweet.isObject()) {
            return null;
        }
        String text = tweet.path("text").isTextual() ? tweet.path("text").asText() : "";
        return new ParentStatus(text, hasPhotoMedia(tweet.path("media")));
    }

    static String parseReplyToHandle(JsonNode reply) {
        if (reply == null || reply.isMissingNode() || reply.isNull()) {
            return null;
        }
        if (reply.isTextual()) {
            String h = reply.asText(null);
            return h == null || h.isBlank() ? null : h;
        }
        if (reply.isObject()) {
            String h = reply.path("screen_name").asText(null);
            return h == null || h.isBlank() ? null : h;
        }
        return null;
    }

    static String parseReplyToStatusId(JsonNode item, JsonNode reply) {
        if (reply != null && reply.isObject()) {
            String nested = text(reply, "status");
            if (!nested.isBlank()) {
                return nested;
            }
        }
        String top = text(item, "replying_to_status");
        return top.isBlank() ? null : top;
    }

    static boolean hasPhotoMedia(JsonNode media) {
        if (media == null || media.isMissingNode() || media.isNull() || !media.isObject()) {
            return false;
        }
        JsonNode photos = media.path("photos");
        if (photos.isArray() && photos.size() > 0) {
            return true;
        }
        JsonNode all = media.path("all");
        if (all.isArray()) {
            for (JsonNode m : all) {
                if (m != null && m.isObject()
                    && "photo".equalsIgnoreCase(m.path("type").asText(""))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isTextual() || v.isNumber() ? v.asText("") : "";
    }
}
