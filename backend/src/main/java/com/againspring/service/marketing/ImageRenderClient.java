package com.againspring.service.marketing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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
     *
     * @param html    HTML content to render
     * @param width   Viewport width in pixels
     * @param height  Viewport height in pixels
     * @return PNG image as byte array
     * @throws RuntimeException if rendering fails
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
}
