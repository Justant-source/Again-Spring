package com.againspring.service.marketing.image;

import com.againspring.domain.Report;
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
 * Image composition strategy for Instagram.
 * Renders 6-7 card-news slides from the slides[] JSON array in structuredPayload.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class InstagramImageStrategy implements ImageCompositionStrategy {

    private final ImageRenderClient renderClient;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.INSTAGRAM;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RenderedImage> compose(
            GenerationOutput output,
            MarketingSimulation sim,
            Report report,
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

        if (slides.size() < 6 || slides.size() > 7) {
            log.warn("InstagramImageStrategy: expected 6-7 slides, got {} for contentId={}", slides.size(), contentId);
        }

        List<ImageRenderClient.CardNewsSlide> rendered =
                renderClient.renderCardNews(slides, "warm", contentId);

        if (rendered.isEmpty()) {
            log.warn("InstagramImageStrategy: renderCardNews returned empty for contentId={}", contentId);
            return List.of();
        }

        Path dir = Paths.get(imageDir);
        Files.createDirectories(dir);

        List<RenderedImage> results = new ArrayList<>();
        for (int i = 0; i < rendered.size(); i++) {
            ImageRenderClient.CardNewsSlide s = rendered.get(i);
            Files.write(dir.resolve(s.filename()), s.png());

            String role = "CARD_SLIDE";
            String alt = "카드뉴스 슬라이드 " + (i + 1);
            if (i < slides.size()) {
                Object r = slides.get(i).get("role");
                if (r != null) role = r.toString();
                Object t = slides.get(i).get("title");
                if (t != null) alt = t.toString();
            }
            results.add(new RenderedImage(s.filename(), role, "SLIDE_" + (i + 1), alt, i + 1));
        }

        log.info("Instagram card-news saved: {} slides for contentId={}", results.size(), contentId);
        return results;
    }
}
