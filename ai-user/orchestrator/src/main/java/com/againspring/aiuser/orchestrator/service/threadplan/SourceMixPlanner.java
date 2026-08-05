package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.Persona;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Batch source-community mix: Blind 70% / Natepan 30%, then author persona by matching
 * {@code voice_profile.voice_type} (blind→BLIND, natepan→NATEPAN).
 */
public final class SourceMixPlanner {

    public static final String SOURCE_BLIND = "blind";
    public static final String SOURCE_NATEPAN = "natepan";
    public static final double BLIND_SHARE = 0.7;

    private SourceMixPlanner() {}

    public record MixCounts(int blind, int natepan) {
        public int total() {
            return blind + natepan;
        }
    }

    /**
     * Blind = round(0.7 * n), Natepan = n − blind (remainder prefers Blind).
     * Example: n=5 → 4 blind, 1 natepan.
     */
    public static MixCounts planCounts(int n) {
        if (n <= 0) return new MixCounts(0, 0);
        int blind = (int) Math.round(n * BLIND_SHARE);
        if (blind > n) blind = n;
        if (blind < 0) blind = 0;
        return new MixCounts(blind, n - blind);
    }

    /**
     * Shuffled list of preferred crawl sources ({@code blind}|{@code natepan}) of length {@code n}.
     */
    public static List<String> planSources(int n, Random rng) {
        MixCounts counts = planCounts(n);
        List<String> slots = new ArrayList<>(n);
        for (int i = 0; i < counts.blind(); i++) slots.add(SOURCE_BLIND);
        for (int i = 0; i < counts.natepan(); i++) slots.add(SOURCE_NATEPAN);
        if (rng != null) {
            Collections.shuffle(slots, rng);
        } else {
            Collections.shuffle(slots);
        }
        return slots;
    }

    /** Crawl source → persona {@code voice_type}. Unknown → empty. */
    public static Optional<String> voiceTypeForSource(String preferredSource) {
        if (preferredSource == null || preferredSource.isBlank()) return Optional.empty();
        return switch (preferredSource.trim().toLowerCase(Locale.ROOT)) {
            case SOURCE_BLIND -> Optional.of("BLIND");
            case SOURCE_NATEPAN -> Optional.of("NATEPAN");
            default -> Optional.empty();
        };
    }

    public static String extractVoiceType(Persona persona) {
        if (persona == null || persona.getVoiceProfile() == null) return null;
        Object v = persona.getVoiceProfile().get("voice_type");
        return v != null ? String.valueOf(v).trim() : null;
    }

    public static boolean matchesVoice(Persona persona, String voiceType) {
        if (voiceType == null || voiceType.isBlank()) return false;
        String vt = extractVoiceType(persona);
        return vt != null && voiceType.equalsIgnoreCase(vt);
    }

    /**
     * Pick one author whose {@code voice_type} matches the preferred crawl source.
     * Prefers HEAVY when any HEAVY match exists; otherwise any active match.
     * On success removes the chosen persona from {@code pool} (caller-owned mutable list).
     */
    public static Optional<Persona> pickAuthor(List<Persona> pool, String preferredSource, Random rng) {
        Optional<String> voiceOpt = voiceTypeForSource(preferredSource);
        if (voiceOpt.isEmpty() || pool == null || pool.isEmpty()) return Optional.empty();
        String voice = voiceOpt.get();

        List<Persona> matching = new ArrayList<>();
        for (Persona p : pool) {
            if (matchesVoice(p, voice)) matching.add(p);
        }
        if (matching.isEmpty()) return Optional.empty();

        List<Persona> heavy = new ArrayList<>();
        for (Persona p : matching) {
            if ("HEAVY".equals(p.getTier())) heavy.add(p);
        }
        List<Persona> candidates = heavy.isEmpty() ? matching : heavy;
        Random r = rng != null ? rng : new Random();
        Persona pick = candidates.get(r.nextInt(candidates.size()));
        pool.remove(pick);
        return Optional.of(pick);
    }

    /** Convenience: voice_type from a raw voice_profile map (tests / callers without Persona). */
    public static String voiceTypeFromProfile(Map<String, Object> voiceProfile) {
        if (voiceProfile == null) return null;
        Object v = voiceProfile.get("voice_type");
        return v != null ? String.valueOf(v).trim() : null;
    }
}
