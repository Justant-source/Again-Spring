package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.Persona;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlanPersonaMapperTest {

    @Test
    void formalityReadsVoiceProfileNotNeutralDefault() {
        Persona polite = persona(Map.of("formality", "polite"));
        Persona missing = persona(Map.of());
        assertThat(PlanPersonaMapper.formalityOf(polite)).isEqualTo("polite");
        assertThat(PlanPersonaMapper.formalityOf(missing)).isEqualTo("casual");
    }

    @Test
    void voiceProfileIsStructuredMapNeverStringValueOf() {
        Map<String, Object> vp = new LinkedHashMap<>();
        vp.put("formality", "casual");
        vp.put("voice_type", "BLIND");
        Persona p = persona(vp);
        Map<String, Object> mapped = PlanPersonaMapper.voiceProfileMap(p);
        assertThat(mapped).isInstanceOf(Map.class);
        assertThat(mapped.get("voice_type")).isEqualTo("BLIND");
        assertThat(mapped).isNotInstanceOf(String.class);
    }

    /**
     * 2026-08-01 회귀 방지: 활성 150명을 통째로 프롬프트에 넣다가 Claude 200K 토큰 한도를
     * 넘겨 REQUESTED 백로그 173건이 전부 실패했다. cap이 실제로 크기를 줄이는지 확인한다.
     */
    @Test
    void capCastPoolBoundsSizeWhenPoolExceedsMax() {
        List<Persona> pool = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) pool.add(persona(Map.of()));
        List<Persona> capped = PlanPersonaMapper.capCastPool(pool, 40);
        assertThat(capped).hasSize(40);
    }

    @Test
    void capCastPoolReturnsPoolUnchangedWhenAlreadyWithinBound() {
        List<Persona> pool = List.of(persona(Map.of()), persona(Map.of()));
        assertThat(PlanPersonaMapper.capCastPool(pool, 40)).hasSize(2);
    }

    private static Persona persona(Map<String, Object> voice) {
        return Persona.builder()
                .id("ai-user-x")
                .archetype("A")
                .tier("LIGHT")
                .voiceProfile(new LinkedHashMap<>(voice))
                .interests(Map.of())
                .biasProfile(Map.of())
                .circadian(List.of())
                .slangLevel(new BigDecimal("0.3"))
                .active(true)
                .createdAt(Instant.now())
                .build();
    }
}
