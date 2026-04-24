package com.againspring.service.report;

import com.againspring.domain.Report;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validates needs map coordinates and structure.
 * Positions must be in -100..+100 range.
 */
@Slf4j
@Component
public class NeedsMapValidator {

    private static final int MIN_COORD = -100;
    private static final int MAX_COORD = 100;

    /**
     * Validates a position coordinate.
     *
     * @param position Position to validate
     * @return true if valid (in range -100..100), false otherwise
     */
    public boolean validatePosition(Report.NeedsMap.Position position) {
        if (position == null) {
            return false;
        }

        return isInRange(position.getX()) && isInRange(position.getY());
    }

    /**
     * Validates entire needs map.
     *
     * @param needsMap Map to validate
     * @return true if valid
     */
    public boolean validateMap(Report.NeedsMap needsMap) {
        if (needsMap == null) {
            return false;
        }

        boolean validA = needsMap.getPositionA() == null || validatePosition(needsMap.getPositionA());
        boolean validB = needsMap.getPositionB() == null || validatePosition(needsMap.getPositionB());
        boolean hasAxis = needsMap.getAxisX() != null && !needsMap.getAxisX().isBlank()
                && needsMap.getAxisY() != null && !needsMap.getAxisY().isBlank();

        return validA && validB && hasAxis;
    }

    /**
     * Clips coordinate to valid range.
     */
    public int clipCoordinate(int value) {
        return Math.max(MIN_COORD, Math.min(MAX_COORD, value));
    }

    /**
     * Creates a fallback position (center).
     */
    public Report.NeedsMap.Position createFallback() {
        return Report.NeedsMap.Position.builder()
                .x(0)
                .y(0)
                .build();
    }

    private boolean isInRange(int value) {
        return value >= MIN_COORD && value <= MAX_COORD;
    }
}
