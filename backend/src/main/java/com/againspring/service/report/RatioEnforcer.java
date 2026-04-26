package com.againspring.service.report;

import com.againspring.domain.enums.ConflictType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Enforces contribution ratio clipping rules per conflict type.
 * Prevents unrealistic ratios while preserving nuance within safe bounds.
 */
@Slf4j
@Component
public class RatioEnforcer {

    /**
     * Enforce ratio bounds based on conflict type.
     * Rules (from shared/docs/policies/ratio-calculation.md):
     * - factual: 90:10 to 10:90 (80% max spread)
     * - difference: 70:30 to 30:70 (40% max spread)
     * - mixed: 80:20 to 20:80 (60% max spread)
     */
    public Enforced enforce(int rawA, int rawB, ConflictType conflictType) {
        int maxA;

        // Determine max allowed value for A (minimum will be 100 - maxA)
        if (conflictType == ConflictType.FACTUAL) {
            maxA = 90;  // Allows 90:10 to 10:90
        } else if (conflictType == ConflictType.DIFFERENCE) {
            maxA = 70;  // Allows 70:30 to 30:70
        } else {
            // MIXED or null
            maxA = 80;  // Allows 80:20 to 20:80
        }

        // Clip rawA to [100-maxA, maxA]
        int clippedA = Math.min(maxA, Math.max(100 - maxA, rawA));
        int clippedB = 100 - clippedA;

        log.debug("Ratio enforcement for {}: raw {}:{}, enforced {}:{}",
                conflictType, rawA, rawB, clippedA, clippedB);

        return new Enforced(clippedA, clippedB, rawA != clippedA);
    }

    public record Enforced(int a, int b, boolean wasClipped) {}
}
