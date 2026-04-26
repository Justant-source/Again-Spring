package com.againspring.service.report;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects an appropriate metaphor based on conversation metrics.
 * Used for report visualization/theming.
 */
@Slf4j
@Component
public class MetaphorSelector {

    /**
     * Select a metaphor based on Gottman four horsemen scores, bid response rate, and repair attempts.
     */
    public String select(List<Integer> fourHorsemen, double bidResponseRate, int repairAttempts) {
        if (fourHorsemen == null || fourHorsemen.size() < 4) {
            return "cracked-window";  // Default fallback
        }

        int criticism = fourHorsemen.get(0);
        int contempt = fourHorsemen.get(1);
        int defensiveness = fourHorsemen.get(2);
        int stonewalling = fourHorsemen.get(3);

        log.debug("Metaphor selection: criticism={}, contempt={}, defensiveness={}, stonewalling={}, bids={}, repairs={}",
                criticism, contempt, defensiveness, stonewalling, bidResponseRate, repairAttempts);

        // Stonewalling dominant + low bid response = locked door
        if (stonewalling >= 7 && bidResponseRate < 0.30) {
            return "locked-door";
        }

        // Strong stonewalling + weak repair = locked mailbox
        if (stonewalling >= 6 && repairAttempts < 2) {
            return "locked-mailbox";
        }

        // Criticism + contempt combo = boiling kettle
        if (criticism >= 6 && (criticism + contempt) / 2.0 >= 5) {
            return "boiling-kettle";
        }

        // Defensiveness dominant = too big umbrella
        if (defensiveness >= 5) {
            return "too-big-umbrella";
        }

        // Very low bid response = person in rain
        if (bidResponseRate < 0.20) {
            return "person-in-rain";
        }

        // High criticism + contempt = overflowing cup
        if (criticism + contempt >= 8) {
            return "overflowing-cup";
        }

        // Stonewalling alone = empty chair
        if (stonewalling >= 6) {
            return "empty-chair";
        }

        // Mild stonewalling + low overall score = frozen pond
        if (stonewalling >= 5 && fourHorsemen.stream().mapToInt(Integer::intValue).sum() <= 12) {
            return "frozen-pond";
        }

        // Good repair attempts = half-open-letter or rope-bridge
        if (repairAttempts >= 3) {
            return "half-open-letter";
        }

        if (repairAttempts >= 2) {
            return "rope-bridge";
        }

        // Low overall scores + decent repairs = two trees roots
        if (fourHorsemen.stream().allMatch(v -> v <= 3) && repairAttempts >= 3) {
            return "two-trees-roots";
        }

        // Default
        return "cracked-window";
    }
}
