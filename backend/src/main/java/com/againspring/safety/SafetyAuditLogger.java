package com.againspring.safety;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Listens for safety-related application events and logs them to audit trail.
 *
 * Uses a dedicated logger at WARN level to create a structured audit log.
 * CRITICAL: Never logs the raw matched text — only the pattern ID and level.
 *
 * Log format follows LLMCallLogger style: key=value pairs for easy parsing.
 * Example:
 *   userId=abc123 sessionId=xyz789 level=CRISIS patterns=[강간,성폭력] timestamp=2026-04-24T10:30:00
 *   userId=abc123 sessionId=xyz789 level=LEVEL2 patterns=[이혼] timestamp=2026-04-24T10:30:01
 */
@Component
@Slf4j
public class SafetyAuditLogger {

	private static final org.slf4j.Logger auditLogger = org.slf4j.LoggerFactory.getLogger("com.againspring.safety.audit");

	/**
	 * Handles crisis detection events.
	 *
	 * Logs the crisis detection with pattern names (not raw text).
	 */
	@EventListener
	public void onCrisisDetected(CrisisDetectedEvent event) {
		auditLogger.warn(
			"userId={} sessionId={} level=CRISIS patterns={} timestamp={}",
			event.getUserId(),
			event.getSessionId(),
			event.getMatchedPatterns(),
			event.getDetectedAt()
		);
	}

	/**
	 * Handles Level 2 warning events.
	 *
	 * Logs the warning with pattern names for compliance tracking.
	 */
	@EventListener
	public void onSafetyTrigger(SafetyTriggerEvent event) {
		auditLogger.warn(
			"userId={} sessionId={} level={} patterns={} timestamp={}",
			event.getUserId(),
			event.getSessionId(),
			event.getLevel().name(),
			event.getMatchedPatterns(),
			event.getTriggeredAt()
		);
	}
}
