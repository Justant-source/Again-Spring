package com.againspring.aiuser.orchestrator.service.threadplan;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure §16.7 human-reply budget accounting: post max, per-persona max, distinct-persona max.
 * Caller loads existing rows then {@link #tryAccept(String)} before each new persist.
 */
public final class HumanReplyBudget {
    private final int postMax;
    private final int personaMax;
    private final int distinctMax;
    private int postCount;
    private final Map<String, Integer> byPersona = new HashMap<>();

    public HumanReplyBudget(int postMax, int personaMax, int distinctMax) {
        this.postMax = Math.max(0, postMax);
        this.personaMax = Math.max(0, personaMax);
        this.distinctMax = Math.max(0, distinctMax);
    }

    public void seed(String personaId) {
        if (personaId == null || personaId.isBlank()) return;
        byPersona.merge(personaId, 1, Integer::sum);
        postCount++;
    }

    /** @return true and records when accepting {@code personaId} stays within all caps. */
    public boolean tryAccept(String personaId) {
        if (personaId == null || personaId.isBlank()) return false;
        if (postCount >= postMax) return false;
        int current = byPersona.getOrDefault(personaId, 0);
        if (current >= personaMax) return false;
        if (current == 0 && byPersona.size() >= distinctMax) return false;
        byPersona.put(personaId, current + 1);
        postCount++;
        return true;
    }

    public boolean canAccept(String personaId) {
        if (personaId == null || personaId.isBlank()) return false;
        if (postCount >= postMax) return false;
        int current = byPersona.getOrDefault(personaId, 0);
        if (current >= personaMax) return false;
        return current != 0 || byPersona.size() < distinctMax;
    }

    public int postCount() { return postCount; }
    public int personaCount(String personaId) { return byPersona.getOrDefault(personaId, 0); }
    public int distinctCount() { return byPersona.size(); }
}
