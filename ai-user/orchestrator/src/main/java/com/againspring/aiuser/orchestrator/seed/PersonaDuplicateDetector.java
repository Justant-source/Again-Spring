package com.againspring.aiuser.orchestrator.seed;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure duplicate / soft-distance check for auto-persona provisioning (WP3 W4-C).
 * No DB. Distance is small-integer; callers treat {@code distance <= softMax} as conflict.
 *
 * <p>Axes: age + gender + job + voice_type. Soft rules:
 * <ul>
 *   <li>0 — all four equal (normalized)</li>
 *   <li>1 — age/gender/voice equal and job soft-match (blank on one side) or adjacent age band + same job</li>
 *   <li>{@link Integer#MAX_VALUE} — not a near-duplicate</li>
 * </ul>
 */
public final class PersonaDuplicateDetector {

    private static final Set<String> ALLOWED_VOICES = Set.of("NATEPAN", "BLIND");

    private PersonaDuplicateDetector() {}

    /** Normalized identity snapshot used for distance. */
    public record Identity(String age, String gender, String job, String voiceType) {
        public Identity {
            age = norm(age);
            gender = normGender(gender);
            job = norm(job);
            voiceType = normVoice(voiceType);
        }

        public static Identity of(String age, String gender, String job, String voiceType) {
            return new Identity(age, gender, job, voiceType);
        }
    }

    /**
     * Soft distance between two identities. Lower = closer.
     * Returns {@link Integer#MAX_VALUE} when not comparable as a near-duplicate.
     */
    public static int distance(Identity a, Identity b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        if (a.gender().isEmpty() || b.gender().isEmpty() || !a.gender().equals(b.gender())) {
            return Integer.MAX_VALUE;
        }
        if (a.voiceType().isEmpty() || b.voiceType().isEmpty()
                || !a.voiceType().equals(b.voiceType())
                || !ALLOWED_VOICES.contains(a.voiceType())) {
            return Integer.MAX_VALUE;
        }

        boolean ageExact = !a.age().isEmpty() && a.age().equals(b.age());
        boolean ageAdj = agesAdjacent(a.age(), b.age());
        if (!ageExact && !ageAdj) return Integer.MAX_VALUE;

        int jobDist = jobDistance(a.job(), b.job());
        if (jobDist == Integer.MAX_VALUE) return Integer.MAX_VALUE;

        if (ageExact && jobDist == 0) return 0;
        if (ageExact && jobDist == 1) return 1;
        if (ageAdj && jobDist == 0) return 1;
        return Integer.MAX_VALUE;
    }

    /**
     * First existing identity within {@code softMax} distance of {@code candidate}, if any.
     * Default softMax for auto-provision is {@code 1}.
     */
    public static Optional<Identity> findNearDuplicate(
            Collection<Identity> existing, Identity candidate, int softMax) {
        if (existing == null || candidate == null || softMax < 0) return Optional.empty();
        for (Identity e : existing) {
            if (distance(e, candidate) <= softMax) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    /** Convenience overload: softMax = 1. */
    public static Optional<Identity> findNearDuplicate(
            Collection<Identity> existing, Identity candidate) {
        return findNearDuplicate(existing, candidate, 1);
    }

    static int jobDistance(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 0;
        if (a.isEmpty() || b.isEmpty()) return 1;
        if (a.equals(b)) return 0;
        return Integer.MAX_VALUE;
    }

    static boolean agesAdjacent(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        List<String> order = List.of(
                "10s", "20s_early", "20s_late", "30s_early", "30s_late", "40s", "50s", "60s");
        int ia = order.indexOf(a);
        int ib = order.indexOf(b);
        if (ia < 0 || ib < 0) return false;
        return Math.abs(ia - ib) == 1;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String normGender(String s) {
        String g = norm(s);
        if (g.isEmpty()) return "";
        if (g.equals("m") || g.equals("male") || g.equals("남") || g.equals("남성")) return "m";
        if (g.equals("f") || g.equals("female") || g.equals("여") || g.equals("여성")) return "f";
        return g;
    }

    private static String normVoice(String s) {
        String v = Objects.toString(s, "").trim().toUpperCase(Locale.ROOT);
        return ALLOWED_VOICES.contains(v) ? v : (v.isEmpty() ? "" : v);
    }
}
