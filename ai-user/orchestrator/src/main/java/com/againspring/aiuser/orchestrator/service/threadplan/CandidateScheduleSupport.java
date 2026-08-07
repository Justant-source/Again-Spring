package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bakes absolute {@code scheduledAt} onto held candidate items so admins can preview/edit
 * comment/reply release times before the post is published. At publish time
 * {@link ThreadPlanGenerationService#persistResponse} prefers stored values and falls back
 * to {@link #schedule} for legacy rows that lack them.
 */
@Component
@RequiredArgsConstructor
public class CandidateScheduleSupport {
    private final OrchestratorProperties properties;

    /**
     * Dense early window, progressively wider later. Same formula historically private on
     * {@link ThreadPlanGenerationService}; kept here so hold-time enrichment and publish-time
     * fallback share one clock.
     */
    public Instant schedule(Instant publishedAt, int index, boolean reply) {
        long[] minutes = {3, 8, 14, 22, 34, 48, 65, 88, 115, 150, 195, 250, 320, 410, 520, 650, 780, 920, 1080};
        long delay = minutes[Math.min(index, minutes.length - 1)] + (reply ? 7 : 0);
        Instant candidate = publishedAt.plusSeconds(delay * 60);
        return ActivityCurve.nextActiveHour(candidate, 0.2, properties.getThreadPlan().getKstHourlyHumanWeights());
    }

    /**
     * Ensures every item in {@code response.items} has an ISO {@code scheduledAt}. Missing values
     * are filled with {@link #schedule}; existing values are left alone.
     */
    @SuppressWarnings("unchecked")
    public void enrichMissingScheduledAts(Map<String, Object> response, Instant postSlot) {
        if (response == null || postSlot == null) return;
        List<Map<String, Object>> items = mutableItems(response);
        int sequence = 0;
        for (Map<String, Object> item : items) {
            boolean reply = hasParent(item);
            Instant existing = parseScheduledAt(item.get("scheduledAt"));
            if (existing == null) {
                item.put("scheduledAt", schedule(postSlot, sequence, reply).toString());
            }
            sequence++;
        }
        response.put("items", items);
    }

    /** Shifts every item {@code scheduledAt} by {@code delta} (used when the post slot moves). */
    @SuppressWarnings("unchecked")
    public void shiftScheduledAts(Map<String, Object> response, Duration delta) {
        if (response == null || delta == null || delta.isZero()) return;
        List<Map<String, Object>> items = mutableItems(response);
        for (Map<String, Object> item : items) {
            Instant at = parseScheduledAt(item.get("scheduledAt"));
            if (at != null) {
                item.put("scheduledAt", at.plus(delta).toString());
            }
        }
        response.put("items", items);
    }

    /**
     * Recomputes every item {@code scheduledAt} from {@code publishAt} using {@link #schedule}.
     * Used at auto-publish so comment release stays dense after the real post time even when
     * the hold slot was moved without shifting baked candidate times (ops SQL / partial PATCH).
     */
    @SuppressWarnings("unchecked")
    public void rescheduleFromPublishAt(Map<String, Object> response, Instant publishAt) {
        if (response == null || publishAt == null) return;
        List<Map<String, Object>> items = mutableItems(response);
        int sequence = 0;
        for (Map<String, Object> item : items) {
            boolean reply = hasParent(item);
            item.put("scheduledAt", schedule(publishAt, sequence, reply).toString());
            sequence++;
        }
        response.put("items", items);
    }

    /**
     * Phase1 invariant: every item {@code scheduledAt} must be strictly before {@code partnerAt}
     * (T0+Δ). Missing values are filled then clamped. Items already before the deadline are kept.
     * When the (publishedAt, partnerAt) window is too short, times pack into the open interval.
     */
    @SuppressWarnings("unchecked")
    public void clampScheduledAtsBefore(Map<String, Object> response, Instant publishedAt, Instant partnerAt) {
        if (response == null || partnerAt == null) return;
        Instant origin = publishedAt != null ? publishedAt : partnerAt.minusSeconds(600);
        Instant lastAllowed = partnerAt.minusSeconds(1);
        if (!lastAllowed.isAfter(origin)) {
            // Degenerate window — pin everything one second before partner.
            List<Map<String, Object>> items = mutableItems(response);
            for (Map<String, Object> item : items) {
                item.put("scheduledAt", lastAllowed.toString());
            }
            response.put("items", items);
            return;
        }
        enrichMissingScheduledAts(response, origin);
        List<Map<String, Object>> items = mutableItems(response);
        int n = items.size();
        List<Integer> overflowIdx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Instant at = parseScheduledAt(items.get(i).get("scheduledAt"));
            if (at == null || !at.isBefore(partnerAt) || !at.isAfter(origin)) {
                overflowIdx.add(i);
            }
        }
        if (!overflowIdx.isEmpty()) {
            long spanMs = Math.max(1L, lastAllowed.toEpochMilli() - origin.toEpochMilli());
            int count = overflowIdx.size();
            for (int k = 0; k < count; k++) {
                // Spread strictly inside (origin, lastAllowed] — never equal partnerAt.
                long offset = spanMs * (k + 1L) / (count + 1L);
                Instant slot = Instant.ofEpochMilli(origin.toEpochMilli() + offset);
                if (!slot.isBefore(partnerAt)) {
                    slot = lastAllowed;
                }
                if (!slot.isAfter(origin)) {
                    slot = origin.plusSeconds(1);
                    if (!slot.isBefore(partnerAt)) slot = lastAllowed;
                }
                items.get(overflowIdx.get(k)).put("scheduledAt", slot.toString());
            }
        }
        response.put("items", items);
    }

    /**
     * Phase2 invariant: every item {@code scheduledAt} is at or after {@code partnerPublishedAt}.
     * Missing values are filled from that origin; early times are pushed forward.
     */
    @SuppressWarnings("unchecked")
    public void clampScheduledAtsOnOrAfter(Map<String, Object> response, Instant partnerPublishedAt) {
        if (response == null || partnerPublishedAt == null) return;
        enrichMissingScheduledAts(response, partnerPublishedAt);
        List<Map<String, Object>> items = mutableItems(response);
        int sequence = 0;
        for (Map<String, Object> item : items) {
            Instant at = parseScheduledAt(item.get("scheduledAt"));
            if (at == null || at.isBefore(partnerPublishedAt)) {
                boolean reply = hasParent(item);
                item.put("scheduledAt", schedule(partnerPublishedAt, sequence, reply).toString());
            }
            sequence++;
        }
        response.put("items", items);
    }

    /** Single-value clamp used when persisting without a full response rewrite. */
    public Instant clampBefore(Instant scheduledAt, Instant partnerAt) {
        if (partnerAt == null) return scheduledAt;
        Instant lastAllowed = partnerAt.minusSeconds(1);
        if (scheduledAt == null) return lastAllowed;
        return scheduledAt.isBefore(partnerAt) ? scheduledAt : lastAllowed;
    }

    public Instant clampOnOrAfter(Instant scheduledAt, Instant notBefore) {
        if (notBefore == null) return scheduledAt;
        if (scheduledAt == null) return notBefore;
        return scheduledAt.isBefore(notBefore) ? notBefore : scheduledAt;
    }

    public Instant parseScheduledAt(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) return null;
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    public int countItems(Map<String, Object> response) {
        if (response == null) return 0;
        Object raw = response.get("items");
        if (!(raw instanceof List<?> list)) return 0;
        return list.size();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> mutableItems(Map<String, Object> response) {
        Object raw = response.get("items");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object row : list) {
            if (row instanceof Map<?, ?> map) {
                out.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return out;
    }

    private static boolean hasParent(Map<String, Object> item) {
        Object parent = item.get("parentRef");
        if (parent == null) return false;
        return !String.valueOf(parent).trim().isEmpty();
    }
}
