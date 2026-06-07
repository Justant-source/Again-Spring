package com.againspring.service.marketing.image;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.service.marketing.content.GenerationOutput;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Selects the most contextually appropriate metaphor SVG for a marketing content piece.
 * Selects based on relationType string — no simulation or story dependency.
 */
@Component
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class MarketingMetaphorSelector {

    private static final Map<String, List<String>> BY_RELATION = Map.of(
        "couple",      List.of("14-melting-candle.svg", "17-tangled-thread.svg",
                               "15-parallel-rails.svg", "13-two-compasses-apart.svg",
                               "22-back-to-back-umbrellas.svg", "19-one-candle-out.svg",
                               "11-half-open-letter.svg"),
        "married",     List.of("18-dying-stove.svg", "22-back-to-back-umbrellas.svg",
                               "15-parallel-rails.svg", "20-empty-photo-frame.svg",
                               "24-empty-nest.svg",   "59-two-compasses-aligned.svg"),
        "friend",      List.of("25-dried-bouquet.svg", "28-broken-thread.svg",
                               "26-emptying-hourglass.svg", "30-string-telephone.svg",
                               "29-wrongly-folded-letter.svg", "27-one-lit-bulb.svg"),
        "family",      List.of("44-empty-dining-table.svg", "42-trees-growing-apart.svg",
                               "43-cracked-bowl.svg", "40-small-birdcage.svg",
                               "46-closed-diary.svg", "45-wilting-plant.svg"),
        "work",        List.of("33-tilted-scale.svg", "39-gears-not-meshing.svg",
                               "34-overflowing-papers.svg", "38-too-many-keys.svg",
                               "36-light-under-door.svg", "35-empty-trophy.svg")
    );

    private static final List<String> GENERAL_FALLBACK = List.of(
        "09-overflowing-cup.svg", "07-cracked-window.svg", "05-person-in-rain.svg",
        "08-empty-chair.svg",     "02-boiling-kettle.svg", "11-half-open-letter.svg"
    );

    /**
     * Selects an SVG filename appropriate for the given relationType.
     */
    public String selectFilename(String relationType) {
        List<String> candidates = BY_RELATION.getOrDefault(
                relationType != null ? relationType.toLowerCase() : "other",
                GENERAL_FALLBACK);
        return candidates.get(0);
    }

    public String labelFor(String svgFilename) {
        if (svgFilename == null) return "관계 메타포";
        String name = svgFilename
            .replaceAll("^\\d+-", "")   // strip leading "14-"
            .replace(".svg", "")
            .replace("-", " ");
        return "메타포: " + name;
    }

    /**
     * Extracts a short hook text from the LLM output to display on the metaphor card.
     */
    @SuppressWarnings("unchecked")
    public String extractHookText(GenerationOutput output, MarketingContent.Platform platform) {
        if (output == null || output.structuredPayload() == null) {
            return "이런 갈등, 겪어보셨나요?";
        }
        Map<String, Object> payload = output.structuredPayload();

        try {
            switch (platform) {
                case INSTAGRAM -> {
                    List<Map<String, Object>> slides = (List<Map<String, Object>>) payload.get("slides");
                    if (slides != null && !slides.isEmpty()) {
                        Object title = slides.get(0).get("title");
                        if (title != null && !title.toString().isBlank()) return clamp(title.toString(), 40);
                    }
                }
                case X -> {
                    Object tweets = payload.get("tweets");
                    if (tweets instanceof List<?> list && !list.isEmpty()) {
                        String first = list.get(0).toString().trim();
                        int dot = first.indexOf('.');
                        return clamp(dot > 8 && dot < 50 ? first.substring(0, dot + 1) : first, 50);
                    }
                }
                case NAVER_BLOG -> {
                    String md = (String) payload.get("markdown");
                    if (md != null) {
                        for (String line : md.split("\n")) {
                            String trimmed = line.trim();
                            if (trimmed.startsWith("# ")) return clamp(trimmed.substring(2).trim(), 40);
                        }
                    }
                }
                default -> { /* fallthrough */ }
            }
        } catch (Exception ignored) { /* safe fallback */ }

        return "이런 갈등, 겪어보셨나요?";
    }

    private String clamp(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }
}
