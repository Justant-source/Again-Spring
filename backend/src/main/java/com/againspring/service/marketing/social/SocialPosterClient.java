package com.againspring.service.marketing.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Social Poster Sidecar Client.
 * Communicates with social-poster container on port 9100.
 * Handles X and Instagram publishing via Playwright session reuse.
 */
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@Slf4j
public class SocialPosterClient {

    public record PublishOutcome(
            boolean ok,
            String url,
            String error,
            boolean needsReseed,
            String updatedStorageState
    ) {}

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SocialPosterClient(@Value("${app.features.marketing.social-poster-url:http://localhost:9100}") String posterUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(posterUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Publish a post to X (Twitter).
     */
    public PublishOutcome publishX(Map<String, Object> requestBody) {
        try {
            byte[] response = restClient.post()
                    .uri("/publish/x")
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);
            return parseOutcome(response);
        } catch (Exception e) {
            log.error("[SOCIAL_POSTER] publishX failed: {}", e.getMessage());
            return new PublishOutcome(false, null, e.getMessage(), false, null);
        }
    }

    /**
     * Publish a post to Instagram.
     */
    public PublishOutcome publishInstagram(Map<String, Object> requestBody) {
        try {
            byte[] response = restClient.post()
                    .uri("/publish/instagram")
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);
            return parseOutcome(response);
        } catch (Exception e) {
            log.error("[SOCIAL_POSTER] publishInstagram failed: {}", e.getMessage());
            return new PublishOutcome(false, null, e.getMessage(), false, null);
        }
    }

    /**
     * Publish a post to Naver Blog.
     */
    public PublishOutcome publishNaverBlog(Map<String, Object> requestBody) {
        try {
            byte[] response = restClient.post()
                    .uri("/publish/naver-blog")
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);
            return parseOutcome(response);
        } catch (Exception e) {
            log.error("[SOCIAL_POSTER] publishNaverBlog failed: {}", e.getMessage());
            return new PublishOutcome(false, null, e.getMessage(), false, null);
        }
    }

    /**
     * Test login with stored credentials.
     */
    public Map<String, Object> testLogin(String platform, Map<String, Object> credentials) {
        try {
            Map<String, Object> body = Map.of("platform", platform, "credentials", credentials);
            byte[] response = restClient.post()
                    .uri("/test-login")
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            log.error("[SOCIAL_POSTER] testLogin failed for platform={}: {}", platform, e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    /**
     * Check if a session is still valid for the given platform.
     * Returns full response map including loggedIn and updatedStorageState.
     */
    public Map<String, Object> checkSessionHealth(String platform, String storageStateJson) {
        try {
            Map<String, Object> body = Map.of("platform", platform, "storageState", storageStateJson);
            byte[] response = restClient.post()
                    .uri("/session/health")
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            log.warn("[SOCIAL_POSTER] health check failed for {}: {}", platform, e.getMessage());
            return Map.of("loggedIn", false, "error", e.getMessage());
        }
    }

    /**
     * Parse a response from social-poster into a PublishOutcome.
     */
    private PublishOutcome parseOutcome(byte[] response) {
        try {
            Map<String, Object> map = objectMapper.readValue(response, Map.class);
            return new PublishOutcome(
                    Boolean.TRUE.equals(map.get("ok")),
                    (String) map.get("url"),
                    (String) map.get("error"),
                    Boolean.TRUE.equals(map.get("needsReseed")),
                    (String) map.get("updatedStorageState")
            );
        } catch (Exception e) {
            log.error("[SOCIAL_POSTER] Failed to parse outcome: {}", e.getMessage());
            return new PublishOutcome(false, null, e.getMessage(), false, null);
        }
    }
}
