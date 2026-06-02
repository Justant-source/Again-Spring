package com.againspring.service.marketing.image;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.MarketingSimulation;
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
 * Image composition strategy for X (Twitter).
 * Renders: (1) quote card from quoteCard JSON field, (2) optional chat key-moment screenshot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class XImageStrategy implements ImageCompositionStrategy {

    private final ImageRenderClient renderClient;
    private final KeyMomentSelector keyMomentSelector;
    private final MarketingMetaphorSelector metaphorSelector;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.X;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RenderedImage> compose(
            GenerationOutput output,
            MarketingSimulation sim,
            Object report,  // Stub: was Report report
            Long contentId,
            String imageDir
    ) throws IOException {
        List<RenderedImage> results = new ArrayList<>();
        Path dir = Paths.get(imageDir);
        Files.createDirectories(dir);
        int order = 1;

        // 1. METAPHOR_COVER — first tweet image (hook card: SVG centered + hook text)
        String svgFilename = metaphorSelector.selectFilename(sim, report);
        String hookText    = metaphorSelector.extractHookText(output, MarketingContent.Platform.X);
        byte[] coverPng    = renderClient.renderMetaphorCard(svgFilename, hookText, contentId, 1, 2);
        if (coverPng != null && coverPng.length > 0) {
            String coverFilename = "metaphor_cover_" + contentId + ".png";
            Files.write(dir.resolve(coverFilename), coverPng);
            results.add(new RenderedImage(coverFilename, "METAPHOR_COVER", "TWEET_1", hookText, order++));
            log.info("Metaphor cover saved: {}/{}", imageDir, coverFilename);
        }

        // 2. Quote card (second tweet)
        Map<String, Object> payload = output.structuredPayload();
        Map<String, Object> quoteCard = payload != null
                ? (Map<String, Object>) payload.get("quoteCard")
                : null;

        if (quoteCard != null) {
            String line1       = (String) quoteCard.getOrDefault("line1", "");
            String line2       = (String) quoteCard.getOrDefault("line2", "");
            String attribution = (String) quoteCard.getOrDefault("attribution", "다시봄");
            byte[] png = renderClient.renderQuote(line1, line2, attribution, "warm");
            if (png != null && png.length > 0) {
                String filename = "quote_" + contentId + ".png";
                Files.write(dir.resolve(filename), png);
                results.add(new RenderedImage(filename, "QUOTE_CARD", "TWEET_2", line1, order++));
                log.info("Quote card saved: {}/{}", imageDir, filename);
            }
        } else if (report != null && "" != null) {
            byte[] png = renderClient.renderQuote(
                    "",
                    "",
                    "다시봄", "warm");
            if (png != null && png.length > 0) {
                String filename = "quote_" + contentId + ".png";
                Files.write(dir.resolve(filename), png);
                results.add(new RenderedImage(filename, "QUOTE_CARD", "TWEET_2",
                        "", order++));
            }
        }

        // 3. Optional chat key-moment screenshot (last tweet)
        // TODO: Implement chat preview rendering for marketing simulation

        return results;
    }
}
