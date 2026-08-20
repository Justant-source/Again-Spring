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
        String top = topCategory(persona);
        List<String> order = new ArrayList<>(PRIMARY.size() + 1);
        if (PRIMARY.contains(top)) {
            order.add(top);
        }
        for (String plaza : PRIMARY) {
            if (!plaza.equals(top)) order.add(plaza);
        }
        order.add("OTHER");
        return List.copyOf(order);
    }
}
