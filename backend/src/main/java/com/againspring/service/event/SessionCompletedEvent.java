package com.againspring.service.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Event published when a session transitions to COMPLETED status.
 * Triggers auto-generation of report via ReportService.
 */
@Getter
public class SessionCompletedEvent extends ApplicationEvent {

    private final String sessionId;

    private final Instant completedAt;

    public SessionCompletedEvent(Object source, String sessionId, Instant completedAt) {
        super(source);
        this.sessionId = sessionId;
        this.completedAt = completedAt;
    }
}
