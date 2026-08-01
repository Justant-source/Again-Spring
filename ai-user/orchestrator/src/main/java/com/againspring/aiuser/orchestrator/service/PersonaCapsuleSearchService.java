package com.againspring.aiuser.orchestrator.service;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaMatchAudit;
import com.againspring.aiuser.orchestrator.repository.PersonaMatchAuditRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LLM-free story → persona top-K search over {@code persona_semantic_capsules}.
 * Embed via {@link AiLearningClient#embedOptional}; MariaDB {@code VEC_DISTANCE_COSINE}.
 * Degrades to active-persona interests ranking when embed/capsules unavailable.
 *
 * <p>Capsule entity/builder ownership is W3-B
 * ({@link com.againspring.aiuser.orchestrator.domain.PersonaSemanticCapsule});
 * this service queries vectors via JDBC only.
 */
@Slf4j
@Service
public class PersonaCapsuleSearchService {

    public static final String PURPOSE_AUTHOR = "AUTHOR_CANDIDATE";
    public static final String PURPOSE_COMMENT = "COMMENT_CANDIDATE";

    private static final Set<String> CATEGORIES = Set.of(
            "COUPLE", "MARRIED", "FRIEND", "FAMILY", "WORK", "OTHER");
    private static final Set<String> REGISTERS = Set.of("NATEPAN", "BLIND");
    /** Capsule rows to fetch before persona aggregation (plan §6.6). */
    private static final int CAPSULE_FETCH_LIMIT = 200;

    private final AiLearningClient aiLearningClient;
    private final JdbcTemplate jdbcTemplate;
    private final PersonaRepository personaRepository;
    private final PersonaMatchAuditRepository matchAuditRepository;

    public PersonaCapsuleSearchService(
            AiLearningClient aiLearningClient,
            JdbcTemplate jdbcTemplate,
            PersonaRepository personaRepository,
            PersonaMatchAuditRepository matchAuditRepository) {
        this.aiLearningClient = aiLearningClient;
        this.jdbcTemplate = jdbcTemplate;
        this.personaRepository = personaRepository;
        this.matchAuditRepository = matchAuditRepository;
    }

    /** Ranked persona match from capsule vector search (or interests fallback). */
    public record PersonaMatch(
            String personaId,
            double score,
            List<String> matchedCapsuleTypes,
            boolean fromFallback
    ) {}

    /** Raw capsule hit before persona aggregation. */
    public record CapsuleHit(
            String personaId,
            String capsuleType,
            double similarity,
            double weight
    ) {
        public double weightedScore() {
            return similarity * weight;
        }
    }

    @Builder
    public record SearchQuery(
            String searchText,
            int topK,
            /** Optional NATEPAN|BLIND voice_type filter. */
            String register,
            /** Optional 6광장 category for interests fallback (else inferred from searchText). */
            String category,
            /** AUTHOR_CANDIDATE | COMMENT_CANDIDATE — when set, best-effort audit rows. */
            String purpose,
            String correlationId,
            Long sourceExampleId
    ) {}

    public List<PersonaMatch> search(String searchText, int topK, String register) {
        return search(SearchQuery.builder()
                .searchText(searchText)
                .topK(topK)
                .register(register)
                .build());
    }

    public List<PersonaMatch> search(SearchQuery query) {
        int topK = Math.max(1, query.topK());
        String register = normalizeRegister(query.register());
        String category = resolveCategory(query.category(), query.searchText());

        List<PersonaMatch> results;
        String mode;

        if (query.searchText() == null || query.searchText().isBlank()) {
            results = fallbackByInterests(category, topK, register);
            mode = "interests_fallback";
        } else if (!hasActiveCapsules()) {
            log.debug("Capsule search degrade: no active capsules");
            results = fallbackByInterests(category, topK, register);
            mode = "interests_fallback";
        } else {
            Optional<List<Double>> embedding = aiLearningClient.embedOptional(query.searchText().trim());
            if (embedding.isEmpty()) {
                log.debug("Capsule search degrade: embed unavailable");
                results = fallbackByInterests(category, topK, register);
                mode = "interests_fallback";
            } else {
                List<CapsuleHit> hits = queryCapsuleHits(embedding.get(), register);
                results = aggregateByPersona(hits, topK);
                if (results.isEmpty()) {
                    results = fallbackByInterests(category, topK, register);
                    mode = "interests_fallback";
                } else {
                    mode = "capsule";
                }
            }
        }

        writeMatchAuditsBestEffort(query, results, mode, category);
        return results;
    }

    /**
     * Aggregate capsule hits by persona: score = max(similarity × weight);
     * matched types = distinct capsule_type among that persona's hits.
     */
    static List<PersonaMatch> aggregateByPersona(List<CapsuleHit> hits, int topK) {
        if (hits == null || hits.isEmpty() || topK <= 0) return List.of();

        Map<String, Double> bestScore = new HashMap<>();
        Map<String, Set<String>> types = new LinkedHashMap<>();

        for (CapsuleHit hit : hits) {
            if (hit == null || hit.personaId() == null || hit.personaId().isBlank()) continue;
            bestScore.merge(hit.personaId(), hit.weightedScore(), Math::max);
            types.computeIfAbsent(hit.personaId(), k -> new LinkedHashSet<>())
                    .add(hit.capsuleType() == null ? "UNKNOWN" : hit.capsuleType());
        }

        return bestScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(topK)
                .map(e -> new PersonaMatch(
                        e.getKey(),
                        e.getValue(),
                        List.copyOf(types.getOrDefault(e.getKey(), Set.of())),
                        false))
                .collect(Collectors.toList());
    }

    List<PersonaMatch> fallbackByInterests(String category, int topK, String register) {
        String cat = (category == null || category.isBlank()) ? "OTHER" : category.toUpperCase(Locale.ROOT);
        List<Persona> active = personaRepository.findByActiveTrue();
        if (active == null || active.isEmpty()) return List.of();

        return active.stream()
                .filter(p -> matchesRegister(p, register))
                .map(p -> {
                    double interest = 0.0;
                    if (p.getInterests() != null) {
                        interest = p.getInterests().getOrDefault(cat, 0.0);
                    }
                    return new PersonaMatch(p.getId(), interest, List.of(), true);
                })
                .sorted(Comparator.comparingDouble(PersonaMatch::score).reversed()
                        .thenComparing(PersonaMatch::personaId))
                .limit(topK)
                .collect(Collectors.toList());
    }

    private boolean hasActiveCapsules() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM persona_semantic_capsules WHERE active = TRUE",
                    Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("Capsule count failed (table missing?): {}", e.getMessage());
            return false;
        }
    }

    private List<CapsuleHit> queryCapsuleHits(List<Double> embedding, String register) {
        String vecStr = toVecFromText(embedding);
        String sql;
        Object[] args;
        if (register != null) {
            sql = """
                    SELECT c.persona_id, c.capsule_type,
                           1 - VEC_DISTANCE_COSINE(c.embedding, VEC_FromText(?)) AS similarity,
                           c.weight
                    FROM persona_semantic_capsules c
                    INNER JOIN personas p ON p.id = c.persona_id AND p.active = TRUE
                    WHERE c.active = TRUE
                      AND JSON_UNQUOTE(JSON_EXTRACT(p.voice_profile, '$.voice_type')) = ?
                    ORDER BY similarity DESC
                    LIMIT ?
                    """;
            args = new Object[]{vecStr, register, CAPSULE_FETCH_LIMIT};
        } else {
            sql = """
                    SELECT c.persona_id, c.capsule_type,
                           1 - VEC_DISTANCE_COSINE(c.embedding, VEC_FromText(?)) AS similarity,
                           c.weight
                    FROM persona_semantic_capsules c
                    INNER JOIN personas p ON p.id = c.persona_id AND p.active = TRUE
                    WHERE c.active = TRUE
                    ORDER BY similarity DESC
                    LIMIT ?
                    """;
            args = new Object[]{vecStr, CAPSULE_FETCH_LIMIT};
        }

        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> new CapsuleHit(
                    rs.getString("persona_id"),
                    rs.getString("capsule_type"),
                    rs.getDouble("similarity"),
                    rs.getDouble("weight")
            ), args);
        } catch (Exception e) {
            log.debug("Capsule vector query failed: {}", e.getMessage());
            return List.of();
        }
    }

    private void writeMatchAuditsBestEffort(
            SearchQuery query,
            List<PersonaMatch> results,
            String mode,
            String category) {
        if (query.purpose() == null || query.purpose().isBlank()) return;
        if (results == null || results.isEmpty()) return;

        String purpose = query.purpose().trim().toUpperCase(Locale.ROOT);
        String correlationId = (query.correlationId() == null || query.correlationId().isBlank())
                ? "capsule-search-" + System.currentTimeMillis()
                : query.correlationId().trim();
        long sourceExampleId = query.sourceExampleId() == null ? 0L : query.sourceExampleId();

        for (int i = 0; i < results.size(); i++) {
            PersonaMatch m = results.get(i);
            try {
                Map<String, Object> reasons = new LinkedHashMap<>();
                reasons.put("mode", mode);
                reasons.put("rank", i + 1);
                reasons.put("fromFallback", m.fromFallback());
                if (m.matchedCapsuleTypes() != null && !m.matchedCapsuleTypes().isEmpty()) {
                    reasons.put("matchedCapsuleTypes", m.matchedCapsuleTypes());
                }
                if (category != null) reasons.put("category", category);
                if (query.register() != null) reasons.put("register", normalizeRegister(query.register()));

                BigDecimal score = BigDecimal.valueOf(m.score()).setScale(5, RoundingMode.HALF_UP);
                matchAuditRepository.save(PersonaMatchAudit.builder()
                        .correlationId(correlationId)
                        .sourceExampleId(sourceExampleId)
                        .purpose(purpose)
                        .personaId(m.personaId())
                        .hardFilterPassed(true)
                        .semanticScore(score)
                        .finalScore(score)
                        .selected(false)
                        .reasons(reasons)
                        .build());
            } catch (Exception e) {
                log.debug("persona_match_audits write skipped: {}", e.getMessage());
            }
        }
    }

    static String toVecFromText(List<Double> embedding) {
        StringBuilder sb = new StringBuilder(embedding.size() * 12);
        sb.append('[');
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US, "%.8f", embedding.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    static String normalizeRegister(String register) {
        if (register == null || register.isBlank()) return null;
        String r = register.trim().toUpperCase(Locale.ROOT);
        return REGISTERS.contains(r) ? r : null;
    }

    static String resolveCategory(String category, String searchText) {
        if (category != null && !category.isBlank()) {
            String c = category.trim().toUpperCase(Locale.ROOT);
            return CATEGORIES.contains(c) ? c : "OTHER";
        }
        if (searchText == null || searchText.isBlank()) return "OTHER";
        String first = searchText.trim().split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        return CATEGORIES.contains(first) ? first : "OTHER";
    }

    private static boolean matchesRegister(Persona persona, String register) {
        if (register == null) return true;
        if (persona.getVoiceProfile() == null) return false;
        Object vt = persona.getVoiceProfile().get("voice_type");
        return vt != null && register.equalsIgnoreCase(Objects.toString(vt));
    }
}
