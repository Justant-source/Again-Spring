package com.againspring.aiuser.orchestrator.service.match;

import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaFactAssertion;
import com.againspring.aiuser.orchestrator.domain.PersonaMatchAudit;
import com.againspring.aiuser.orchestrator.repository.PersonaFactAssertionRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaMatchAuditRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.service.PersonaCapsuleSearchService;
import com.againspring.aiuser.orchestrator.domain.StoryProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * StoryProfile → author/commenter ranking (WP3 / W4-B).
 * Capsule search → hard filter (evaluable axes) → simplified score (§6.7 weights remapped to available signals).
 */
@Slf4j
@Service
public class PersonaMatcherService {

    public static final String PURPOSE_AUTHOR = PersonaCapsuleSearchService.PURPOSE_AUTHOR;
    public static final String PURPOSE_COMMENT = PersonaCapsuleSearchService.PURPOSE_COMMENT;

    /** Capsule pool before hard-filter (plan §6.6). */
    static final int AUTHOR_POOL = 20;
    static final int COMMENT_POOL = 60;

    static final double W_SEMANTIC = 0.45;
    static final double W_REGISTER = 0.25;
    static final double W_FACT = 0.15;
    static final double W_INTEREST = 0.15;

    private final PersonaCapsuleSearchService capsuleSearchService;
    private final PersonaRepository personaRepository;
    private final PersonaFactAssertionRepository factAssertionRepository;
    private final PersonaMatchAuditRepository matchAuditRepository;
    private final double authorThreshold;

    public PersonaMatcherService(
            PersonaCapsuleSearchService capsuleSearchService,
            PersonaRepository personaRepository,
            PersonaFactAssertionRepository factAssertionRepository,
            PersonaMatchAuditRepository matchAuditRepository,
            @Value("${ai-user.matcher.author-threshold:0.35}") double authorThreshold) {
        this.capsuleSearchService = capsuleSearchService;
        this.personaRepository = personaRepository;
        this.factAssertionRepository = factAssertionRepository;
        this.matchAuditRepository = matchAuditRepository;
        this.authorThreshold = authorThreshold;
    }

    public double authorThreshold() {
        return authorThreshold;
    }

    public List<RankedPersona> matchAuthors(
            StoryProfile profile, int topK, long sourceExampleId, String correlationId) {
        return match(profile, Math.max(1, topK), AUTHOR_POOL, PURPOSE_AUTHOR, sourceExampleId, correlationId);
    }

    public List<RankedPersona> matchCommenters(
            StoryProfile profile, int topK, long sourceExampleId, String correlationId) {
        return match(profile, Math.max(1, topK), COMMENT_POOL, PURPOSE_COMMENT, sourceExampleId, correlationId);
    }

    public Optional<RankedPersona> bestAuthorAbove(
            StoryProfile profile,
            double threshold,
            long sourceExampleId,
            String correlationId) {
        List<RankedPersona> ranked = matchAuthors(profile, 1, sourceExampleId, correlationId);
        if (ranked.isEmpty()) return Optional.empty();
        RankedPersona best = ranked.get(0);
        return best.score() >= threshold ? Optional.of(best) : Optional.empty();
    }

    /** Convenience: default author threshold from config. */
    public Optional<RankedPersona> bestAuthorAbove(
            StoryProfile profile, long sourceExampleId, String correlationId) {
        return bestAuthorAbove(profile, authorThreshold, sourceExampleId, correlationId);
    }

