package com.againspring.safety;

/**
 * Severity levels for forbidden word detection.
 *
 * - CRISIS: Immediate session termination required (domestic violence, self-harm, sexual violence, child abuse)
 * - LEVEL1: Immediate session termination (legal risk, stigmatizing terms)
 * - LEVEL2: Warning but continue (clinical terms, relationship termination encouragement)
 * - LEVEL3: Log only, no user action (judgment language for audit trail)
 * - LEVEL4: Relationship termination encouragement (blocked in output)
 */
public enum Level {
	CRISIS(0, "즉시 중단"),
	LEVEL1(1, "레벨 1 - 즉시 중단"),
	LEVEL2(2, "레벨 2 - 경고"),
	LEVEL3(3, "레벨 3 - 알림"),
	LEVEL4(4, "레벨 4 - 관계 파국 조장");

	private final int priority;
	private final String description;

	Level(int priority, String description) {
		this.priority = priority;
		this.description = description;
	}

	public int getPriority() {
		return priority;
	}

	public String getDescription() {
		return description;
	}

	/**
	 * Returns the highest severity level from two levels.
	 */
	public static Level max(Level a, Level b) {
		return a.priority < b.priority ? a : b;
	}

	/**
	 * Check if this level requires session termination.
	 */
	public boolean requiresTermination() {
		return this == CRISIS || this == LEVEL1;
	}

	/**
	 * Check if this level indicates a crisis situation.
	 */
	public boolean isCrisis() {
		return this == CRISIS;
	}
}
