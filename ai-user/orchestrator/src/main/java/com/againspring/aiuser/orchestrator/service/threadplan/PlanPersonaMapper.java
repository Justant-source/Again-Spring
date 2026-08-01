package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.Persona;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds PLAN-request persona payloads: real nickname, voice_profile formality, and a structured
 * voiceProfile Map (never {@code String.valueOf(Map)}). Cast = full active pool — no fixed 24 cap.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanPersonaMapper {
    private final JdbcTemplate jdbcTemplate;

    /** Full active cast for an AI_POST / HUMAN_POST plan request. */
    public List<Map<String, Object>> mapCast(List<Persona> active) {
        if (active == null || active.isEmpty()) return List.of();
        Map<String, String> nicknames = loadNicknames(active.stream().map(Persona::getId).toList());
        List<Map<String, Object>> out = new ArrayList<>(active.size());
        for (Persona p : active) {
            out.add(toPersonaMap(p, nicknames.getOrDefault(p.getId(), p.getId())));
        }
        return out;
    }

    /** Author block for AI_POST — same fields as cast entry plus light demography hooks. */
    public Map<String, Object> mapAuthor(Persona author) {
        if (author == null) return Map.of();
        String nickname = loadNicknames(List.of(author.getId())).getOrDefault(author.getId(), author.getId());
        Map<String, Object> m = toPersonaMap(author, nickname);
        if (author.getSlangLevel() != null) m.put("slangLevel", author.getSlangLevel().doubleValue());
        if (author.getInterests() != null && !author.getInterests().isEmpty()) m.put("interests", author.getInterests());
        return m;
    }

    public Set<String> castIds(List<Map<String, Object>> cast) {
        if (cast == null || cast.isEmpty()) return Set.of();
        return cast.stream()
                .map(m -> String.valueOf(m.getOrDefault("personaId", "")))
                .filter(id -> !id.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static String formalityOf(Persona persona) {
        if (persona == null || persona.getVoiceProfile() == null) return "casual";
        Object f = persona.getVoiceProfile().get("formality");
        if (f == null) return "casual";
        String s = String.valueOf(f).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? "casual" : s;
    }

    /** Copy of voice_profile suitable for Jackson JSON object deserialization (W1-G Map/DTO). */
    public static Map<String, Object> voiceProfileMap(Persona persona) {
        if (persona == null || persona.getVoiceProfile() == null || persona.getVoiceProfile().isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(persona.getVoiceProfile()));
    }

    private Map<String, Object> toPersonaMap(Persona p, String nickname) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("personaId", p.getId());
        m.put("nickname", nickname == null || nickname.isBlank() ? p.getId() : nickname);
        m.put("voiceProfile", voiceProfileMap(p));
        m.put("formality", formalityOf(p));
        if (p.getArchetype() != null) m.put("archetype", p.getArchetype());
        if (p.getTier() != null) m.put("tier", p.getTier());
        return m;
    }

    private Map<String, String> loadNicknames(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        try {
            String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, nickname FROM users WHERE id IN (" + placeholders + ")",
                    ids.toArray());
            Map<String, String> out = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                Object nick = row.get("nickname");
                if (id != null && nick != null && !String.valueOf(nick).isBlank()) {
                    out.put(String.valueOf(id), String.valueOf(nick));
                }
            }
            return out;
        } catch (Exception e) {
            log.debug("PlanPersonaMapper nickname lookup failed: {}", e.getMessage());
            return Map.of();
        }
    }
}
