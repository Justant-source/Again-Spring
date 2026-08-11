package com.againspring.aiuser.orchestrator.service.threadplan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Paired-author hold metadata embedded in {@code ai_scheduled_posts.candidates_json}
 * (same pattern as {@link AiPostBundleService#SOURCE_PROVENANCE_KEY}).
 */
public final class PairedHoldMeta {
    public static final String KEY = "_pairedMeta";
    public static final String ORIGIN_PAIRED = "PAIRED";

    private PairedHoldMeta() { }

    public static Map<String, Object> wrap(String partnerPersonaId, String relationType,
                                           String correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("partnerPersonaId", partnerPersonaId);
        meta.put("relationType", relationType);
        meta.put("correlationId", correlationId);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(KEY, meta);
        return root;
    }

    public static Optional<Map<String, Object>> read(ObjectMapper mapper, String candidatesJson) {
        if (mapper == null || candidatesJson == null || candidatesJson.isBlank()) return Optional.empty();
        try {
            Map<String, Object> root = mapper.readValue(candidatesJson, new TypeReference<>() { });
            Object raw = root.get(KEY);
            if (raw instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
                return Optional.of(out);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Optional.empty();
    }

    public static String text(Map<String, Object> meta, String key) {
        if (meta == null) return null;
        Object v = meta.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }
}
