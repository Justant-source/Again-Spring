package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;

import java.util.Optional;

/**
 * Outcome of one {@code generateAndHold} attempt. Empty claim must not invoke LLM;
 * LLM/safety/serialize/persist happen only after a successful claim.
 */
public record HoldResult(
        Outcome outcome,
        Optional<AiScheduledPost> saved,
        String source,
        String plaza,
        String personaId,
        Long exampleId,
        String detail,
        boolean llmInvoked
) {
    public enum Outcome {
        SAVED,
        CLAIM_EMPTY,
        LLM_OR_SAFETY,
        SERIALIZE,
        PERSIST,
        GENERATION_SKIPPED,
        SAME_EXAMPLE
    }

    public static HoldResult saved(AiScheduledPost row, String source, String plaza,
                                   String personaId, Long exampleId) {
        return new HoldResult(Outcome.SAVED, Optional.ofNullable(row), source, plaza, personaId,
                exampleId, "saved", true);
    }

    public static HoldResult claimEmpty(String source, String plaza, String personaId, String detail) {
        return new HoldResult(Outcome.CLAIM_EMPTY, Optional.empty(), source, plaza, personaId,
                null, detail == null ? "no claimed source" : detail, false);
    }

    public static HoldResult llmOrSafety(String source, String plaza, String personaId,
                                         Long exampleId, String detail) {
        return new HoldResult(Outcome.LLM_OR_SAFETY, Optional.empty(), source, plaza, personaId,
                exampleId, detail == null ? "LLM or safety rejected" : detail, true);
    }

    public static HoldResult serialize(String source, String plaza, String personaId,
                                       Long exampleId, String detail) {
        return new HoldResult(Outcome.SERIALIZE, Optional.empty(), source, plaza, personaId,
                exampleId, detail, true);
    }

    public static HoldResult persist(String source, String plaza, String personaId,
                                     Long exampleId, String detail) {
        return new HoldResult(Outcome.PERSIST, Optional.empty(), source, plaza, personaId,
                exampleId, detail, true);
    }

    public static HoldResult generationSkipped(String source, String plaza, String personaId,
                                               Long exampleId, String detail) {
        return new HoldResult(Outcome.GENERATION_SKIPPED, Optional.empty(), source, plaza, personaId,
                exampleId, detail, false);
    }

    public static HoldResult sameExample(String source, String plaza, String personaId,
                                         Long exampleId, String detail) {
        return new HoldResult(Outcome.SAME_EXAMPLE, Optional.empty(), source, plaza, personaId,
                exampleId, detail, false);
    }

    public String detailedReason() {
        return String.format(
                "outcome=%s source=%s plaza=%s persona=%s exampleId=%s llmInvoked=%s %s",
                outcome,
                nullToDash(source),
                nullToDash(plaza),
                nullToDash(personaId),
                exampleId == null ? "-" : exampleId,
                llmInvoked,
                detail == null || detail.isBlank() ? "" : detail.trim());
    }

    private static String nullToDash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }
}
