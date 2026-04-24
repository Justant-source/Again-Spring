package com.againspring.safety;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * Result of a keyword scan operation.
 *
 * Immutable DTO carrying:
 * - maxLevel: highest severity detected
 * - matches: list of matched patterns with position and category info
 * - blocked: true if any LEVEL1 or CRISIS detected (session must terminate)
 * - crisis: true if any CRISIS-level keyword detected (show crisis resources)
 */
@Value
@Builder
public class ScanResult {
	private final Level maxLevel;
	private final List<Match> matches;
	private final boolean blocked;
	private final boolean crisis;

	/**
	 * Immutable match result for a single forbidden word detection.
	 */
	@Value
	public static class Match {
		private final String pattern;
		private final Level level;
		private final String category;
		private final boolean crisis;
		private final int position;
	}

	/**
	 * Factory method for empty result (no matches).
	 */
	public static ScanResult empty() {
		return ScanResult.builder()
			.maxLevel(null)
			.matches(Collections.emptyList())
			.blocked(false)
			.crisis(false)
			.build();
	}

	/**
	 * Factory method for crisis result.
	 */
	public static ScanResult crisisResult(List<Match> matches) {
		return ScanResult.builder()
			.maxLevel(Level.CRISIS)
			.matches(matches == null ? Collections.emptyList() : Collections.unmodifiableList(matches))
			.blocked(true)
			.crisis(true)
			.build();
	}

	/**
	 * Factory method for level1 blocking result.
	 */
	public static ScanResult blockedResult(Level level, List<Match> matches) {
		return ScanResult.builder()
			.maxLevel(level)
			.matches(matches == null ? Collections.emptyList() : Collections.unmodifiableList(matches))
			.blocked(level.requiresTermination())
			.crisis(false)
			.build();
	}

	/**
	 * Factory method for warning result (continues but alerts).
	 */
	public static ScanResult warningResult(Level level, List<Match> matches) {
		return ScanResult.builder()
			.maxLevel(level)
			.matches(matches == null ? Collections.emptyList() : Collections.unmodifiableList(matches))
			.blocked(false)
			.crisis(false)
			.build();
	}

	/**
	 * Returns matches as unmodifiable list.
	 */
	public List<Match> getMatches() {
		return matches == null ? Collections.emptyList() : matches;
	}
}
