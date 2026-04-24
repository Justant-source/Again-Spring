package com.againspring.api.dto.response;

import com.againspring.domain.enums.TurnRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for current turn state.
 * GET /api/sessions/{sessionId}/turns/current returns this.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentTurnResponse {

    private int currentTurn;

    private TurnRole currentRole;

    /**
     * What role is the requesting user?
     */
    private TurnRole myRole;

    /**
     * Is it the current user's turn?
     */
    private boolean isMyTurn;

    /**
     * Questions for the current turn (if applicable).
     */
    private String mediatorQuestion;

    /**
     * Transcript so far: list of turn summaries visible to current user.
     */
    private List<TranscriptItem> transcript;

    /**
     * Embedded DTO for a single turn in transcript.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranscriptItem {
        private int turnNumber;
        private TurnRole role;
        private String userInput;
        private String mediatorMessage;
        private Instant createdAt;
    }
}
