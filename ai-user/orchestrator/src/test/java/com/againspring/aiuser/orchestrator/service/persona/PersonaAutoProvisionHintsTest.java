package com.againspring.aiuser.orchestrator.service.persona;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersonaAutoProvisionHintsTest {

    @Test
    void fromMap_readsNestedExplicitIdentity() {
        var hints = PersonaAutoProvisionService.ProvisionHints.fromMap(Map.of(
                "category", "WORK",
                "sourceRegister", "BLIND",
                "explicitIdentity", Map.of(
                        "age", "30s_early",
                        "gender", "M",
                        "job", "직장인"
                ),
                "sourceExampleId", 42
        ));
        assertThat(hints.category()).isEqualTo("WORK");
        assertThat(hints.sourceRegister()).isEqualTo("BLIND");
        assertThat(hints.age()).isEqualTo("30s_early");
        assertThat(hints.gender()).isEqualTo("M");
        assertThat(hints.job()).isEqualTo("직장인");
        assertThat(hints.sourceExampleId()).isEqualTo(42L);
    }

    @Test
    void fromStoryLike_readsRecordAccessors() {
        record MiniStory(String category, String sourceRegister, String age, String gender, String job) {}
        var hints = PersonaAutoProvisionService.fromStoryLike(
                new MiniStory("COUPLE", "NATEPAN", "20s_late", "F", "학생"));
        assertThat(hints.category()).isEqualTo("COUPLE");
        assertThat(hints.sourceRegister()).isEqualTo("NATEPAN");
        assertThat(hints.age()).isEqualTo("20s_late");
        assertThat(hints.gender()).isEqualTo("F");
        assertThat(hints.job()).isEqualTo("학생");
    }
}
