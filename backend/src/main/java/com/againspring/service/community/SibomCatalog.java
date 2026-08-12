package com.againspring.service.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sibom character catalog SSOT (classpath {@code sibom/catalog.json}, mirrored from sprouts package).
 * Used for keyword shortlist scoring, prompt one-line cards, and {@link SibomPlanGuard} — never dump full catalog to LLM.
 */
public final class SibomCatalog {

    public record Entry(
            String id,
            String arc,
            int people,
            String meaning,
            String caption,
            String swapGroup,
            String siblingBottom,
            int maxChars,
            List<String> keywords,
            String trigger,
            List<String> triggerTokens
    ) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Entry> BY_ID;
    private static final List<Entry> ORDERED;

    static {
        Map<String, Entry> map = new LinkedHashMap<>();
        try (InputStream in = new ClassPathResource("sibom/catalog.json").getInputStream()) {
            JsonNode root = JSON.readTree(in);
            JsonNode images = root.path("images");
            if (images.isArray()) {
                for (JsonNode n : images) {
                    String id = n.path("id").asText("").trim();
                    if (id.isEmpty()) continue;
                    String sibling = textOrNull(n, "sibling_bottom");
                    String trigger = n.path("trigger").asText("").trim();
                    map.put(id, new Entry(
                            id,
                            n.path("arc").asText("").trim(),
                            n.path("people").asInt(1),
                            n.path("meaning").asText("").trim(),
                            n.path("caption").asText("").trim(),
                            n.path("swap_group").asText("").trim(),
                            sibling,
                            n.path("maxChars").asInt(SibomPlanGuard.CAPTION_MAX_CHARS),
                            readStringArray(n.path("keywords")),
                            trigger,
                            tokenizeTrigger(trigger)
                    ));
                }
            }
        } catch (Exception ignored) {
            map = Map.of();
        }
        BY_ID = Collections.unmodifiableMap(map);
        ORDERED = List.copyOf(map.values());
    }

    private SibomCatalog() {}

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText(null);
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    private static List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode n : node) {
            String s = n.asText("").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return List.copyOf(out);
    }

    /**
     * Light trigger tokens for soft scoring: strip boilerplate and keep short phrases.
     */
    static List<String> tokenizeTrigger(String trigger) {
        if (trigger == null || trigger.isBlank()) return List.of();
        String cleaned = trigger
                .replaceAll("라고\\s*(서술하는|말하는)\\s*대목$", "")
                .replaceAll("(서술하는|말하는)\\s*대목$", "")
                .trim();
        if (cleaned.isEmpty()) return List.of();
        String[] parts = cleaned.split("[\\s·,，、/]+");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (t.length() >= 2) tokens.add(t);
        }
        return List.copyOf(tokens);
    }

    public static boolean isKnown(String id) {
        return id != null && BY_ID.containsKey(id.trim());
    }

    public static Optional<Entry> get(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(BY_ID.get(id.trim()));
    }

    public static Set<String> ids() {
        return BY_ID.keySet();
    }

    /** Catalog order (stable for shortlist tie-breaks). */
    public static List<Entry> entries() {
        return ORDERED;
    }

    /** One-line prompt card: {@code id|arc|people|meaning|maxChars}. */
    public static String oneLineCard(Entry e) {
        return e.id() + "|" + e.arc() + "|" + e.people() + "|" + e.meaning() + "|" + e.maxChars();
    }

    public static int size() {
        return BY_ID.size();
    }
}
