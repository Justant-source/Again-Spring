package com.againspring.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for progressing a turn in mediation.
 * POST /api/sessions/{sessionId}/turns
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressTurnRequest {

    /**
     * User input for this turn (required).
     */
    private String userInput;

    /**
     * Whether user is skipping this turn (optional, default false).
     * Used for perspective-taking turns 5-6.
     */
    @Builder.Default
    private boolean skip = false;
}
