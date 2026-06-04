package com.againspring.aiuser.orchestrator.engine;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/**
 * archetypes.yml 런타임 카탈로그.
 * 글 생성 시 scenario/hot-button/감정비트 주입, 댓글 시 stance별 few-shot 제공.
 */
@Slf4j
@Component
public class ArchetypeCatalog {

    public record Archetype(
        String id,
        String category,
        String scenarioSkeleton,
        List<String> emotionalBeats,
        List<String> commonDetails,
        List<String> hotButtonPhrases,
        String progressiveFrame,
        String conservativeFrame,
        List<String> commonCommentsProgressive,
        List<String> commonCommentsConservative
    ) {}

    private final Map<String, Archetype> catalog = new HashMap<>();
    private final Map<String, List<Archetype>> byCategory = new HashMap<>();
    private static final Random RNG = new Random();

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void load() {
        // archetypes.yml structure: {archetypes: [{id:..., category:..., ...}, ...]}
        try (InputStream is = new ClassPathResource("personas/archetypes.yml").getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            if (root == null) { log.warn("archetypes.yml is empty"); return; }
            Object archetypesRaw = root.get("archetypes");
            if (!(archetypesRaw instanceof List)) {
                log.warn("archetypes.yml: 'archetypes' key not a list");
                return;
            }
            List<Map<String, Object>> archetypeList = (List<Map<String, Object>>) archetypesRaw;
            for (Map<String, Object> a : archetypeList) {
                if (!(a instanceof Map)) continue;
                String id = str(a.get("id"));
                String category = str(a.get("category"));
                if (id == null) continue;
                Archetype arch = new Archetype(
                    id, category != null ? category : "OTHER",
                    str(a.get("scenario_skeleton")),
                    toStrList(a.get("emotional_beats")),
                    toStrList(a.get("common_details")),
                    toStrList(a.get("hot_button_phrases")),
                    str(a.get("progressive_frame")),
                    str(a.get("conservative_frame")),
                    toStrList(a.get("common_comments_progressive")),
                    toStrList(a.get("common_comments_conservative"))
                );
                catalog.put(id, arch);
                byCategory.computeIfAbsent(arch.category(), k -> new ArrayList<>()).add(arch);
            }
            log.info("ArchetypeCatalog loaded: {} archetypes in {} categories", catalog.size(), byCategory.size());
        } catch (Exception e) {
            log.warn("Failed to load archetypes.yml: {}", e.getMessage());
        }
    }

    /** null-safe lookup by ID */
    public Optional<Archetype> get(String id) {
        return Optional.ofNullable(id != null ? catalog.get(id) : null);
    }

    /** Fallback: random archetype from category */
    public Optional<Archetype> byCategory(String category) {
        List<Archetype> list = byCategory.get(category);
        if (list == null || list.isEmpty()) return Optional.empty();
        return Optional.of(list.get(RNG.nextInt(list.size())));
    }

    public boolean isValidId(String id) {
        return id != null && catalog.containsKey(id);
    }

    /** Build a topic seed string for post generation */
    public String buildTopicSeed(Archetype a, Random rng) {
        StringBuilder sb = new StringBuilder();
        if (a.scenarioSkeleton() != null && !a.scenarioSkeleton().isBlank()) {
            sb.append("시나리오: ").append(a.scenarioSkeleton()).append("\n");
        }
        List<String> beats = a.emotionalBeats();
        if (beats != null && !beats.isEmpty()) {
            int n = Math.min(2, beats.size());
            List<String> shuffled = new ArrayList<>(beats);
            Collections.shuffle(shuffled, rng);
            sb.append("감정: ").append(String.join(", ", shuffled.subList(0, n))).append("\n");
        }
        List<String> hotButtons = a.hotButtonPhrases();
        if (hotButtons != null && !hotButtons.isEmpty()) {
            int n = Math.min(2, hotButtons.size());
            List<String> shuffled = new ArrayList<>(hotButtons);
            Collections.shuffle(shuffled, rng);
            sb.append("핵심 표현: ").append(String.join(" / ", shuffled.subList(0, n)));
        }
        return sb.toString().trim();
    }

    /** Build comment few-shot samples based on political orientation */
    public String buildCommentSamples(Archetype a, String politicalOrientation) {
        List<String> samples = "conservative".equalsIgnoreCase(politicalOrientation)
            ? a.commonCommentsConservative()
            : a.commonCommentsProgressive();
        if (samples == null || samples.isEmpty()) return null;
        List<String> shuffled = new ArrayList<>(samples);
        Collections.shuffle(shuffled, RNG);
        int n = Math.min(3, shuffled.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append("- ").append(shuffled.get(i)).append("\n");
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private List<String> toStrList(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<String> result = new ArrayList<>();
            for (Object item : list) if (item != null) result.add(item.toString());
            return result;
        }
        return Collections.emptyList();
    }

    private String str(Object obj) {
        return obj != null ? obj.toString() : null;
    }
}
