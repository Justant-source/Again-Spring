package com.againspring.service.event;

import com.againspring.domain.enums.TurnRole;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Event published when a turn is completed.
 * Consumed by ReportService to track session progress.
 */
@Getter
public class TurnCompletedEvent extends ApplicationEvent {

    private final String sessionId;

    private final int turnNumber;

    private final TurnRole role;

    private final Instant completedAt;

    public TurnCompletedEvent(Object source, String sessionId, int turnNumber, TurnRole role, Instant completedAt) {
        super(source);
        this.sessionId = sessionId;
        this.turnNumber = turnNumber;
        this.role = role;
        this.completedAt = completedAt;
    }
}
