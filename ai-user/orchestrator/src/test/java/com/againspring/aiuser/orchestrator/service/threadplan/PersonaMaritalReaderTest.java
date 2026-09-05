package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.Persona;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * persona-diversity-v4 계약1 — {@code personas.marital}(V22) 컬럼 읽기 어댑터.
 * 컬럼이 SSOT다. {@code voice_profile.marital}은 어떤 코드도 채우지 않아 읽지 않는다.
 */
class PersonaMaritalReaderTest {

    @Test
    void readsMarriedFromColumn() {
        assertThat(PersonaMaritalReader.read(persona("MARRIED"))).isEqualTo("MARRIED");
        assertThat(PersonaMaritalReader.isMarried(persona("MARRIED"))).isTrue();
    }

    @Test
    void readsDatingAndEngaged() {
        assertThat(PersonaMaritalReader.read(persona("DATING"))).isEqualTo("DATING");
        assertThat(PersonaMaritalReader.read(persona("ENGAGED"))).isEqualTo("ENGAGED");
        assertThat(PersonaMaritalReader.isMarried(persona("DATING"))).isFalse();
    }

    @Test
    void missingOrUnknownValueDefaultsToSingle() {
        assertThat(PersonaMaritalReader.read(persona(null))).isEqualTo("SINGLE");
        assertThat(PersonaMaritalReader.read(persona("이혼"))).isEqualTo("SINGLE");
        assertThat(PersonaMaritalReader.read(null)).isEqualTo("SINGLE");
    }

    @Test
    void voiceProfileMaritalIsIgnored() {
        Map<String, Object> vp = new LinkedHashMap<>();
        vp.put("marital", "MARRIED");
        Persona p = Persona.builder()
                .id("p-vp-only")
                .archetype("TEST")
                .tier("REGULAR")
                .marital("SINGLE")
                .voiceProfile(vp)
                .interests(Map.of())
                .biasProfile(Map.of())
                .circadian(List.of())
                .slangLevel(new BigDecimal("0.3"))
                .active(true)
                .createdAt(Instant.now())
                .build();
        assertThat(PersonaMaritalReader.read(p)).isEqualTo("SINGLE");
        assertThat(PersonaMaritalReader.isMarried(p)).isFalse();
    }

    @Test
    void caseInsensitiveMatching() {
        assertThat(PersonaMaritalReader.read(persona("married"))).isEqualTo("MARRIED");
    }

    private static Persona persona(String marital) {
        return Persona.builder()
                .id("p-marital")
                .marital(marital)
                .archetype("TEST")
                .tier("REGULAR")
                .voiceProfile(new LinkedHashMap<>())
                .interests(Map.of())
                .biasProfile(Map.of())
                .circadian(List.of())
                .slangLevel(new BigDecimal("0.3"))
                .active(true)
                .createdAt(Instant.now())
                .build();
    }
}
