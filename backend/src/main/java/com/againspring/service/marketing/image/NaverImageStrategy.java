package com.againspring.service.marketing.image;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.service.marketing.ImageRenderClient;
import com.againspring.service.marketing.content.GenerationOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Image composition strategy for Naver Blog.
 * Renders: metaphor cover + per-slot images (chat-preview, report-needs, quote-card).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class NaverImageStrategy implements ImageCompositionStrategy {

    private final ImageRenderClient renderClient;
    private final KeyMomentSelector keyMomentSelector;
    private final MarketingMetaphorSelector metaphorSelector;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.NAVER_BLOG;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RenderedImage> compose(
            GenerationOutput output,
            String relationType,
            Long contentId,
            String imageDir
    ) throws IOException {
        Path dir = Paths.get(imageDir);
        Files.createDirectories(dir);

        Map<String, Object> payload = output.structuredPayload();
        List<Map<String, Object>> imageSlots = payload != null
                ? (List<Map<String, Object>>) payload.get("imageSlots")
                : null;

        List<RenderedImage> results = new ArrayList<>();

        // METAPHOR_COVER (hook card PNG)
        String svgFilename = metaphorSelector.selectFilename(relationType);
        String hookText    = metaphorSelector.extractHookText(output, MarketingContent.Platform.NAVER_BLOG);
        int totalSlots     = imageSlots != null ? imageSlots.size() + 1 : 1;
        byte[] coverPng    = renderClient.renderMetaphorCard(svgFilename, hookText, contentId, 1, totalSlots);
        if (coverPng != null && coverPng.length > 0) {
            String coverFilename = "metaphor_cover_" + contentId + ".png";
            Files.write(dir.resolve(coverFilename), coverPng);
            results.add(new RenderedImage(coverFilename, "METAPHOR_COVER", "<!-- IMG:metaphor -->",
                    hookText, 1));
            log.info("Naver metaphor cover saved: {}/{}", imageDir, coverFilename);
        }

        if (imageSlots == null || imageSlots.isEmpty()) {
            log.warn("NaverImageStrategy: no imageSlots in payload for contentId={}", contentId);
            return results;
        }

        for (int i = 0; i < imageSlots.size(); i++) {
            Map<String, Object> slot = imageSlots.get(i);
            String kind = (String) slot.getOrDefault("kind", "");
            String slotMarker = (String) slot.getOrDefault("slot", "IMG:slot_" + i);
            String idx = String.format("%02d", i + 1);

            byte[] png = switch (kind) {
                case "chat" -> null; // community post: no session chat to render
                case "report-needs", "report-ratio", "report-combined" -> null;
                case "quote" -> {
                    String quoteText = (String) slot.get("quoteText");
                    if (quoteText == null) quoteText = "";
                    yield renderClient.renderQuote(quoteText, "", "다시봄", "warm");
                }
                default -> null;
            };

            if (png == null || png.length == 0) {
                log.warn("NaverImageStrategy: {} render returned empty for contentId={}", kind, contentId);
                continue;
            }

            String filename = "naver_" + contentId + "_" + idx + "_" + kind.replace("-", "_") + ".png";
            Files.write(dir.resolve(filename), png);

            String roleKey = switch (kind) {
                case "quote" -> "QUOTE_CARD";
                default -> kind.toUpperCase();
            };
            String alt = switch (kind) {
                case "quote" -> "메타포 인용 카드";
                default -> filename;
            };

            results.add(new RenderedImage(filename, roleKey, slotMarker, alt, i + 2));
            log.info("Naver image saved: {}/{}", imageDir, filename);
        }

        return results;
    }
}
