package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.service.PersonaHistoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Resolves example_bank grounding for PLAN AI_POST generation — port of legacy
 * {@code ActionExecutor.executePost} RAG / reconstruct / anti-self-copy ideas.
 * Always excludes {@code SELF_GENERATED} (via {@link AiLearningClient#findSimilar} register overload).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanSourceStoryResolver {
    private final AiLearningClient aiLearningClient;
    private final PersonaHistoryStore personaHistoryStore;

    public record ResolvedSource(
            String topicSeed,
            Map<String, Object> sourceContext,
            boolean reconstructMode,
            Long sourceExampleId,
            String sourceBody,
            String sourceCommunity,
            String sourceUrl,
            String sourceTitle,
            String dynamicExamples,
            List<String> recentBodies
    ) {
        public Map<String, Object> provenanceForTrace() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (sourceExampleId != null) m.put("sourceExampleId", sourceExampleId);
            if (sourceCommunity != null) m.put("sourceCommunity", sourceCommunity);
            if (sourceUrl != null) m.put("sourceUrl", sourceUrl);
            if (sourceTitle != null) m.put("sourceOriginalTitle", sourceTitle);
            if (sourceBody != null) m.put("sourceOriginalBody", truncate(sourceBody, 2000));
            m.put("reconstructMode", reconstructMode);
            return m;
        }
    }

    public ResolvedSource resolve(Persona author, String category, String topicHint) {
        String cat = category == null || category.isBlank() ? "OTHER" : category;
        String topicSeed = resolveTopicSeed(author, cat, topicHint);
        String register = resolveRegister(author);

        List<AiLearningClient.ExampleItem> examples = topicSeed == null || topicSeed.isBlank()
                ? List.of()
                : aiLearningClient.findSimilar(topicSeed, "POST", cat, 3, register);

        AiLearningClient.ExampleItem primary = null;
        if (!examples.isEmpty() && examples.get(0).hasSourceProvenance()) {
            primary = examples.get(0);
            topicSeed = truncate(primary.getContent(), 200);
        }

        String dynamicExamples = "";
        if (!examples.isEmpty()) {
            List<AiLearningClient.ExampleItem> style = primary != null
                    ? examples.subList(1, examples.size())
                    : examples;
            if (!style.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (AiLearningClient.ExampleItem e : style) {
                    if (!sb.isEmpty()) sb.append("\n---\n");
                    sb.append(truncate(e.getContent(), 350));
                }
                dynamicExamples = sb.toString();
            }
        }

        List<String> recent = loadRecentPostBodies(author, 3);

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("topicSeed", topicSeed == null ? "" : topicSeed);
        ctx.put("reconstructMode", primary != null);
        if (primary != null) {
            ctx.put("exampleId", primary.getId());
            ctx.put("title", primary.getTitle() == null ? "" : primary.getTitle());
            ctx.put("body", truncate(primary.getContent(), 2000));
            ctx.put("source", primary.getSource());
            ctx.put("sourceUrl", primary.getSourceUrl());
        } else if (!examples.isEmpty()) {
            AiLearningClient.ExampleItem first = examples.get(0);
            ctx.put("exampleId", first.getId());
            ctx.put("title", first.getTitle() == null ? "" : first.getTitle());
            ctx.put("body", truncate(first.getContent(), 1200));
            ctx.put("source", first.getSource());
            ctx.put("sourceUrl", first.getSourceUrl());
        }

        return new ResolvedSource(
                topicSeed == null ? "" : topicSeed,
                ctx,
                primary != null,
                primary != null ? primary.getId() : (!examples.isEmpty() ? examples.get(0).getId() : null),
                primary != null ? primary.getContent() : null,
                primary != null ? primary.getSource() : null,
                primary != null ? primary.getSourceUrl() : null,
                primary != null ? primary.getTitle() : null,
                dynamicExamples,
                recent
        );
    }

    private String resolveTopicSeed(Persona author, String category, String topicHint) {
        if (topicHint != null && !topicHint.isBlank()) return topicHint.trim();

        List<AiLearningClient.DailyTopicItem> daily = aiLearningClient.fetchDailyTopics(category, 5);
        if (!daily.isEmpty()) {
            int pick = ThreadLocalRandom.current().nextInt(Math.min(2, daily.size()));
            AiLearningClient.DailyTopicItem chosen = daily.get(pick);
            aiLearningClient.markTopicUsed(chosen.getId());
            return chosen.getText();
        }

        String archetype = author != null && author.getArchetype() != null ? author.getArchetype() : "일반";
        return archetype + " 관점의 " + category + " 갈등 사연";
    }

    private static String resolveRegister(Persona persona) {
        if (persona == null || persona.getVoiceProfile() == null) return "casual";
        Object formality = persona.getVoiceProfile().get("formality");
        if ("polite".equalsIgnoreCase(String.valueOf(formality))) return "polite";
        return "casual";
    }

    private List<String> loadRecentPostBodies(Persona author, int n) {
        if (author == null) return List.of();
        try {
            List<String> rows = personaHistoryStore.loadRecentPosts(author.getId(), n);
            List<String> out = new ArrayList<>();
            for (String body : rows) {
                if (body != null && !body.isBlank()) out.add(body);
            }
            return out;
        } catch (Exception e) {
            log.debug("recent posts load failed persona={}: {}", author.getId(), e.getMessage());
            return List.of();
        }
    }

    static String truncate(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** Prompt-friendly bullet list of recent bodies (anti-self-copy). */
    public static List<String> recentOutputsForRequest(List<String> bodies, int eachMax) {
        if (bodies == null || bodies.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(bodies.size());
        for (int i = bodies.size() - 1; i >= 0; i--) {
            out.add(truncate(bodies.get(i).replaceAll("\\s+", " ").trim(), eachMax));
        }
        return out;
    }
}
