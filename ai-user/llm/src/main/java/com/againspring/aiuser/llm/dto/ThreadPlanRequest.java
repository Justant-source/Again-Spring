package com.againspring.aiuser.llm.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/** One-shot request for a post (when applicable) and its complete comment candidate tree. */
@Data
public class ThreadPlanRequest {
    /** AI_POST uses the stronger model; HUMAN_POST plans reactions to an existing human post. */
    private Kind kind;
    private String provider; // CLAUDE | CODEX; direct API is intentionally not accepted.
    private String model;
    private String correlationId;
    private Long timeoutMs;
    private String postId;
    private Long postRevision;
    private String existingTitle;
    private String existingBody;
    private String category;
    private String topicHint;
    /**
     * Structured source-story grounding from orchestrator (example_bank hit).
     * Keys typically include title/body/source/register/reconstructMode — opaque Map so
     * schema can evolve without LLM DTO churn.
     */
    private Map<String, Object> sourceContext;
    /** True when a single crawl original (source_url) should be re-narrated, not freestyled. */
    private Boolean reconstructMode;
    /** example_bank.id of the primary source when reconstructMode is true. */
    private Long sourceExampleId;
    /** Full primary source body for reconstruct mode (orchestrator may also put a truncated copy in sourceContext). */
    private String sourceBody;
    /** Style-only few-shot anchors (not the reconstruct primary). */
    private String dynamicExamples;
    /** Recent post bodies for anti-self-copy (list of short strings). */
    private List<String> recentOutputs;
    /** Metaphor ids used too often recently (orchestrator-computed) — LLM should avoid repeating these. */
    private List<String> overusedMetaphorIds;
    /**
     * Explicit AI_POST author profile. Prefer this over assuming {@code personas[0]}.
     * Same shape as {@link Persona} plus optional slangLevel/interests.
     */
    private Map<String, Object> author;
    private List<Persona> personas;
    private Integer maxTopLevel = 14;
    private Integer maxReplies = 10;
    /**
     * Minimum top-level comment candidates accepted by {@code parsePlan}.
     * Null → legacy floor {@code min(6, maxTopLevel)}.
     * Explicit value (including 1) is honored, clamped to {@code 1..maxTopLevel}.
     * Orchestrators that defer quality to a later gate should send {@code 1}.
     */
    private Integer minTopLevel;
    /**
     * Minimum total comment candidates accepted by {@code parsePlan}.
     * Null → legacy floor {@code min(12, maxTopLevel+maxReplies)}.
     * Explicit value (including 1) is honored, clamped to {@code 1..max}.
     * Orchestrators that defer quality to a later gate should send {@code 1}.
     */
    private Integer minItems;

    public enum Kind { AI_POST, HUMAN_POST }

    /**
     * Participant (and for AI_POST, author should be first or matched via {@code personaId}).
     * {@code voiceProfile} is a structured object (not {@code Map.toString()}); Jackson accepts
     * a JSON object so orchestrator can send {@code Map<String,Object>} as-is.
     */
    @Data
    public static class Persona {
        private String personaId;
        /** Real display nickname from users.nickname — never the persona id. */
        private String nickname;
        /**
         * Structured voice_profile fields (formality, voice_type, age, gender, styles, …).
         * Prefer top-level {@link #formality} for register; keep formality inside the map too
         * when present so prompt JSON is not lossy.
         */
        private Map<String, Object> voiceProfile;
        /** casual | polite | formal — from voice_profile.$.formality, not hard-coded "neutral". */
        private String formality;
    }
}
