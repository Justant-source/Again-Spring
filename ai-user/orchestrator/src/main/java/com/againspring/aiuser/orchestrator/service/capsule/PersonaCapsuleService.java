package com.againspring.aiuser.orchestrator.service.capsule;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaFactAssertion;
import com.againspring.aiuser.orchestrator.domain.PersonaSemanticCapsule;
import com.againspring.aiuser.orchestrator.repository.PersonaFactAssertionRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaSemanticCapsuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds / upserts ≤3 semantic capsules + slim fact assertions for a persona.
 * Embedding via {@link AiLearningClient#embedOptional}; JDBC {@code VEC_FromText} for VECTOR column.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonaCapsuleService {

    private final PersonaRepository personaRepository;
    private final PersonaSemanticCapsuleRepository capsuleRepository;
    private final PersonaFactAssertionRepository factRepository;
    private final AiLearningClient learningClient;
    private final JdbcTemplate jdbcTemplate;

    public record PersonaBuildResult(
            String personaId,
            int capsulesUpserted,
            int capsulesSkippedUnchanged,
            int capsulesSkippedNoEmbed,
            int factsUpserted,
            int deactivated
    ) {}

    public record BackfillResult(
            int personasProcessed,
            int capsulesUpserted,
            int capsulesSkipped,
            int factsUpserted,
            int errors
    ) {}

    /** Rebuild capsules + facts for one persona. Embed failure → skip that capsule (degrade). */
    @Transactional
    public PersonaBuildResult rebuildPersona(Persona persona) {
        List<PersonaCapsuleTextBuilder.CapsuleDraft> drafts =
                PersonaCapsuleTextBuilder.buildCapsules(persona);
        int upserted = 0;
        int skippedHash = 0;
        int skippedEmbed = 0;
        List<Long> keepIds = new ArrayList<>();

        // Facts first — independent of capsule deactivate path (self-invoke may skip class @Transactional).
        int facts = upsertFacts(persona);

        for (PersonaCapsuleTextBuilder.CapsuleDraft draft : drafts) {
            Optional<PersonaSemanticCapsule> existingOpt = capsuleRepository
                    .findByPersonaIdAndCapsuleTypeAndTopicKey(
                            persona.getId(), draft.capsuleType(), draft.topicKey());

            if (existingOpt.isPresent()) {
                PersonaSemanticCapsule existing = existingOpt.get();
                // embedding is NOT NULL in schema — hash match ⇒ skip re-embed
                if (draft.contentHash().equals(existing.getContentHash())) {
                    if (!existing.isActive()) {
                        existing.setActive(true);
                        capsuleRepository.save(existing);
                    }
                    keepIds.add(existing.getId());
                    skippedHash++;
                    continue;
                }
            }

            Optional<List<Double>> emb = learningClient.embedOptional(draft.text());
            if (emb.isEmpty()) {
                log.debug("capsule embed skipped persona={} type={} key={}",
                        persona.getId(), draft.capsuleType(), draft.topicKey());
                existingOpt.ifPresent(e -> {
                    if (e.isActive()) keepIds.add(e.getId());
                });
                skippedEmbed++;
                continue;
            }

            Long id = upsertCapsule(persona.getId(), draft, emb.get());
            if (id != null) {
                keepIds.add(id);
                upserted++;
            }
        }

        int deactivated = 0;
        if (!keepIds.isEmpty()) {
            deactivated = capsuleRepository.deactivateExcept(persona.getId(), keepIds);
        }

        return new PersonaBuildResult(persona.getId(), upserted, skippedHash, skippedEmbed, facts, deactivated);
    }

    /** Process all active personas (caller may batch / async). */
    public BackfillResult backfillAllActive(int batchSize) {
        List<Persona> active = personaRepository.findByActiveTrue();
        int processed = 0;
        int upserted = 0;
        int skipped = 0;
        int facts = 0;
        int errors = 0;
        int size = Math.max(1, batchSize);

        for (int i = 0; i < active.size(); i += size) {
            List<Persona> batch = active.subList(i, Math.min(i + size, active.size()));
            for (Persona p : batch) {
                try {
                    PersonaBuildResult r = rebuildPersona(p);
                    processed++;
                    upserted += r.capsulesUpserted();
                    skipped += r.capsulesSkippedUnchanged() + r.capsulesSkippedNoEmbed();
                    facts += r.factsUpserted();
                } catch (Exception e) {
                    errors++;
                    log.warn("[capsule-backfill] persona={} error={}", p.getId(), e.getMessage());
                }
            }
            log.info("[capsule-backfill] progress {}/{}", Math.min(i + size, active.size()), active.size());
        }
        return new BackfillResult(processed, upserted, skipped, facts, errors);
    }

    private int upsertFacts(Persona persona) {
        List<PersonaCapsuleTextBuilder.FactDraft> drafts = PersonaCapsuleTextBuilder.buildFacts(persona);
        int n = 0;
        for (PersonaCapsuleTextBuilder.FactDraft d : drafts) {
            PersonaFactAssertion row = factRepository
                    .findByPersonaIdAndFactKey(persona.getId(), d.factKey())
                    .orElseGet(() -> PersonaFactAssertion.builder()
                            .personaId(persona.getId())
                            .factKey(d.factKey())
                            .build());
            boolean changed = !d.factValue().equals(row.getFactValue())
                    || row.getId() == null
                    || !d.origin().equals(row.getOrigin());
            if (!changed) continue;
            row.setFactValue(d.factValue());
            row.setOrigin(d.origin());
            row.setConfidence(d.confidence());
            row.setEvidenceRef(d.evidenceRef());
            row.setSchemaVersion((short) 1);
            factRepository.save(row);
            n++;
        }
        return n;
    }

    /**
     * INSERT … ON DUPLICATE KEY UPDATE with {@code VEC_FromText}.
     * Returns row id.
     */
    private Long upsertCapsule(String personaId, PersonaCapsuleTextBuilder.CapsuleDraft draft, List<Double> embedding) {
        String vecLiteral = toVecLiteral(embedding);
        jdbcTemplate.update("""
                INSERT INTO persona_semantic_capsules
                  (persona_id, capsule_type, topic_key, text_value, embedding, weight,
                   origin, confidence, evidence_ref, content_hash, schema_version, active,
                   created_at, updated_at)
                VALUES (?, ?, ?, ?, VEC_FromText(?), 1.000, ?, ?, ?, ?, 1, 1, NOW(3), NOW(3))
                ON DUPLICATE KEY UPDATE
                  text_value = VALUES(text_value),
                  embedding = VALUES(embedding),
                  origin = VALUES(origin),
                  confidence = VALUES(confidence),
                  evidence_ref = VALUES(evidence_ref),
                  content_hash = VALUES(content_hash),
                  active = 1,
                  updated_at = NOW(3)
                """,
                personaId,
                draft.capsuleType(),
                draft.topicKey(),
                draft.text(),
                vecLiteral,
                draft.origin(),
                draft.confidence(),
                draft.evidenceRef(),
                draft.contentHash());

        return capsuleRepository
                .findByPersonaIdAndCapsuleTypeAndTopicKey(personaId, draft.capsuleType(), draft.topicKey())
                .map(PersonaSemanticCapsule::getId)
                .orElse(null);
    }

    /** MariaDB VEC_FromText accepts "[f1,f2,...]". */
    static String toVecLiteral(List<Double> embedding) {
        return embedding.stream()
                .map(d -> String.format(java.util.Locale.ROOT, "%.8f", d))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
