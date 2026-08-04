package com.againspring.aiuser.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 60 metaphor illustration IDs (docs/frontend/design/specs/metaphor-illustration-system.md).
 * Used to validate PLAN {@code metaphor_id} and inject the compact catalog into prompts.
 */
public final class MetaphorCatalog {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> IDS;
    private static final String COMPACT;

    private static final Map<String, String> CATEGORY_FALLBACK = Map.of(
            "COUPLE", "tangled-thread",
            "MARRIED", "dying-stove",
            "FRIEND", "broken-thread",
            "FAMILY", "empty-dining-table",
            "WORK", "gears-not-meshing",
            "OTHER", "empty-chair"
    );

    static {
        Set<String> ids = new LinkedHashSet<>();
        String compact;
        try {
            compact = new ClassPathResource("metaphors/catalog-compact.txt")
                    .getContentAsString(StandardCharsets.UTF_8).trim();
            for (String line : compact.split("\n")) {
                String id = line.split("\\|", 2)[0].trim();
                if (!id.isEmpty()) ids.add(id);
            }
            try (InputStream in = new ClassPathResource("metaphors/catalog.json").getInputStream()) {
                JsonNode arr = JSON.readTree(in);
                if (arr != null && arr.isArray()) {
                    for (JsonNode n : arr) {
                        String id = n.path("id").asText("").trim();
                        if (!id.isEmpty()) ids.add(id);
                    }
                }
            }
        } catch (Exception e) {
            compact = "";
            ids = Set.of();
        }
        IDS = Collections.unmodifiableSet(ids);
        COMPACT = compact;
    }

    private MetaphorCatalog() {}

    public static Set<String> ids() {
        return IDS;
    }

    public static String compactCatalog() {
        return COMPACT;
    }

    public static boolean isKnown(String id) {
        return id != null && IDS.contains(id.trim());
    }

    /**
     * Normalize LLM metaphor_id; unknown/blank → category fallback when category known, else null.
     */
    public static String sanitize(String proposed, String category) {
        if (proposed != null) {
            String id = proposed.trim().toLowerCase(Locale.ROOT);
            if (isKnown(id)) return id;
        }
        if (category == null || category.isBlank()) return null;
        String key = category.trim().toUpperCase(Locale.ROOT);
        String fb = CATEGORY_FALLBACK.get(key);
        return isKnown(fb) ? fb : null;
    }
}
