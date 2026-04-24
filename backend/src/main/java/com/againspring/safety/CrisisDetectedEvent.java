package com.againspring.safety;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEvent;

/**
 * Application event published when a crisis keyword is detected in user input.
 *
 * Used by SafetyAuditLogger to create an audit trail of crisis detections.
 * This event carries the detection metadata but NOT the raw matched text.
 */
public class CrisisDetectedEvent extends ApplicationEvent {

	private final String userId;
	private final String sessionId;
	private final List<String> matchedPatterns;
	private final LocalDateTime detectedAt;

	public CrisisDetectedEvent(
		Object source,
		String userId,
		String sessionId,
		List<String> matchedPatterns
	) {
		super(source);
		this.userId = userId;
		this.sessionId = sessionId;
		this.matchedPatterns = matchedPatterns;
		this.detectedAt = LocalDateTime.now();
	}

	public String getUserId() {
		return userId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public List<String> getMatchedPatterns() {
		return matchedPatterns;
	}

	public LocalDateTime getDetectedAt() {
		return detectedAt;
	}
}
