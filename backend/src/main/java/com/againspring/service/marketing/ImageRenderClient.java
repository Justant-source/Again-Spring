package com.againspring.service.marketing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class ImageRenderClient {

    private final RestClient restClient;

    public ImageRenderClient(@Value("${app.features.marketing.renderer-url:http://localhost:9000}") String rendererUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(rendererUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Renders HTML to PNG image.
     */
    public byte[] renderToPng(String html, int width, int height) {
        Map<String, Object> body = Map.of(
                "html", html,
                "viewport", Map.of("w", width, "h", height),
                "strip_exif", true
        );
        try {
            return restClient.post()
                    .uri("/render")
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            throw new RuntimeException("이미지 렌더링 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Renders a chat preview screenshot using the Dasibom chat UI design.
     * messages: list of {sender, content, createdAt} maps.
     * Returns PNG bytes, or null if renderer is unavailable (non-fatal).
     */
    public byte[] renderChatPreview(List<Map<String, Object>> messages, String title, String subtitle) {
        Map<String, Object> body = Map.of(
                "messages", messages,
                "title", title != null ? title : "다시봄",
                "subtitle", subtitle != null ? subtitle : "AI 갈등 중재",
                "viewport", Map.of("w", 390, "h", 720)
        );
        try {
            return restClient.post()
                    .uri("/render-chat")
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            return null;
        }
    }
}
