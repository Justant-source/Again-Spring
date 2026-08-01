package com.againspring.aiuser.orchestrator.seed;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersonaFactoryStoryHelpersTest {

    @Test
    void normalizeStoryVoice_allowsNatepanBlindOnly() {
        assertThat(PersonaFactory.normalizeStoryVoice("natepan")).isEqualTo("NATEPAN");
        assertThat(PersonaFactory.normalizeStoryVoice("BLIND")).isEqualTo("BLIND");
        assertThat(PersonaFactory.normalizeStoryVoice("DCINSIDE")).isNull();
        assertThat(PersonaFactory.normalizeStoryVoice("")).isNull();
        assertThat(PersonaFactory.normalizeStoryVoice(null)).isNull();
    }

    @Test
    void biasInterestsToCategory_boostsKnownCategory() {
        Map<String, Double> interests = new LinkedHashMap<>();
        interests.put("COUPLE", 0.2);
        interests.put("WORK", 0.3);
        PersonaFactory.biasInterestsToCategory(interests, "work");
        assertThat(interests.get("WORK")).isGreaterThanOrEqualTo(0.75);
        assertThat(interests.get("COUPLE")).isEqualTo(0.2);
    }

    @Test
    void biasInterestsToCategory_ignoresUnknown() {
        Map<String, Double> interests = new LinkedHashMap<>();
        interests.put("OTHER", 0.1);
        PersonaFactory.biasInterestsToCategory(interests, "UNKNOWN_CAT");
        assertThat(interests).containsOnlyKeys("OTHER");
    }
}
