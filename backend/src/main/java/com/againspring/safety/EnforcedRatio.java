package com.againspring.safety;

import com.againspring.domain.enums.ConflictType;

import lombok.Builder;
import lombok.Value;

/**
 * Result of a ratio clipping and normalization operation.
 *
 * Immutable DTO carrying:
 * - aPct: A's enforced percentage (0-100)
 * - bPct: B's enforced percentage (0-100), always sum to 100
 * - wasClipped: true if clipping or rounding occurred
 * - originalA: A's input percentage before processing
 * - originalB: B's input percentage before processing
 * - type: Conflict type used for clipping rules
 */
@Value
@Builder
public class EnforcedRatio {
	private final int aPct;
	private final int bPct;
	private final boolean wasClipped;
	private final Integer originalA;
	private final Integer originalB;
	private final ConflictType type;

	/**
	 * Validates that the ratio sums to 100.
	 *
	 * @return true if aPct + bPct == 100
	 */
	public boolean isValid() {
		return aPct + bPct == 100;
	}

	/**
	 * Alias for Lombok-generated isWasClipped() — matches the natural read: "was it clipped?".
	 */
	public boolean wasClipped() {
		return wasClipped;
	}

	/**
	 * Gets the clipping percentage difference from original.
	 *
	 * @return absolute difference from original for A (both A and B change equally)
	 */
	public int getClipAmount() {
		if (originalA == null) {
			return 0;
		}
		return Math.abs(aPct - originalA);
	}
}
