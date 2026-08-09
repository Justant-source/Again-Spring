package com.againspring.aiuser.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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

    /**
     * Normalize LLM metaphor_ids (ranked list). Returns 3-5 unique ids:
     * 1. Validate & normalize each proposed id (lowercase).
     * 2. Dedup while preserving first-seen order (LLM ranking).
     * 3. If < 3 valid ids, pad deterministically: use category fallback, then sequentially add catalog ids (in order) until size 3.
     * 4. Cap at 5 entries.
     * @param proposed list of metaphor ids from LLM (may be null, empty, or contain invalid/duplicate ids).
     * @param category story category (COUPLE, MARRIED, FRIEND, FAMILY, WORK, OTHER) for fallback logic.
     * @return list of 3-5 unique valid ids, or empty list if catalog is empty.
     */
    public static List<String> sanitizeList(List<String> proposed, String category) {
        if (IDS.isEmpty()) return Collections.emptyList();

        // 1. Validate & normalize proposed ids, preserving order & deduping
        LinkedHashSet<String> valid = new LinkedHashSet<>();
        if (proposed != null) {
            for (String id : proposed) {
                if (id != null) {
                    String normalized = id.trim().toLowerCase(Locale.ROOT);
                    if (isKnown(normalized)) {
                        valid.add(normalized);
                    }
                }
            }
        }

        // 2. If we already have 3-5, cap at 5 and return
        if (valid.size() >= 3) {
            List<String> result = new ArrayList<>(valid);
            if (result.size() > 5) result = result.subList(0, 5);
            return Collections.unmodifiableList(result);
        }

        // 3. Pad: add category fallback if available, then sequentially fill from catalog
        String fallbackId = null;
        if (category != null && !category.isBlank()) {
            String key = category.trim().toUpperCase(Locale.ROOT);
            fallbackId = CATEGORY_FALLBACK.get(key);
            if (isKnown(fallbackId)) {
                valid.add(fallbackId);
            }
        }

        // 4. If still < 3, sequentially add ids from catalog (in iteration order) until we reach 3
        if (valid.size() < 3) {
            for (String id : IDS) {
                if (!valid.contains(id)) {
                    valid.add(id);
                    if (valid.size() >= 3) break;
                }
            }
        }

        List<String> result = new ArrayList<>(valid);
        return Collections.unmodifiableList(result);
    }
}
