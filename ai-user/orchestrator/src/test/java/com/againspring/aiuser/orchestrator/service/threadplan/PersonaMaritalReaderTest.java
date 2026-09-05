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
 * persona-diversity-v4 계약1 — {@code voice_profile.marital} fallback 어댑터.
 * 이 브랜치엔 아직 personas.marital 컬럼·getter가 없어 voice_profile만 검증한다.
 */
class PersonaMaritalReaderTest {

    @Test
    void readsMarriedFromVoiceProfile() {
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
    void personaWithoutVoiceProfileDefaultsToSingle() {
        Persona p = Persona.builder()
                .id("p-novp")
                .archetype("TEST")
                .tier("REGULAR")
                .voiceProfile(null)
                .interests(Map.of())
                .biasProfile(Map.of())
                .circadian(List.of())
                .slangLevel(new BigDecimal("0.3"))
                .active(true)
                .createdAt(Instant.now())
                .build();
        assertThat(PersonaMaritalReader.read(p)).isEqualTo("SINGLE");
    }

    @Test
    void caseInsensitiveMatching() {
        assertThat(PersonaMaritalReader.read(persona("married"))).isEqualTo("MARRIED");
    }

    private static Persona persona(String marital) {
        Map<String, Object> voiceProfile = new LinkedHashMap<>();
        if (marital != null) voiceProfile.put("marital", marital);
        return Persona.builder()
                .id("p-marital")
                .archetype("TEST")
                .tier("REGULAR")
                .voiceProfile(voiceProfile)
                .interests(Map.of())
                .biasProfile(Map.of())
                .circadian(List.of())
                .slangLevel(new BigDecimal("0.3"))
                .active(true)
                .createdAt(Instant.now())
                .build();
    }
}