    private List<RankedPersona> match(
            StoryProfile profile,
            int topK,
            int poolSize,
            String purpose,
            long sourceExampleId,
            String correlationId) {
        if (profile == null) return List.of();

        String searchDoc = profile.toSearchDocument();
        String register = PersonaHardFilter.normalizeRegister(profile.sourceRegister());
        String category = normalizeCategory(profile.category());

        // purpose omitted so capsule search does not double-write audits
        List<PersonaCapsuleSearchService.PersonaMatch> capsuleHits =
                capsuleSearchService.search(PersonaCapsuleSearchService.SearchQuery.builder()
                        .searchText(searchDoc)
                        .topK(poolSize)
                        .register(register)
                        .category(category)
                        .build());

        String corr = (correlationId == null || correlationId.isBlank())
                ? "matcher-" + purpose + "-" + System.currentTimeMillis()
                : correlationId.trim();

        List<RankedPersona> scored = new ArrayList<>();
        List<AuditDraft> audits = new ArrayList<>();

        for (PersonaCapsuleSearchService.PersonaMatch hit : capsuleHits) {
            if (hit == null || hit.personaId() == null) continue;
            Optional<Persona> personaOpt = personaRepository.findById(hit.personaId());
            if (personaOpt.isEmpty()) continue;
            Persona persona = personaOpt.get();
            List<PersonaFactAssertion> facts =
                    factAssertionRepository.findByPersonaId(persona.getId());

            FilterResult filter = PersonaHardFilter.filter(persona, facts, profile);
            if (!filter.passed()) {
                audits.add(AuditDraft.failed(purpose, persona.getId(), hit, filter, corr, sourceExampleId));
                continue;
            }

            RankedPersona ranked = score(persona, hit, filter, category, register);
            scored.add(ranked);
            audits.add(AuditDraft.passed(purpose, ranked, filter, corr, sourceExampleId));
        }

        scored.sort(Comparator.comparingDouble(RankedPersona::score).reversed()
                .thenComparing(RankedPersona::personaId));

        List<RankedPersona> top = scored.stream().limit(topK).toList();
        writeAuditsBestEffort(audits, top);
        return top;
    }

    RankedPersona score(
            Persona persona,
            PersonaCapsuleSearchService.PersonaMatch hit,
            FilterResult filter,
            String category,
            String storyRegister) {
        double semantic = clamp01(hit.score());
        double registerMatch = registerMatchScore(persona, storyRegister);
        double factRatio = explicitFactMatchRatio(filter.reasons());
        double interest = interestScore(persona, category);

        double finalScore = W_SEMANTIC * semantic
                + W_REGISTER * registerMatch
                + W_FACT * factRatio
                + W_INTEREST * interest;

        List<String> reasons = new ArrayList<>(filter.reasons());
        reasons.add(String.format(Locale.US, "SCORE:semantic=%.4f", semantic));
        reasons.add(String.format(Locale.US, "SCORE:register=%.2f", registerMatch));
        reasons.add(String.format(Locale.US, "SCORE:fact_ratio=%.4f", factRatio));
        reasons.add(String.format(Locale.US, "SCORE:interest=%.4f", interest));
        reasons.add(String.format(Locale.US, "SCORE:final=%.4f", finalScore));

        return new RankedPersona(
                persona.getId(),
                finalScore,
                semantic,
                registerMatch,
                factRatio,
                interest,
                reasons,
                hit.matchedCapsuleTypes(),
                hit.fromFallback());
    }

    static double explicitFactMatchRatio(List<String> reasons) {
        int evaluated = PersonaHardFilter.countEvaluatedFactAxes(reasons);
        if (evaluated == 0) return 1.0;
        int matched = PersonaHardFilter.countEvaluatedFactPasses(reasons);
        return (double) matched / (double) evaluated;
    }

    static double registerMatchScore(Persona persona, String storyRegister) {
        if (storyRegister == null) return 1.0;
        if (persona.getVoiceProfile() == null) return 0.0;
        Object vt = persona.getVoiceProfile().get("voice_type");
        if (vt == null) return 0.0;
        String norm = PersonaHardFilter.normalizeRegister(vt.toString());
        return storyRegister.equals(norm) ? 1.0 : 0.0;
    }

    static double interestScore(Persona persona, String category) {
        if (persona.getInterests() == null || category == null) return 0.0;
        Double v = persona.getInterests().get(category);
        return v == null ? 0.0 : clamp01(v);
    }

