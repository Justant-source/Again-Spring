package com.againspring.api.dto.response;

import com.againspring.domain.enums.TurnRole;
import com.againspring.safety.CrisisResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for a completed turn.
 * POST /api/sessions/{sessionId}/turns returns this.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnResponse {

    private String sessionId;

    private int turnNumber;

    private TurnRole role;

    /**
     * Message from mediator to the current user (the submitter).
     */
    private String mediatorMessage;

    /**
     * Neutral summary prepared for the opponent to view.
     */
    private String neutralSummaryForOpponent;

    /**
     * Follow-up questions generated for next turn (turns 3-4), if any.
     */
    private List<String> questions;

    /**
     * Next turn info: which role goes next.
     */
    private Integer nextTurnNumber;

    private TurnRole nextRole;

    /**
     * Whether session is complete after this turn.
     */
    private boolean isComplete;

    /**
     * Whether this turn response was generated via fallback.
     */
    private boolean isFallback;

    /**
     * Crisis response if detected (null otherwise).
     */
    private CrisisResponse crisis;

    private Instant createdAt;
}
