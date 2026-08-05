package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.Persona;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SourceMixPlannerTest {

    @Test
    void planCounts_prefersBlindOnRemainder() {
        assertThat(SourceMixPlanner.planCounts(0)).isEqualTo(new SourceMixPlanner.MixCounts(0, 0));
        assertThat(SourceMixPlanner.planCounts(1)).isEqualTo(new SourceMixPlanner.MixCounts(1, 0));
        assertThat(SourceMixPlanner.planCounts(2)).isEqualTo(new SourceMixPlanner.MixCounts(1, 1));
        assertThat(SourceMixPlanner.planCounts(5)).isEqualTo(new SourceMixPlanner.MixCounts(4, 1));
        assertThat(SourceMixPlanner.planCounts(10)).isEqualTo(new SourceMixPlanner.MixCounts(7, 3));
    }

    @Test
    void planCounts_alwaysSumsToN() {
        for (int n = 0; n <= 20; n++) {
            SourceMixPlanner.MixCounts c = SourceMixPlanner.planCounts(n);
            assertThat(c.total()).isEqualTo(n);
            assertThat(c.blind()).isGreaterThanOrEqualTo(0);
            assertThat(c.natepan()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void planSources_matchesCounts() {
        Random rng = new Random(42);
        List<String> slots = SourceMixPlanner.planSources(5, rng);
        assertThat(slots).hasSize(5);
        long blind = slots.stream().filter(SourceMixPlanner.SOURCE_BLIND::equals).count();
        long natepan = slots.stream().filter(SourceMixPlanner.SOURCE_NATEPAN::equals).count();
        assertThat(blind).isEqualTo(4);
        assertThat(natepan).isEqualTo(1);
    }

    @Test
    void voiceTypeForSource_mapsCommunities() {
        assertThat(SourceMixPlanner.voiceTypeForSource("blind")).contains("BLIND");
        assertThat(SourceMixPlanner.voiceTypeForSource("BLIND")).contains("BLIND");
        assertThat(SourceMixPlanner.voiceTypeForSource("natepan")).contains("NATEPAN");
        assertThat(SourceMixPlanner.voiceTypeForSource("other")).isEmpty();
        assertThat(SourceMixPlanner.voiceTypeForSource(null)).isEmpty();
    }

    @Test
    void pickAuthor_prefersHeavyMatchingVoice() {
        List<Persona> pool = new ArrayList<>();
        pool.add(persona("b-light", "LIGHT", "BLIND"));
        pool.add(persona("b-heavy", "HEAVY", "BLIND"));
        pool.add(persona("n-heavy", "HEAVY", "NATEPAN"));

        Optional<Persona> picked = SourceMixPlanner.pickAuthor(pool, "blind", new Random(1));
        assertThat(picked).isPresent();
        assertThat(picked.get().getId()).isEqualTo("b-heavy");
        assertThat(pool).extracting(Persona::getId).doesNotContain("b-heavy");
    }

    @Test
    void pickAuthor_fallsBackToNonHeavyWhenNoHeavyMatch() {
        List<Persona> pool = new ArrayList<>();
        pool.add(persona("b-light", "LIGHT", "BLIND"));
        pool.add(persona("n-heavy", "HEAVY", "NATEPAN"));

        Optional<Persona> picked = SourceMixPlanner.pickAuthor(pool, "blind", new Random(1));
        assertThat(picked).isPresent();
        assertThat(picked.get().getId()).isEqualTo("b-light");
    }

    @Test
    void pickAuthor_skipsWhenNoMatchingVoice() {
        List<Persona> pool = new ArrayList<>();
        pool.add(persona("n1", "HEAVY", "NATEPAN"));
        assertThat(SourceMixPlanner.pickAuthor(pool, "blind", new Random(1))).isEmpty();
        assertThat(pool).hasSize(1);
    }

    private static Persona persona(String id, String tier, String voiceType) {
        Map<String, Object> vp = new HashMap<>();
        vp.put("voice_type", voiceType);
        return Persona.builder()
                .id(id)
                .archetype("test")
                .tier(tier)
                .voiceProfile(vp)
                .interests(Map.of("WORK", 1.0))
                .biasProfile(Map.of())
                .circadian(List.of())
                .active(true)
                .build();
    }
}
