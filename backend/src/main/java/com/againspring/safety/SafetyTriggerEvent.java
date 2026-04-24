package com.againspring.safety;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEvent;

/**
 * Application event published when a Level 2 warning keyword is detected.
 *
 * Used by SafetyAuditLogger to create an audit trail of safety warnings.
 * Less severe than CrisisDetectedEvent but still logged for compliance.
 */
public class SafetyTriggerEvent extends ApplicationEvent {

	private final String userId;
	private final String sessionId;
	private final Level level;
	private final List<String> matchedPatterns;
	private final LocalDateTime triggeredAt;

	public SafetyTriggerEvent(
		Object source,
		String userId,
		String sessionId,
		Level level,
		List<String> matchedPatterns
	) {
		super(source);
		this.userId = userId;
		this.sessionId = sessionId;
		this.level = level;
		this.matchedPatterns = matchedPatterns;
		this.triggeredAt = LocalDateTime.now();
	}

	public String getUserId() {
		return userId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public Level getLevel() {
		return level;
	}

	public List<String> getMatchedPatterns() {
		return matchedPatterns;
	}

	public LocalDateTime getTriggeredAt() {
		return triggeredAt;
	}
}
