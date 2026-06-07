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
 * Image composition strategy for Instagram.
 * Renders 6-7 card-news slides from the slides[] JSON array in structuredPayload.
 * Slide 1 = METAPHOR_COVER (metaphor SVG + hook text), slides 2+ = LLM card-news.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class InstagramImageStrategy implements ImageCompositionStrategy {

    private final ImageRenderClient renderClient;
    private final MarketingMetaphorSelector metaphorSelector;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.INSTAGRAM;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RenderedImage> compose(
            GenerationOutput output,
            String relationType,
            Long contentId,
            String imageDir
    ) throws IOException {
        Map<String, Object> payload = output.structuredPayload();
        List<Map<String, Object>> slides = payload != null
                ? (List<Map<String, Object>>) payload.get("slides")
                : null;

        if (slides == null || slides.isEmpty()) {
            log.warn("InstagramImageStrategy: no slides in payload for contentId={}", contentId);
            return List.of();
        }

        Path dir = Paths.get(imageDir);
        Files.createDirectories(dir);

        List<RenderedImage> results = new ArrayList<>();
        int total = slides.size() + 1;

        // slide 1: METAPHOR_COVER
        String svgFilename = metaphorSelector.selectFilename(relationType);
        String hookText    = metaphorSelector.extractHookText(output, MarketingContent.Platform.INSTAGRAM);
        byte[] coverPng    = renderClient.renderMetaphorCard(svgFilename, hookText, contentId, 1, total);
        if (coverPng != null && coverPng.length > 0) {
            String coverFilename = "metaphor_cover_" + contentId + ".png";
            Files.write(dir.resolve(coverFilename), coverPng);
            results.add(new RenderedImage(coverFilename, "METAPHOR_COVER", "SLIDE_1", hookText, 1));
            log.info("Metaphor cover saved: {}/{}", imageDir, coverFilename);
        } else {
            log.warn("InstagramImageStrategy: metaphor cover render failed for contentId={}", contentId);
            total--;
        }

        // slides 2+: LLM card-news
        List<ImageRenderClient.CardNewsSlide> rendered =
                renderClient.renderCardNews(slides, "warm", contentId);

        if (rendered.isEmpty()) {
            log.warn("InstagramImageStrategy: renderCardNews returned empty for contentId={}", contentId);
            return results;
        }

        for (int i = 0; i < rendered.size(); i++) {
            ImageRenderClient.CardNewsSlide s = rendered.get(i);
            Files.write(dir.resolve(s.filename()), s.png());

            String role = "CARD_SLIDE";
            String alt  = "카드뉴스 슬라이드 " + (i + 2);
            if (i < slides.size()) {
                Object r = slides.get(i).get("role");
                if (r != null) role = r.toString();
                Object t = slides.get(i).get("title");
                if (t != null) alt = t.toString();
            }
            results.add(new RenderedImage(s.filename(), role, "SLIDE_" + (i + 2), alt, i + 2));
        }

        log.info("Instagram slides saved: total={} for contentId={}", results.size(), contentId);
        return results;
    }
}
