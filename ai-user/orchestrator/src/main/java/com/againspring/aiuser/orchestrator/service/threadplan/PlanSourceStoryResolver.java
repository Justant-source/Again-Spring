package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.service.PersonaHistoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves example_bank grounding for PLAN AI_POST generation via popularity claim
 * ({@link AiLearningClient#claimPopularSource}) — no findSimilar primary selection,
 * no archetype freestyle fallback when the claim pool is empty.
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

    /**
     * Claim a popular crawl source and build reconstruct grounding.
     * Empty claim → {@link Optional#empty()} (caller must skip the slot; no freestyle).
     *
     * @param author          may be null (persona chosen after claim); recentBodies empty then
     * @param preferredSource {@code "blind"} or {@code "natepan"} (required)
     * @param categoryHint    optional; ignored for selection (reserved for callers/logging)
     */
    public Optional<ResolvedSource> claimAndResolve(
            Persona author,
            String preferredSource,
            String reservationKey,
            Instant reserveUntil,
            String categoryHint) {
        String source = normalizePreferredSource(preferredSource);
        if (source == null || reservationKey == null || reservationKey.isBlank() || reserveUntil == null) {
            log.debug("claimAndResolve skipped: invalid args source={} key={} until={} categoryHint={}",
                    preferredSource, reservationKey, reserveUntil, categoryHint);
            return Optional.empty();
        }

        Optional<AiLearningClient.ExampleItem> claimed =
                aiLearningClient.claimPopularSource(source, reservationKey, reserveUntil);
        if (claimed.isEmpty()) {
            log.info("claimAndResolve empty: no popular source for preferredSource={} reservationKey={}",
                    source, reservationKey);
            return Optional.empty();
        }

        AiLearningClient.ExampleItem primary = claimed.get();
        String topicSeed = truncate(primary.getContent(), 200);
        if (topicSeed == null) topicSeed = "";

        String dynamicExamples = optionalStyleExamples(author, source);
        List<String> recent = loadRecentPostBodies(author, 3);

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("topicSeed", topicSeed);
        ctx.put("reconstructMode", true);
        ctx.put("exampleId", primary.getId());
        ctx.put("title", primary.getTitle() == null ? "" : primary.getTitle());
        ctx.put("body", truncate(primary.getContent(), 2000));
        ctx.put("source", primary.getSource() != null ? primary.getSource() : source);
        ctx.put("sourceUrl", primary.getSourceUrl());

        return Optional.of(new ResolvedSource(
                topicSeed,
                ctx,
                true,
                primary.getId(),
                primary.getContent(),
                primary.getSource() != null ? primary.getSource() : source,
                primary.getSourceUrl(),
                primary.getTitle(),
                dynamicExamples,
                recent
        ));
    }

    /**
     * Legacy entry: maps persona {@code voice_type} → preferredSource and claims with a
     * short-lived reservation. Does not freestyle when the pool is empty — throws instead.
     * Prefer {@link #claimAndResolve} for new callers.
     */
    public ResolvedSource resolve(Persona author, String category, String topicHint) {
        String preferred = preferredSourceFromVoice(author);
        String reservationKey = "legacy-" + UUID.randomUUID();
        Instant reserveUntil = Instant.now().plus(24, ChronoUnit.HOURS);
        // topicHint unused for selection (popularity claim replaces topicSeed/findSimilar)
        return claimAndResolve(author, preferred, reservationKey, reserveUntil, category)
                .orElseThrow(() -> new IllegalStateException(
                        "No popular source available for preferredSource=" + preferred
                                + " category=" + category
                                + (topicHint == null || topicHint.isBlank() ? "" : " topicHint=" + topicHint.trim())));
    }

    /** BLIND → blind; everything else (incl. null) → natepan. */
    static String preferredSourceFromVoice(Persona author) {
        if (author != null && author.getVoiceProfile() != null) {
            Object vt = author.getVoiceProfile().get("voice_type");
            if (vt != null && "BLIND".equalsIgnoreCase(String.valueOf(vt).trim())) {
                return "blind";
            }
        }
        return "natepan";
    }

    /** Accept blind|natepan (any case); null/blank/other → null. */
    static String normalizePreferredSource(String preferredSource) {
        if (preferredSource == null || preferredSource.isBlank()) return null;
        String s = preferredSource.trim().toLowerCase();
        if ("blind".equals(s) || "natepan".equals(s)) return s;
        return null;
    }

    /** Best-effort style anchors; never blocks claim path. */
    private String optionalStyleExamples(Persona author, String source) {
        try {
            String register = resolveRegister(author);
            List<AiLearningClient.ExampleItem> style =
                    aiLearningClient.styleSample(source, "POST", register, 2, 350);
            if (style == null || style.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (AiLearningClient.ExampleItem e : style) {
                if (e == null || e.getContent() == null || e.getContent().isBlank()) continue;
                if (!sb.isEmpty()) sb.append("\n---\n");
                sb.append(truncate(e.getContent(), 350));
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("optional styleSample failed source={}: {}", source, e.getMessage());
            return "";
        }
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