    static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "OTHER";
        String c = category.trim().toUpperCase(Locale.ROOT);
        return switch (c) {
            case "COUPLE", "MARRIED", "FRIEND", "FAMILY", "WORK", "OTHER" -> c;
            default -> "OTHER";
        };
    }

    static double clamp01(double v) {
        if (Double.isNaN(v) || v < 0) return 0.0;
        return Math.min(1.0, v);
    }

    private void writeAuditsBestEffort(List<AuditDraft> drafts, List<RankedPersona> selectedTop) {
        if (drafts == null || drafts.isEmpty()) return;
        var selectedIds = selectedTop.stream().map(RankedPersona::personaId).collect(java.util.stream.Collectors.toSet());
        for (AuditDraft d : drafts) {
            try {
                boolean selected = d.hardPassed && selectedIds.contains(d.personaId);
                matchAuditRepository.save(PersonaMatchAudit.builder()
                        .correlationId(d.correlationId)
                        .sourceExampleId(d.sourceExampleId)
                        .purpose(d.purpose)
                        .personaId(d.personaId)
                        .hardFilterPassed(d.hardPassed)
                        .semanticScore(scale(d.semanticScore))
                        .finalScore(d.finalScore == null ? null : scale(d.finalScore))
                        .selected(selected)
                        .reasons(d.reasons)
                        .build());
            } catch (Exception e) {
                log.debug("persona_match_audits write skipped: {}", e.getMessage());
            }
        }
    }

    private static BigDecimal scale(double v) {
        return BigDecimal.valueOf(v).setScale(5, RoundingMode.HALF_UP);
    }

    private record AuditDraft(
            String purpose,
            String personaId,
            boolean hardPassed,
            Double semanticScore,
            Double finalScore,
            Map<String, Object> reasons,
            String correlationId,
            long sourceExampleId
    ) {
        static AuditDraft failed(
                String purpose,
                String personaId,
                PersonaCapsuleSearchService.PersonaMatch hit,
                FilterResult filter,
                String correlationId,
                long sourceExampleId) {
            Map<String, Object> reasons = baseReasons(filter, hit);
            reasons.put("hardFilterPassed", false);
            return new AuditDraft(
                    purpose, personaId, false,
                    hit.score(), null, reasons, correlationId, sourceExampleId);
        }

        static AuditDraft passed(
                String purpose,
                RankedPersona ranked,
                FilterResult filter,
                String correlationId,
                long sourceExampleId) {
            Map<String, Object> reasons = new LinkedHashMap<>();
            reasons.put("hardFilterPassed", true);
            reasons.put("filterReasons", filter.reasons());
            reasons.put("scoreReasons", ranked.reasons());
            reasons.put("semanticScore", ranked.semanticScore());
            reasons.put("registerMatch", ranked.registerMatch());
            reasons.put("explicitFactMatchRatio", ranked.explicitFactMatchRatio());
            reasons.put("interestCategoryScore", ranked.interestCategoryScore());
            reasons.put("finalScore", ranked.score());
            reasons.put("fromFallback", ranked.fromFallback());
            if (!ranked.matchedCapsuleTypes().isEmpty()) {
                reasons.put("matchedCapsuleTypes", ranked.matchedCapsuleTypes());
            }
            List<String> unevaluated = filter.reasons().stream()
                    .filter(r -> r != null && r.startsWith("UNEVALUATED:"))
                    .toList();
            if (!unevaluated.isEmpty()) {
                reasons.put("unevaluatedAxes", unevaluated);
            }
            return new AuditDraft(
                    purpose, ranked.personaId(), true,
                    ranked.semanticScore(), ranked.score(),
                    reasons, correlationId, sourceExampleId);
        }

        private static Map<String, Object> baseReasons(
                FilterResult filter, PersonaCapsuleSearchService.PersonaMatch hit) {
            Map<String, Object> reasons = new LinkedHashMap<>();
            reasons.put("filterReasons", filter.reasons());
            reasons.put("semanticScore", hit.score());
            reasons.put("fromFallback", hit.fromFallback());
            List<String> unevaluated = filter.reasons().stream()
                    .filter(r -> r != null && r.startsWith("UNEVALUATED:"))
                    .toList();
            if (!unevaluated.isEmpty()) {
                reasons.put("unevaluatedAxes", unevaluated);
            }
            return reasons;
        }
    }
}
