package com.againspring.service.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published after a partner joins a session.
 * Handled by ChatService.onPartnerJoinedEvent() AFTER_COMMIT so that
 * the join transaction is fully committed before LLM calls begin.
 * This prevents SessionRoleResolver from throwing "User not part of this session"
 * when B's first message arrives before the join transaction commits.
 */
@Getter
public class PartnerJoinedEvent extends ApplicationEvent {

    private final String sessionId;
    private final String userBId;

    public PartnerJoinedEvent(Object source, String sessionId, String userBId) {
        super(source);
        this.sessionId = sessionId;
        this.userBId = userBId;
    }
}
