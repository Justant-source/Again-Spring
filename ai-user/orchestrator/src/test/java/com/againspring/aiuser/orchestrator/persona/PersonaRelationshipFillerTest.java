package com.againspring.aiuser.orchestrator.persona;

import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaRelationship;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 01-wp1-persona-data.md §6 — 150명(테스트는 축소 표본) 전원 관계 ≥1 보장. */
class PersonaRelationshipFillerTest {

    private final PersonaRelationshipFiller filler = new PersonaRelationshipFiller(null, null);

    private Persona persona(String id, String gender, int age, String marital) {
        return Persona.builder()
                .id(id).archetype("general").tier("REGULAR")
                .voiceProfile(Map.of()).interests(Map.of()).biasProfile(Map.of())
                .circadian(List.of()).active(true).createdAt(Instant.now())
                .ageYears(age).gender(gender).marital(marital).jobType("CORP_LARGE")
                .build();
    }

    @Test
    void plan_everyoneGetsAtLeastOneCoveringRelationship() {
        List<Persona> pool = List.of(
                persona("m1", "M", 34, "MARRIED"), persona("f1", "F", 32, "MARRIED"),
                persona("m2", "M", 40, "MARRIED"), persona("f2", "F", 38, "MARRIED"),
                persona("m3", "M", 27, "DATING"), persona("f3", "F", 26, "DATING"),
                persona("m4", "M", 29, "ENGAGED"), persona("f4", "F", 28, "ENGAGED"),
                persona("s1", "F", 25, "SINGLE"), persona("s2", "M", 24, "SINGLE"),
                persona("s3", "F", 45, "SINGLE"), persona("s4", "M", 46, "SINGLE"));

        var plan = filler.plan(pool, List.of(), 99L);

        assertThat(plan.result().stillUncovered()).isEmpty();
        assertThat(plan.result().coveredAfter()).isEqualTo(pool.size());
    }

    @Test
    void plan_marriagePairsAreOppositeGenderAndWithinAgeDiff8() {
        List<Persona> pool = List.of(
                persona("m1", "M", 34, "MARRIED"), persona("f1", "F", 32, "MARRIED"),
                persona("m2", "M", 40, "MARRIED"), persona("f2", "F", 38, "MARRIED"));
        var plan = filler.plan(pool, List.of(), 5L);

        List<PersonaRelationship> marriages = plan.toCreate().stream()
                .filter(r -> "MARRIAGE".equals(r.getRelationType())).toList();
        assertThat(marriages).isNotEmpty();
        Map<String, Persona> byId = Map.of("m1", pool.get(0), "f1", pool.get(1), "m2", pool.get(2), "f2", pool.get(3));
        for (PersonaRelationship r : marriages) {
            Persona a = byId.get(r.getPersonaId());
            Persona b = byId.get(r.getOtherId());
            assertThat(a.getGender()).isNotEqualTo(b.getGender());
            assertThat(Math.abs(a.getAgeYears() - b.getAgeYears())).isLessThanOrEqualTo(8);
        }
    }

    @Test
    void plan_respectsExistingRelationships() {
        List<Persona> pool = List.of(
                persona("m1", "M", 34, "MARRIED"), persona("f1", "F", 32, "MARRIED"),
                persona("solo", "F", 50, "SINGLE"));
        List<PersonaRelationship> existing = List.of(
                PersonaRelationship.builder().personaId("solo").otherId("m1")
                        .relationType("FRIEND").status("ACTIVE").build());

        var plan = filler.plan(pool, existing, 3L);

        // solo는 이미 FRIEND로 커버되어 있으므로 새 관계가 필요 없다
        boolean soloGetsNewRelation = plan.toCreate().stream()
                .anyMatch(r -> r.getPersonaId().equals("solo") || r.getOtherId().equals("solo"));
        assertThat(soloGetsNewRelation).isFalse();
        assertThat(plan.result().stillUncovered()).isEmpty();
    }

    @Test
    void plan_noSelfRelationship() {
        List<Persona> pool = new ArrayList<>();
        for (int i = 0; i < 8; i++) pool.add(persona("id" + i, i % 2 == 0 ? "M" : "F", 30 + i, "SINGLE"));
        var plan = filler.plan(pool, List.of(), 11L);
        for (PersonaRelationship r : plan.toCreate()) {
            assertThat(r.getPersonaId()).isNotEqualTo(r.getOtherId());
        }
    }
}
