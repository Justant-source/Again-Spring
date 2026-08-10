package com.againspring.aiuser.orchestrator.service.threadplan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drops plan comment candidates whose {@code personaId} is a story-side account
 * (post author and/or partner). Those personas write the post bodies; bystander
 * comments must use other cast members — otherwise the OP appears to reply as a stranger.
 */
public final class StoryPersonaCommentFilter {
    private StoryPersonaCommentFilter() {}

    /**
     * Mutates {@code response.items} (or leaves other keys untouched). Returns how many
     * items were removed (including replies whose parent was removed).
     */
    @SuppressWarnings("unchecked")
    public static int stripFromResponse(Map<String, Object> response, Collection<String> excludedPersonaIds) {
        if (response == null || excludedPersonaIds == null || excludedPersonaIds.isEmpty()) return 0;
        Object raw = response.get("items");
        if (!(raw instanceof List<?> list) || list.isEmpty()) return 0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object row : list) {
            if (row instanceof Map<?, ?> m) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getKey() != null) copy.put(String.valueOf(e.getKey()), e.getValue());
                }
                items.add(copy);
            }
        }
        int before = items.size();
        List<Map<String, Object>> kept = strip(items, excludedPersonaIds);
        response.put("items", kept);
        return before - kept.size();
    }

    /** Keep order; cascade-drop replies whose parentRef was removed. */
    public static List<Map<String, Object>> strip(List<Map<String, Object>> items,
                                                  Collection<String> excludedPersonaIds) {
        if (items == null || items.isEmpty() || excludedPersonaIds == null || excludedPersonaIds.isEmpty()) {
            return items == null ? List.of() : items;
        }
        Set<String> excluded = new HashSet<>();
        for (String id : excludedPersonaIds) {
            if (id != null && !id.isBlank()) excluded.add(id);
        }
        if (excluded.isEmpty()) return items;

        Set<String> removedRefs = new HashSet<>();
        List<Map<String, Object>> pass1 = new ArrayList<>(items.size());
        for (Map<String, Object> row : items) {
            if (row == null) continue;
            String personaId = text(row.get("personaId"));
            String ref = text(row.get("ref"));
            if (!personaId.isBlank() && excluded.contains(personaId)) {
                if (!ref.isBlank()) removedRefs.add(ref);
                continue;
            }
            pass1.add(row);
        }

        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (Map<String, Object> row : pass1) {
                String parent = text(row.get("parentRef"));
                String ref = text(row.get("ref"));
                if (!parent.isBlank() && removedRefs.contains(parent) && removedRefs.add(ref)) {
                    progressed = true;
                }
            }
        }

        List<Map<String, Object>> kept = new ArrayList<>(pass1.size());
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : pass1) {
            String ref = text(row.get("ref"));
            if (!ref.isBlank() && removedRefs.contains(ref)) continue;
            if (!ref.isBlank() && !seen.add(ref)) continue;
            kept.add(row);
        }
        return kept;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
