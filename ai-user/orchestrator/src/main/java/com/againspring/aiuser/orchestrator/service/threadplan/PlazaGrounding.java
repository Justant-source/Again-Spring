package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.Persona;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plaza labels used to ground popular-source claims. OTHER is last-resort only.
 */
public final class PlazaGrounding {
    public static final List<String> PRIMARY = List.of("COUPLE", "MARRIED", "FRIEND", "FAMILY", "WORK");

    private PlazaGrounding() {}

    /** Same rule as {@code AdminTriggerController.topCategory} / ActionExecutor. */
    public static String topCategory(Persona persona) {
        if (persona == null) return "OTHER";
        Map<String, Double> interests = persona.getInterests();
        if (interests == null || interests.isEmpty()) return "OTHER";
        return interests.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("OTHER");
    }

    /**
     * Preferred plaza first (persona top interest when it is a primary plaza), then the
     * remaining primary plazas, then OTHER.
     */
    public static List<String> retryOrder(Persona persona) {
        return retryOrderWithFamilyControl(persona, true);
    }

    /**
     * Preferred plaza first (persona top interest when it is a primary plaza), then the
     * remaining primary plazas, then OTHER.
     * When familyEnabled=false, FAMILY is replaced with OTHER if it would be first,
     * and excluded from the primary sequence.
     *
     * @param persona the persona to ground plaza preferences
     * @param familyEnabled if false, FAMILY is routed to OTHER (for nightly fill when corpus lacks family stories)
     */
    public static List<String> retryOrderWithFamilyControl(Persona persona, boolean familyEnabled) {
        String top = topCategory(persona);
        // When family is disabled, remap FAMILY → OTHER in top position
        if (!familyEnabled && "FAMILY".equals(top)) {
            top = "OTHER";
        }
        List<String> order = new ArrayList<>(PRIMARY.size() + 1);
        if (PRIMARY.contains(top)) {
            order.add(top);
        }
        for (String plaza : PRIMARY) {
            // Skip FAMILY if disabled
            if (!familyEnabled && "FAMILY".equals(plaza)) continue;
            if (!plaza.equals(top)) order.add(plaza);
        }
        order.add("OTHER");
        return List.copyOf(order);
    }
}
