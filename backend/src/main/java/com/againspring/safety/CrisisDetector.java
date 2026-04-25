package com.againspring.safety;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Detects crisis situations from user input using KeywordGuard.
 *
 * When a crisis keyword is detected (domestic violence, self-harm, sexual violence, child abuse):
 * 1. Returns a CrisisResponse with hotline information and crisis resources
 * 2. Publishes a CrisisDetectedEvent for audit logging
 * 3. Recommends session termination
 *
 * Non-crisis warnings do not trigger this component; those are handled by SafetyAuditLogger.
 *
 * TODO Phase 7: Invoke at session start or on every turn submission.
 * TODO Phase 8: Verify all crisis keywords are covered in KeywordGuard configuration.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CrisisDetector {

	private final KeywordGuard keywordGuard;
	private final ApplicationEventPublisher eventPublisher;

	public enum CrisisLevel {
		NONE,
		LEGAL_INQUIRY,   // "이혼 절차", "이혼 변호사" — 법률 상담 안내
		IMMEDIATE_CRISIS // 폭력, 자해, 성폭력 — 즉각 위기 핫라인
	}

	/**
	 * Detects crisis keywords in user input.
	 *
	 * @param userInput The text to scan
	 * @param sessionId Session identifier for audit trail
	 * @param userId User identifier for audit trail
	 * @return CrisisResponse if crisis detected, or null if no crisis
	 */
	public CrisisResponse detect(String userInput, String sessionId, String userId) {
		ScanResult result = keywordGuard.scanUserInput(userInput, userId);

		if (!result.isCrisis()) {
			return null;
		}

		// Extract matched pattern names for audit log (not raw text)
		List<String> patterns = result.getMatches().stream()
			.map(ScanResult.Match::getPattern)
			.distinct()
			.collect(Collectors.toList());

		// Publish audit event
		eventPublisher.publishEvent(new CrisisDetectedEvent(
			this,
			userId,
			sessionId,
			patterns
		));

		log.warn("Crisis detected for user={}, session={}, patterns={}", userId, sessionId, patterns);

		// Return crisis response with hotline information
		return CrisisResponse.createStandardCrisisResponse();
	}

	/**
	 * Detects crisis level from user input.
	 *
	 * @param userInput The text to scan
	 * @return CrisisLevel indicating severity (NONE, LEGAL_INQUIRY, IMMEDIATE_CRISIS)
	 */
	public CrisisLevel detectLevel(String userInput) {
		ScanResult result = keywordGuard.scanUserInput(userInput, "system");

		if (result.isCrisis()) {
			return CrisisLevel.IMMEDIATE_CRISIS;
		}

		// Legal inquiry keywords
		if (userInput != null) {
			String lower = userInput.toLowerCase();
			if (lower.contains("이혼 절차") || lower.contains("이혼 변호사") ||
				lower.contains("이혼 신청") || lower.contains("이혼 서류")) {
				return CrisisLevel.LEGAL_INQUIRY;
			}
		}

		return CrisisLevel.NONE;
	}
}
