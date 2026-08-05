package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Soft-reserve helpers for popular-source claim lifecycle:
 * hold → SOFT ({@code reservationKey}=scheduled post id) → publish commit / cancel·fail release.
 *
 * <p>Reservation refs live under {@link AiPostBundleService#SOURCE_PROVENANCE_KEY} in
 * {@code candidates_json} ({@code sourceExampleId} + {@code reservationKey}).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SourceReservationSupport {
    public static final String RESERVATION_KEY = "reservationKey";

    private final AiLearningClient aiLearningClient;
    private final ObjectMapper objectMapper;

    public record ReservationRefs(Long sourceExampleId, String reservationKey) {
        public boolean isPresent() {
            return sourceExampleId != null && reservationKey != null && !reservationKey.isBlank();
        }
    }

    /** Prefer explicit param; else persona voice_type (BLIND→blind, else natepan). */
    public static String resolvePreferredSource(String preferredSource, Persona author) {
        if (preferredSource != null && !preferredSource.isBlank()) {
            String normalized = PlanSourceStoryResolver.normalizePreferredSource(preferredSource);
            if (normalized != null) return normalized;
        }
        return PlanSourceStoryResolver.preferredSourceFromVoice(author);
    }

    public Map<String, Object> provenanceWithReservation(
            PlanSourceStoryResolver.ResolvedSource source, String reservationKey) {
        Map<String, Object> m = source == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source.provenanceForTrace());
        if (reservationKey != null && !reservationKey.isBlank()) {
            m.put(RESERVATION_KEY, reservationKey);
        }
        return m;
    }

    public Optional<ReservationRefs> readFromCandidatesJson(String candidatesJson) {
        if (candidatesJson == null || candidatesJson.isBlank()) return Optional.empty();
        try {
            Map<String, Object> response = objectMapper.readValue(candidatesJson, new TypeReference<>() { });
            Object raw = response.get(AiPostBundleService.SOURCE_PROVENANCE_KEY);
            if (!(raw instanceof Map<?, ?> prov)) return Optional.empty();
            Long exampleId = asLong(prov.get("sourceExampleId"));
            String key = asText(prov.get(RESERVATION_KEY));
            if (exampleId == null || key == null || key.isBlank()) return Optional.empty();
            return Optional.of(new ReservationRefs(exampleId, key));
        } catch (Exception e) {
            log.debug("Could not read source reservation from candidates: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void commit(Long exampleId, String reservationKey) {
        if (exampleId == null || reservationKey == null || reservationKey.isBlank()) return;
        try {
            if (!aiLearningClient.commitSource(exampleId, reservationKey)) {
                log.debug("commitSource returned false exampleId={} key={}", exampleId, reservationKey);
            }
        } catch (Exception e) {
            log.warn("commitSource failed exampleId={}: {}", exampleId, e.getMessage());
        }
    }

    public void release(Long exampleId, String reservationKey) {
        if (exampleId == null || reservationKey == null || reservationKey.isBlank()) return;
        try {
            if (!aiLearningClient.releaseSource(exampleId, reservationKey)) {
                log.debug("releaseSource returned false exampleId={} key={}", exampleId, reservationKey);
            }
        } catch (Exception e) {
            log.warn("releaseSource failed exampleId={}: {}", exampleId, e.getMessage());
        }
    }

    public void commit(ReservationRefs refs) {
        if (refs != null && refs.isPresent()) commit(refs.sourceExampleId(), refs.reservationKey());
    }

    public void release(ReservationRefs refs) {
        if (refs != null && refs.isPresent()) release(refs.sourceExampleId(), refs.reservationKey());
    }

    public void commitFromCandidatesJson(String candidatesJson) {
        readFromCandidatesJson(candidatesJson).ifPresent(this::commit);
    }

    public void releaseFromCandidatesJson(String candidatesJson) {
        readFromCandidatesJson(candidatesJson).ifPresent(this::release);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String asText(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isBlank() ? null : s;
    }
}
