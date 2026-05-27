package com.againspring.service.marketing.image;

import com.againspring.domain.Report;
import com.againspring.domain.marketing.MarketingSimulation;
import com.againspring.repository.marketing.MarketingSourceStoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Selects the most contextually appropriate metaphor SVG for a marketing content piece.
 * Returns the raw SVG filename (e.g. "05-person-in-rain.svg") — served as static assets by the FE,
 * not as rendered PNGs — so no imageDir write is needed.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class MarketingMetaphorSelector {

    private final MarketingSourceStoryRepository storyRepo;

    private static final Map<String, List<String>> BY_RELATION = Map.of(
        "couple",      List.of("14-melting-candle.svg", "17-tangled-thread.svg",
                               "15-parallel-rails.svg", "13-two-compasses-apart.svg",
                               "22-back-to-back-umbrellas.svg", "19-one-candle-out.svg",
                               "11-half-open-letter.svg"),
        "marriage",    List.of("18-dying-stove.svg", "22-back-to-back-umbrellas.svg",
                               "15-parallel-rails.svg", "20-empty-photo-frame.svg",
                               "24-empty-nest.svg",   "59-two-compasses-aligned.svg"),
        "friend",      List.of("25-dried-bouquet.svg", "28-broken-thread.svg",
                               "26-emptying-hourglass.svg", "30-string-telephone.svg",
                               "29-wrongly-folded-letter.svg", "27-one-lit-bulb.svg"),
        "family",      List.of("44-empty-dining-table.svg", "42-trees-growing-apart.svg",
                               "43-cracked-bowl.svg", "40-small-birdcage.svg",
                               "46-closed-diary.svg", "45-wilting-plant.svg"),
        "parent_child",List.of("47-long-shadow.svg", "41-tall-fence.svg",
                               "40-small-birdcage.svg", "46-closed-diary.svg",
                               "42-trees-growing-apart.svg"),
        "colleague",   List.of("33-tilted-scale.svg", "39-gears-not-meshing.svg",
                               "34-overflowing-papers.svg", "38-too-many-keys.svg",
                               "36-light-under-door.svg", "35-empty-trophy.svg")
    );

    private static final List<String> GENERAL_FALLBACK = List.of(
        "09-overflowing-cup.svg", "07-cracked-window.svg", "05-person-in-rain.svg",
        "08-empty-chair.svg",     "02-boiling-kettle.svg", "11-half-open-letter.svg"
    );

    // NVC need keyword → preferred SVG (applied before relationType selection)
    private static final Map<String, String> NEED_TO_SVG = Map.ofEntries(
        Map.entry("연결",   "30-string-telephone.svg"),
        Map.entry("소통",   "11-half-open-letter.svg"),
        Map.entry("인정",   "35-empty-trophy.svg"),
        Map.entry("자율성", "37-chained-anchor.svg"),
        Map.entry("안전",   "04-too-big-umbrella.svg"),
        Map.entry("이해",   "29-wrongly-folded-letter.svg"),
        Map.entry("회복",   "58-crack-with-light.svg"),
        Map.entry("신뢰",   "31-inside-out-umbrella.svg"),
        Map.entry("돌봄",   "55-open-window.svg"),
        Map.entry("존중",   "56-cups-finally-touching.svg")
    );

    /**
     * Selects an SVG filename appropriate for the given simulation and report context.
     */
    public String selectFilename(MarketingSimulation sim, Report report) {
        // 1. NVC need keyword match takes priority
        if (report != null && report.getNvcNeed() != null) {
            for (Map.Entry<String, String> e : NEED_TO_SVG.entrySet()) {
                if (report.getNvcNeed().contains(e.getKey())) {
                    return e.getValue();
                }
            }
        }

        // 2. relationType-based candidate list
        String relationType = resolveRelationType(sim);
        List<String> candidates = BY_RELATION.getOrDefault(relationType, GENERAL_FALLBACK);
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

    private String resolveRelationType(MarketingSimulation sim) {
        if (sim.getSourceStoryId() != null) {
            return storyRepo.findById(sim.getSourceStoryId())
                .map(s -> s.getRelationType() != null ? s.getRelationType() : "general")
                .orElse("general");
        }
        return "general";
    }
}
