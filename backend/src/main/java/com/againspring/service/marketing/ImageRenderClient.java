package com.againspring.service.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class ImageRenderClient {

    public record CardNewsSlide(String filename, byte[] png) {}

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
     * Renders a chat preview screenshot. maxMessages <= 0 means no limit.
     * Returns null if renderer is unavailable (non-fatal).
     */
    public byte[] renderChatPreview(List<Map<String, Object>> messages, String title, String subtitle) {
        return renderChatPreview(messages, title, subtitle, 5);
    }

    public byte[] renderChatPreview(List<Map<String, Object>> messages, String title, String subtitle, int maxMessages) {
        Map<String, Object> body = Map.of(
                "messages", messages,
                "title", title != null ? title : "다시봄",
                "subtitle", subtitle != null ? subtitle : "AI 갈등 중재",
                "maxMessages", maxMessages,
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

    /**
     * Renders a quote card PNG (1080×1350). Returns null on failure.
     */
    public byte[] renderQuote(String line1, String line2, String attribution, String variant) {
        Map<String, Object> body = Map.of(
                "line1", line1 != null ? line1 : "",
                "line2", line2 != null ? line2 : "",
                "attribution", attribution != null ? attribution : "다시봄",
                "variant", variant != null ? variant : "warm"
        );
        try {
            return restClient.post()
                    .uri("/render-quote")
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Renders card-news slides and returns decoded PNG bytes per slide.
     * Returns empty list on failure.
     */
    @SuppressWarnings("unchecked")
    public List<CardNewsSlide> renderCardNews(List<Map<String, Object>> slides, String theme, long contentId) {
        Map<String, Object> body = Map.of(
                "slides", slides,
                "theme", theme != null ? theme : "warm",
                "contentId", contentId
        );
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/render-card-news")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null) return List.of();
            List<Map<String, String>> rawSlides = (List<Map<String, String>>) response.get("slides");
            if (rawSlides == null) return List.of();
            return rawSlides.stream()
                    .map(s -> new CardNewsSlide(
                            s.get("filename"),
                            Base64.getDecoder().decode(s.get("base64"))))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Renders a report summary diagram (NeedsMap / ContributionRatio).
     * mode: "needs" | "ratio" | "combined". Returns null on failure.
     */
    public byte[] renderReportSummary(Map<String, Object> reportData, String mode) {
        Map<String, Object> body = Map.of(
                "report", reportData,
                "mode", mode != null ? mode : "combined"
        );
        try {
            return restClient.post()
                    .uri("/render-report-summary")
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            return null;
        }
    }
}
