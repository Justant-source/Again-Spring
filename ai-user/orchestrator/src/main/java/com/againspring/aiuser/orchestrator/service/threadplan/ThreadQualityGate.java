package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.util.LiteralNewlineNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Post-LLM thread candidate filter before plan READY.
 * Drops bad items (never pads with filler). Operational mins set
 * {@link QualityResult#passedOperationalMin()}; {@link #FAILURE_QUALITY_BELOW_MIN} is a reason
 * code only — {@code persistAndFinalize} may regen once then thin-READY rather than fail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadQualityGate {
    public static final String FAILURE_QUALITY_BELOW_MIN = "QUALITY_BELOW_MIN_ITEMS";
    public static final String UNEVALUATED_STANCE = "UNEVALUATED:stance";
    public static final double DEFAULT_STANCE_SHARE_MAX = 0.80;

    private final ContentSafetyGuard safetyGuard;

    /**
     * Validate candidates: cast membership, story-side exclusion (post author / partner),
     * parent graph (reply → earlier kept top-level), body safety, then stance share cap
     * among items that declare stance.
     */
    public QualityResult evaluate(List<?> rawItems,
                                  Set<String> requestedCast,
                                  Predicate<String> personaExists,
                                  int readyMinTopLevel,
                                  int readyMinItems) {
        return evaluate(rawItems, requestedCast, personaExists, readyMinTopLevel, readyMinItems,
                DEFAULT_STANCE_SHARE_MAX, Set.of());
    }

    public QualityResult evaluate(List<?> rawItems,
                                  Set<String> requestedCast,
                                  Predicate<String> personaExists,
                                  int readyMinTopLevel,
                                  int readyMinItems,
                                  double stanceShareMax) {
        return evaluate(rawItems, requestedCast, personaExists, readyMinTopLevel, readyMinItems,
                stanceShareMax, Set.of());
    }

    /**
     * @param excludedStoryPersonaIds post author and/or partner user ids — must never appear as
     *        bystander comment/reply personas in a generated plan (human-reply batch bypasses this gate).
     */
    public QualityResult evaluate(List<?> rawItems,
                                  Set<String> requestedCast,
                                  Predicate<String> personaExists,
                                  int readyMinTopLevel,
                                  int readyMinItems,
                                  double stanceShareMax,
                                  Set<String> excludedStoryPersonaIds) {
        List<String> reasons = new ArrayList<>();
        List<Candidate> provisional = new ArrayList<>();
        Set<String> seenRefs = new HashSet<>();
        Set<String> keptTopLevelRefs = new HashSet<>();
        Set<String> cast = requestedCast == null ? Set.of() : requestedCast;
        Set<String> excluded = excludedStoryPersonaIds == null ? Set.of() : excludedStoryPersonaIds;
        Predicate<String> exists = personaExists == null ? id -> true : personaExists;
        int dropped = 0;
        double cap = stanceShareMax <= 0 ? DEFAULT_STANCE_SHARE_MAX : stanceShareMax;

        for (Object raw : rawItems == null ? List.of() : rawItems) {
            if (!(raw instanceof Map<?, ?> row)) {
                dropped++;
                reasons.add("INVALID_ITEM");
                continue;
            }
            String ref = text(row.get("ref"));
            String parentRef = text(row.get("parentRef"));
            String body = text(row.get("body"));
            String personaId = text(row.get("personaId"));
            String stance = text(row.get("stance"));

            if (ref.isBlank() || body.isBlank() || personaId.isBlank() || !seenRefs.add(ref)) {
                dropped++;
                reasons.add("INVALID_CANDIDATE:" + (ref.isBlank() ? "?" : ref));
                continue;
            }
            if (!excluded.isEmpty() && excluded.contains(personaId)) {
                dropped++;
                reasons.add("STORY_PERSONA:" + ref);
                continue;
            }
            if (!cast.isEmpty() && !cast.contains(personaId)) {
                dropped++;
                reasons.add("CAST:" + ref);
                continue;
            }
            if (!exists.test(personaId)) {
                dropped++;
                reasons.add("PERSONA_UNKNOWN:" + ref);
                continue;
            }
            if (!parentRef.isBlank()) {
                if (!keptTopLevelRefs.contains(parentRef)) {
                    dropped++;
                    reasons.add("PARENT:" + ref);
                    continue;
                }
            }
            ContentSafetyGuard.GuardResult guard = safetyGuard.check(body, ContentSafetyGuard.ContentType.COMMENT);
            if (!guard.passed()) {
                dropped++;
                reasons.add("SAFETY:" + ref + ":" + guard.reason());
                continue;
            }

            Map<String, Object> normalized = copyRow(row, ref, parentRef, body, personaId, stance);
            Candidate c = new Candidate(ref, parentRef, personaId, body, stance, normalized);
            provisional.add(c);
            if (parentRef.isBlank()) keptTopLevelRefs.add(ref);
        }

        boolean anyStance = provisional.stream().anyMatch(c -> !c.stance().isBlank());
        if (!anyStance) {
            reasons.add(UNEVALUATED_STANCE);
        } else {
            int stanceDropped = applyStanceCap(provisional, reasons, cap);
            dropped += stanceDropped;
        }

        List<Map<String, Object>> keptItems = provisional.stream().map(Candidate::row).toList();
        long topLevel = provisional.stream().filter(c -> c.parentRef().isBlank()).count();
        boolean passed = topLevel >= readyMinTopLevel && keptItems.size() >= readyMinItems;
        if (!passed) {
            reasons.add(FAILURE_QUALITY_BELOW_MIN
                    + ":top=" + topLevel + "/" + readyMinTopLevel
                    + ",items=" + keptItems.size() + "/" + readyMinItems);
        }
        return new QualityResult(keptItems, dropped, List.copyOf(reasons), passed);
    }

    /**
     * Among stance-bearing kept items, drop from the majority stance (last first) until
     * no stance exceeds {@code stanceShareMax}. Cascade-drops replies of removed parents.
     */
    static int applyStanceCap(List<Candidate> kept, List<String> reasons, double stanceShareMax) {
        int dropped = 0;
        while (true) {
            List<Candidate> withStance = kept.stream().filter(c -> !c.stance().isBlank()).toList();
            if (withStance.isEmpty()) return dropped;

            Map<String, Integer> counts = new HashMap<>();
            for (Candidate c : withStance) {
                String key = c.stance().toUpperCase(Locale.ROOT);
                counts.merge(key, 1, Integer::sum);
            }
            String majority = null;
            double maxShare = 0;
            int n = withStance.size();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                double share = e.getValue() / (double) n;
                if (share > maxShare) {
                    maxShare = share;
                    majority = e.getKey();
                }
            }
            if (maxShare <= stanceShareMax || majority == null) return dropped;

            Candidate victim = null;
            for (int i = withStance.size() - 1; i >= 0; i--) {
                if (withStance.get(i).stance().equalsIgnoreCase(majority)) {
                    victim = withStance.get(i);
                    break;
                }
            }
            if (victim == null) return dropped;
            dropped += cascadeRemove(kept, victim.ref(), reasons, "STANCE_CAP:" + majority);
        }
    }

    /** Remove {@code ref} and any replies that parent to it (recursive). */
    static int cascadeRemove(List<Candidate> kept, String ref, List<String> reasons, String reasonPrefix) {
        int removed = 0;
        boolean progress = true;
        Set<String> doomed = new HashSet<>();
        doomed.add(ref);
        while (progress) {
            progress = false;
            for (Candidate c : kept) {
                if (!c.parentRef().isBlank() && doomed.contains(c.parentRef()) && doomed.add(c.ref())) {
                    progress = true;
                }
            }
        }
        for (int i = kept.size() - 1; i >= 0; i--) {
            Candidate c = kept.get(i);
            if (doomed.contains(c.ref())) {
                reasons.add(reasonPrefix + ":" + c.ref());
                kept.remove(i);
                removed++;
            }
        }
        return removed;
    }

    private static Map<String, Object> copyRow(Map<?, ?> row, String ref, String parentRef,
                                                 String body, String personaId, String stance) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : row.entrySet()) {
            if (e.getKey() != null) out.put(String.valueOf(e.getKey()), e.getValue());
        }
        out.put("ref", ref);
        if (parentRef.isBlank()) out.remove("parentRef");
        else out.put("parentRef", parentRef);
        out.put("body", body);
        out.put("personaId", personaId);
        if (stance.isBlank()) out.remove("stance");
        else out.put("stance", stance);
        return out;
    }

    private static String text(Object value) {
        if (value == null) return "";
        return LiteralNewlineNormalizer.normalize(String.valueOf(value)).trim();
    }

    /** Working candidate during gate evaluation. */
    record Candidate(String ref, String parentRef, String personaId, String body, String stance,
                     Map<String, Object> row) { }

    public record QualityResult(
            List<Map<String, Object>> keptItems,
            int dropped,
            List<String> reasons,
            boolean passedOperationalMin
    ) { }
}
