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
