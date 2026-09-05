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
                persona("solo", "F", 32, "SINGLE"));
        List<PersonaRelationship> existing = List.of(
                // solo(32)-m1(34) 나이차 2 ≤ 5 → FRIEND 유효, STALE 처리되지 않고 그대로 커버 유지
                PersonaRelationship.builder().personaId("solo").otherId("m1")
                        .relationType("FRIEND").status("ACTIVE").build());

        var plan = filler.plan(pool, existing, 3L);

        // solo는 이미 (여전히 유효한) FRIEND로 커버되어 있으므로 새 관계가 필요 없다
        boolean soloGetsNewRelation = plan.toCreate().stream()
                .anyMatch(r -> r.getPersonaId().equals("solo") || r.getOtherId().equals("solo"));
        assertThat(soloGetsNewRelation).isFalse();
        assertThat(plan.toInvalidate()).isEmpty();
        assertThat(plan.result().stillUncovered()).isEmpty();
    }

    // --- 재실행 안전성 (PersonaProfileRegenerator가 age_years/gender/marital을 덮어쓴 뒤 재실행) ---

    @Test
    void plan_invalidatesStaleFriendWhenAgeDriftsPastRegeneration() {
        // a-b는 재생성 전 나이로 FRIEND를 맺었으나, 재생성 후 a가 45세로 바뀌어 b(24)와 나이차가
        // 5를 넘는다 — 더 이상 유효하지 않으므로 STALE 처리되고, 각자 나이대가 맞는 새 상대
        // (a↔c, b↔d)로 재배정되어야 한다.
        List<Persona> pool = List.of(
                persona("a", "M", 45, "SINGLE"),
                persona("b", "F", 24, "SINGLE"),
                persona("c", "M", 44, "SINGLE"),
                persona("d", "F", 26, "SINGLE"));
        List<PersonaRelationship> existing = List.of(
                PersonaRelationship.builder().personaId("a").otherId("b")
                        .relationType("FRIEND").status("ACTIVE").build());

        var plan = filler.plan(pool, existing, 7L);

        assertThat(plan.toInvalidate()).hasSize(1);
        assertThat(plan.toInvalidate().get(0).getStatus()).isEqualTo("STALE");
        assertThat(plan.toInvalidate().get(0).getPersonaId()).isEqualTo("a");
        assertThat(plan.result().invalidated()).isEqualTo(1);
        // a·b 모두 stale 관계 제거 후 covered에서 빠졌다가, 나이대가 맞는 새 상대로 재배정된다
        assertThat(plan.result().stillUncovered()).isEmpty();
        boolean aGetsNewRelation = plan.toCreate().stream()
                .anyMatch(r -> r.getPersonaId().equals("a") || r.getOtherId().equals("a"));
        boolean bGetsNewRelation = plan.toCreate().stream()
                .anyMatch(r -> r.getPersonaId().equals("b") || r.getOtherId().equals("b"));
        assertThat(aGetsNewRelation).isTrue();
        assertThat(bGetsNewRelation).isTrue();
    }

    @Test
    void plan_invalidatesStaleMarriageWhenMaritalRegressesToSingle() {
        // m1-f1은 예전에 MARRIAGE였으나 재생성 후 둘 다 SINGLE로 바뀌었다 — marital 정합성이
        // 깨졌으므로 STALE 처리되어야 한다.
        List<Persona> pool = List.of(
                persona("m1", "M", 34, "SINGLE"),
                persona("f1", "F", 32, "SINGLE"));
        List<PersonaRelationship> existing = List.of(
                PersonaRelationship.builder().personaId("m1").otherId("f1")
                        .relationType("MARRIAGE").status("ACTIVE").build());

        var plan = filler.plan(pool, existing, 11L);

        assertThat(plan.toInvalidate()).hasSize(1);
        assertThat(plan.toInvalidate().get(0).getRelationType()).isEqualTo("MARRIAGE");
        assertThat(plan.result().invalidated()).isEqualTo(1);
        // 정합성 깨진 관계 제거 후에도 서로 나이차 2 ≤ 5라 FRIEND로 재커버된다
        assertThat(plan.result().stillUncovered()).isEmpty();
    }

    @Test
    void plan_reassignsMarriageAfterSubsequentRegenerationMakesPersonasMarried() {
        // 1차 실행(재생성 전, 전원 SINGLE): FRIEND만 생성됨
        List<Persona> preRegen = List.of(
                persona("m1", "M", 34, "SINGLE"), persona("f1", "F", 32, "SINGLE"),
                persona("m2", "M", 40, "SINGLE"), persona("f2", "F", 38, "SINGLE"));
        var firstPlan = filler.plan(preRegen, List.of(), 42L);
        assertThat(firstPlan.toCreate()).allMatch(r -> "FRIEND".equals(r.getRelationType()));

        // 2차 실행(재생성 후): m1/f1이 MARRIED로 바뀜. 1차에서 만든 FRIEND는 age가 그대로라
        // 여전히 유효하므로 STALE 처리되지 않고, 추가로 올바른 MARRIAGE가 생성되어야 한다.
        List<Persona> postRegen = List.of(
                persona("m1", "M", 34, "MARRIED"), persona("f1", "F", 32, "MARRIED"),
                persona("m2", "M", 40, "SINGLE"), persona("f2", "F", 38, "SINGLE"));
        var secondPlan = filler.plan(postRegen, firstPlan.toCreate(), 42L);

        boolean marriageCreated = secondPlan.toCreate().stream()
                .anyMatch(r -> "MARRIAGE".equals(r.getRelationType())
                        && Set.of(r.getPersonaId(), r.getOtherId()).equals(Set.of("m1", "f1")));
        assertThat(marriageCreated).isTrue();
        assertThat(secondPlan.result().stillUncovered()).isEmpty();
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
