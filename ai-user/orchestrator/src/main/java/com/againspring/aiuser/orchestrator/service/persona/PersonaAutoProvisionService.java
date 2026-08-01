package com.againspring.aiuser.orchestrator.service.persona;

import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaMatchAudit;
import com.againspring.aiuser.orchestrator.repository.PersonaMatchAuditRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.seed.PersonaDuplicateDetector;
import com.againspring.aiuser.orchestrator.seed.PersonaFactory;
import com.againspring.aiuser.orchestrator.service.capsule.PersonaCapsuleService;
import com.againspring.aiuser.orchestrator.domain.StoryProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * WP3 W4-C: on author-match miss, create a minimal active persona — or refuse when a near-duplicate exists.
 * Accepts Map hints or W4-A {@link com.againspring.aiuser.orchestrator.domain.StoryProfile}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonaAutoProvisionService {

    public static final String PURPOSE_AUTO_CREATE_FAILED = "PERSONA_AUTO_CREATE_FAILED";
    public static final String PURPOSE_AUTO_CREATED = "PERSONA_AUTO_CREATED";
    /** Soft distance ≤ 1 blocks activation (exact or adjacent age / blank job). */
    public static final int DEFAULT_SOFT_DISTANCE = 1;

    private final PersonaRepository personaRepository;
    private final PersonaFactory personaFactory;
    private final PersonaCapsuleService personaCapsuleService;
    private final PersonaMatchAuditRepository matchAuditRepository;

    /**
     * Story-shaped hints. Prefer explicit identity keys; category/register drive voice + interests.
     * Compatible with a future StoryProfile serialized as Map or bean getters via {@link #fromStoryLike(Object)}.
     */
    public record ProvisionHints(
            String category,
            String sourceRegister,
            String age,
            String gender,
            String job,
            String region,
            String title,
            String body,
            Long sourceExampleId,
            String correlationId
    ) {
        public static ProvisionHints fromMap(Map<String, ?> map) {
            if (map == null || map.isEmpty()) {
                return new ProvisionHints(null, null, null, null, null, null, null, null, null, null);
            }
            Map<String, Object> m = new HashMap<>();
            map.forEach((k, v) -> m.put(k, v));
            // nested explicitIdentity / identity
            Object identity = m.get("explicitIdentity");
            if (identity == null) identity = m.get("identity");
            if (identity instanceof Map<?, ?> idMap) {
                idMap.forEach((k, v) -> {
                    if (k != null) m.putIfAbsent(k.toString(), v);
                });
            }
            Long exampleId = null;
            Object rawEx = m.get("sourceExampleId");
            if (rawEx instanceof Number n) exampleId = n.longValue();
            else if (rawEx != null && !rawEx.toString().isBlank()) {
                try { exampleId = Long.parseLong(rawEx.toString().trim()); } catch (NumberFormatException ignored) {}
            }
            return new ProvisionHints(
                    str(m.get("category")),
                    first(str(m.get("sourceRegister")), str(m.get("register")), str(m.get("voice_type"))),
                    first(str(m.get("age")), str(m.get("age_band"))),
                    str(m.get("gender")),
                    str(m.get("job")),
                    str(m.get("region")),
                    str(m.get("title")),
                    first(str(m.get("body")), str(m.get("searchDoc"))),
                    exampleId,
                    str(m.get("correlationId"))
            );
        }
    }

    public record ProvisionResult(
            boolean created,
            Optional<Persona> persona,
            String failureReason,
            Optional<PersonaDuplicateDetector.Identity> conflicting
    ) {
        public static ProvisionResult ok(Persona p) {
            return new ProvisionResult(true, Optional.of(p), null, Optional.empty());
        }

        public static ProvisionResult conflict(String reason, PersonaDuplicateDetector.Identity dup) {
            return new ProvisionResult(false, Optional.empty(), reason, Optional.ofNullable(dup));
        }

        public static ProvisionResult fail(String reason) {
            return new ProvisionResult(false, Optional.empty(), reason, Optional.empty());
        }
    }

    /**
     * Check soft-distance duplicates among active personas; if clear, create via
     * {@link PersonaFactory#createForStory} and rebuild capsules/facts.
     */
    public Optional<Persona> provisionIfNoDuplicate(ProvisionHints hints) {
        ProvisionResult result = provision(hints);
        return result.persona();
    }

    /** W4-A {@link StoryProfile} → provision hints (explicitIdentity age/gender/job). */
    public ProvisionResult provision(StoryProfile profile, Long sourceExampleId, String correlationId) {
        if (profile == null) {
            return provision(ProvisionHints.fromMap(Map.of()));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", profile.category());
        m.put("sourceRegister", profile.sourceRegister());
        m.put("title", profile.centralConflict());
        m.put("body", profile.toSearchDocument());
        if (profile.explicitIdentity() != null) {
            m.put("explicitIdentity", profile.explicitIdentity());
        }
        if (sourceExampleId != null) m.put("sourceExampleId", sourceExampleId);
        if (correlationId != null) m.put("correlationId", correlationId);
        return provision(ProvisionHints.fromMap(m));
    }

    public ProvisionResult provision(ProvisionHints hints) {
        ProvisionHints h = hints != null ? hints : ProvisionHints.fromMap(Map.of());
        String register = PersonaFactory.normalizeStoryVoice(h.sourceRegister());
        if (register == null) {
            register = "NATEPAN";
        }

        String age = blankToNull(h.age());
        String gender = blankToNull(h.gender());
        String job = blankToNull(h.job());

        // When identity is fully blank we still create (factory fills randomly) — skip dup gate.
        boolean identityPresent = age != null || gender != null || job != null;
        PersonaDuplicateDetector.Identity candidate =
                PersonaDuplicateDetector.Identity.of(age, gender, job, register);

        if (identityPresent && gender != null && age != null) {
            List<PersonaDuplicateDetector.Identity> existing = personaRepository.findByActiveTrue().stream()
                    .map(PersonaAutoProvisionService::identityOf)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            Optional<PersonaDuplicateDetector.Identity> dup =
                    PersonaDuplicateDetector.findNearDuplicate(existing, candidate, DEFAULT_SOFT_DISTANCE);
            if (dup.isPresent()) {
                String reason = "NEAR_DUPLICATE age=" + candidate.age()
                        + " gender=" + candidate.gender()
                        + " job=" + candidate.job()
                        + " voice=" + candidate.voiceType()
                        + " softDistance<=" + DEFAULT_SOFT_DISTANCE;
                log.info("PersonaAutoProvision: skip create — {}", reason);
                writeFailureAudit(h, reason, dup.get());
                return ProvisionResult.conflict(reason, dup.get());
            }
        }

        Map<String, String> factoryHints = new LinkedHashMap<>();
        if (age != null) factoryHints.put("age", age);
        if (gender != null) factoryHints.put("gender", gender);
        if (job != null) factoryHints.put("job", job);
        if (blankToNull(h.region()) != null) factoryHints.put("region", h.region().trim());

        Optional<Persona> created = personaFactory.createForStory(register, h.category(), factoryHints);
        if (created.isEmpty()) {
            String reason = "FACTORY_CREATE_FAILED";
            log.warn("PersonaAutoProvision: {}", reason);
            writeFailureAudit(h, reason, null);
            return ProvisionResult.fail(reason);
        }

        Persona persona = created.get();
        try {
            personaCapsuleService.rebuildPersona(persona);
        } catch (Exception e) {
            log.warn("PersonaAutoProvision: capsule rebuild skipped for {}: {}",
                    persona.getId(), e.getMessage());
        }

        writeSuccessAudit(h, persona);
        return ProvisionResult.ok(persona);
    }

    /** Reflective adapter for W4-A StoryProfile (or any bean with category / sourceRegister / getters). */
    public static ProvisionHints fromStoryLike(Object storyProfile) {
        if (storyProfile == null) return ProvisionHints.fromMap(Map.of());
        if (storyProfile instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, ?> cast = (Map<String, ?>) map;
            return ProvisionHints.fromMap(cast);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        putIfPresent(m, "category", invoke(storyProfile, "category"));
        putIfPresent(m, "sourceRegister", invoke(storyProfile, "sourceRegister"));
        putIfPresent(m, "register", invoke(storyProfile, "register"));
        Object identity = invoke(storyProfile, "explicitIdentity");
        if (identity != null) {
            putIfPresent(m, "age", invoke(identity, "age"));
            putIfPresent(m, "gender", invoke(identity, "gender"));
            putIfPresent(m, "job", invoke(identity, "job"));
            putIfPresent(m, "region", invoke(identity, "region"));
        }
        putIfPresent(m, "age", invoke(storyProfile, "age"));
        putIfPresent(m, "gender", invoke(storyProfile, "gender"));
        putIfPresent(m, "job", invoke(storyProfile, "job"));
        putIfPresent(m, "title", invoke(storyProfile, "title"));
        putIfPresent(m, "body", invoke(storyProfile, "body"));
        putIfPresent(m, "searchDoc", invoke(storyProfile, "searchDoc"));
        putIfPresent(m, "sourceExampleId", invoke(storyProfile, "sourceExampleId"));
        return ProvisionHints.fromMap(m);
    }

    static PersonaDuplicateDetector.Identity identityOf(Persona p) {
        if (p == null || p.getVoiceProfile() == null) return null;
        Map<String, Object> vp = p.getVoiceProfile();
        return PersonaDuplicateDetector.Identity.of(
                str(vp.get("age")),
                str(vp.get("gender")),
                str(vp.get("job")),
                str(vp.get("voice_type")));
    }

    private void writeFailureAudit(
            ProvisionHints h, String reason, PersonaDuplicateDetector.Identity dup) {
        try {
            Map<String, Object> reasons = new LinkedHashMap<>();
            reasons.put("failure", reason);
            reasons.put("category", h.category());
            reasons.put("register", h.sourceRegister());
            if (dup != null) {
                reasons.put("conflictAge", dup.age());
                reasons.put("conflictGender", dup.gender());
                reasons.put("conflictJob", dup.job());
                reasons.put("conflictVoice", dup.voiceType());
            }
            matchAuditRepository.save(PersonaMatchAudit.builder()
                    .correlationId(corr(h))
                    .sourceExampleId(h.sourceExampleId() != null ? h.sourceExampleId() : 0L)
                    .purpose(PURPOSE_AUTO_CREATE_FAILED)
                    .personaId(null)
                    .hardFilterPassed(false)
                    .semanticScore(null)
                    .finalScore(null)
                    .selected(false)
                    .reasons(reasons)
                    .build());
        } catch (Exception e) {
            log.debug("persona_match_audits auto-create failure write skipped: {}", e.getMessage());
        }
    }

    private void writeSuccessAudit(ProvisionHints h, Persona persona) {
        try {
            Map<String, Object> reasons = new LinkedHashMap<>();
            reasons.put("status", "CREATED");
            reasons.put("category", h.category());
            reasons.put("register",
                    persona.getVoiceProfile() != null
                            ? persona.getVoiceProfile().get("voice_type") : h.sourceRegister());
            matchAuditRepository.save(PersonaMatchAudit.builder()
                    .correlationId(corr(h))
                    .sourceExampleId(h.sourceExampleId() != null ? h.sourceExampleId() : 0L)
                    .purpose(PURPOSE_AUTO_CREATED)
                    .personaId(persona.getId())
                    .hardFilterPassed(true)
                    .semanticScore(null)
                    .finalScore(BigDecimal.ONE)
                    .selected(true)
                    .reasons(reasons)
                    .build());
        } catch (Exception e) {
            log.debug("persona_match_audits auto-create success write skipped: {}", e.getMessage());
        }
    }

    private static String corr(ProvisionHints h) {
        if (h.correlationId() != null && !h.correlationId().isBlank()) return h.correlationId().trim();
        return "auto-persona-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String first(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> m, String key, Object val) {
        if (val != null) m.putIfAbsent(key, val);
    }

    private static Object invoke(Object target, String method) {
        if (target == null || method == null) return null;
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            try {
                String getter = "get" + method.substring(0, 1).toUpperCase(Locale.ROOT) + method.substring(1);
                return target.getClass().getMethod(getter).invoke(target);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    }
}
