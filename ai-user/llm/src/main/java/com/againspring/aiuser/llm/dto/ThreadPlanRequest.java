package com.againspring.aiuser.llm.dto;

import lombok.Data;
import java.util.List;

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
    private List<Persona> personas;
    private Integer maxTopLevel = 14;
    private Integer maxReplies = 10;

    public enum Kind { AI_POST, HUMAN_POST }

    @Data
    public static class Persona {
        private String personaId;
        private String nickname;
        private String voiceProfile;
        private String formality;
    }
}
