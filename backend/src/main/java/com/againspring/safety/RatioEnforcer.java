package com.againspring.safety;

import com.againspring.domain.enums.ConflictType;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Enforces ratio clipping rules for reconciliation contribution calculations.
 *
 * See RATIO_CALCULATION.md for algorithm details.
 *
 * Rules by conflict type:
 * - FACTUAL: max 100:0 (no clipping needed, but round to 5 units)
 * - DIFFERENCE: max 70:30 (clip if either side > 70)
 * - MIXED: max 85:15 (clip if either side > 85)
 *
 * After clipping, rounds to nearest 5-unit boundary and enforces sum=100.
 *
 * Stateless component suitable for testing. Pure function behavior.
 *
 * TODO Phase 8: Called by ReportService after LLM ratio calculation.
 */
@Component
@Slf4j
public class RatioEnforcer {

	/**
	 * Clips and normalizes a contribution ratio based on conflict type.
	 *
	 * @param type Conflict type (factual, difference, mixed)
	 * @param aPct A's initial percentage (0-100)
	 * @param bPct B's initial percentage (0-100)
	 * @param rationale Explanation for use in logging (optional)
	 * @return EnforcedRatio with clipped values, rounding applied, and clip flags
	 * @throws IllegalArgumentException if input is invalid (negative, > 100, or sum wildly off)
	 */
	public EnforcedRatio clip(
		ConflictType type,
		int aPct,
		int bPct,
		String rationale
	) {
		// Validate input
		if (aPct < 0 || bPct < 0 || aPct > 100 || bPct > 100) {
			throw new IllegalArgumentException(
				String.format("Invalid percentages: a=%d, b=%d (must be 0-100)", aPct, bPct)
			);
		}

		// Allow some tolerance in sum validation (within ±5 before rounding)
		int sum = aPct + bPct;
		if (sum < 95 || sum > 105) {
			throw new IllegalArgumentException(
				String.format(
					"Invalid sum: a=%d + b=%d = %d (must be approximately 100)",
					aPct, bPct, sum
				)
			);
		}

		Integer originalA = aPct;
		Integer originalB = bPct;
		boolean wasClipped = false;

		// Apply type-specific clipping
		int clippedA = aPct;
		int clippedB = bPct;

		switch (type) {
			case DIFFERENCE:
				// Max 70:30 for difference-based conflicts
				if (clippedA > 70) {
					clippedA = 70;
					clippedB = 30;
					wasClipped = true;
				} else if (clippedB > 70) {
					clippedA = 30;
					clippedB = 70;
					wasClipped = true;
				}
				break;

			case MIXED:
				// Max 85:15 for mixed conflicts
				if (clippedA > 85) {
					clippedA = 85;
					clippedB = 15;
					wasClipped = true;
				} else if (clippedB > 85) {
					clippedA = 15;
					clippedB = 85;
					wasClipped = true;
				}
				break;

			case FACTUAL:
				// No clipping for factual conflicts (100:0 is allowed)
				break;

			default:
				throw new IllegalArgumentException("Unknown conflict type: " + type);
		}

		// Round to nearest 5 units
		int roundedA = roundToFive(clippedA);
		int roundedB = 100 - roundedA;

		if (roundedA != clippedA || roundedB != clippedB) {
			wasClipped = true;
		}

		if (log.isDebugEnabled()) {
			log.debug(
				"Ratio enforced: type={} input={}:{} clipped={}:{} rounded={}:{} wasClipped={}",
				type,
				aPct, bPct,
				clippedA, clippedB,
				roundedA, roundedB,
				wasClipped
			);
		}

		return EnforcedRatio.builder()
			.aPct(roundedA)
			.bPct(roundedB)
			.wasClipped(wasClipped)
			.originalA(originalA)
			.originalB(originalB)
			.type(type)
			.build();
	}

	/**
	 * Rounds a percentage to the nearest 5-unit boundary.
	 * Examples:
	 * - 67 → 65 (round down)
	 * - 68 → 70 (round up)
	 * - 70 → 70 (exact)
	 *
	 * @param value Value to round (0-100)
	 * @return Rounded value (multiple of 5)
	 */
	private int roundToFive(int value) {
		return Math.round((float) value / 5) * 5;
	}
}
