package com.againspring.aiuser.orchestrator.persona;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 00-shared.md 계약 2·3 — 150명 쿼터 그리드 + style_axes 균등 분포 검증.
 */
class PersonaQuotaPlannerTest {

    private static List<String> ids150() {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 150; i++) ids.add(String.format("p%03d", i));
        return ids;
    }

    private final PersonaQuotaPlanner planner = new PersonaQuotaPlanner();

    @Test
    void plan_assignsExactly150() {
        var result = planner.plan(ids150(), 42L);
        assertThat(result).hasSize(150);
    }

    @Test
    void plan_isDeterministicForSameSeed() {
        var a = planner.plan(ids150(), 20260905L);
        var b = planner.plan(ids150(), 20260905L);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void plan_differentSeedsCanDiffer() {
        var a = planner.plan(ids150(), 1L);
        var b = planner.plan(ids150(), 2L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void plan_genderQuotaExact() {
        var result = planner.plan(ids150(), 7L);
        Map<String, Long> counts = countBy(result.values(), PersonaQuotaPlanner.IdentityAxes::gender);
        assertThat(counts).containsEntry("M", 75L).containsEntry("F", 75L);
    }

    @Test
    void plan_ageBandQuotaExact() {
        var result = planner.plan(ids150(), 7L);
        Map<String, Long> counts = countBy(result.values(), a -> band(a.ageYears()));
        assertThat(counts).containsEntry("23-29", 60L).containsEntry("30-36", 60L).containsEntry("37-49", 30L);
    }

    @Test
    void plan_maritalTotalsExact() {
        var result = planner.plan(ids150(), 7L);
        Map<String, Long> counts = countBy(result.values(), PersonaQuotaPlanner.IdentityAxes::marital);
        long nonMarried = counts.getOrDefault("SINGLE", 0L) + counts.getOrDefault("DATING", 0L)
                + counts.getOrDefault("ENGAGED", 0L);
        assertThat(nonMarried).isEqualTo(60L);
        assertThat(counts.get("MARRIED")).isEqualTo(90L);
    }

    @Test
    void plan_marriedCrossQuotaByAgeBandExact() {
        var result = planner.plan(ids150(), 7L);
        Map<String, Long> marriedByBand = new LinkedHashMap<>();
        result.values().stream().filter(a -> "MARRIED".equals(a.marital()))
                .forEach(a -> marriedByBand.merge(band(a.ageYears()), 1L, Long::sum));
        assertThat(marriedByBand.getOrDefault("23-29", 0L)).isEqualTo(15L);
        assertThat(marriedByBand.getOrDefault("30-36", 0L)).isEqualTo(45L);
        assertThat(marriedByBand.getOrDefault("37-49", 0L)).isEqualTo(30L);
    }

    @Test
    void plan_hasKidsExactlyHalfOfMarried() {
        var result = planner.plan(ids150(), 7L);
        long married = result.values().stream().filter(a -> "MARRIED".equals(a.marital())).count();
        long withKids = result.values().stream().filter(PersonaQuotaPlanner.IdentityAxes::hasKids).count();
        assertThat(married).isEqualTo(90L);
        assertThat(withKids).isEqualTo(45L);
        // has_kids는 MARRIED에서만 참
        assertThat(result.values().stream().filter(a -> !"MARRIED".equals(a.marital()))
                .allMatch(a -> !a.hasKids())).isTrue();
    }

    @Test
    void plan_tierQuotaExact() {
        var result = planner.plan(ids150(), 7L);
        Map<String, Long> counts = countBy(result.values(), PersonaQuotaPlanner.IdentityAxes::tier);
        assertThat(counts).containsEntry("HEAVY", 20L).containsEntry("REGULAR", 80L).containsEntry("LIGHT", 50L);
    }

    @Test
    void plan_jobTypeQuotaExact() {
        var result = planner.plan(ids150(), 7L);
        Map<String, Long> counts = countBy(result.values(), PersonaQuotaPlanner.IdentityAxes::jobType);
        assertThat(counts).containsEntry("CORP_LARGE", 30L)
                .containsEntry("CORP_MID", 25L)
                .containsEntry("STARTUP", 20L)
                .containsEntry("PUBLIC", 15L)
                .containsEntry("PROFESSIONAL", 15L)
                .containsEntry("SELF_EMPLOYED", 15L)
                .containsEntry("FREELANCER", 10L)
                .containsEntry("JOBSEEKER", 10L)
                .containsEntry("PARENT_LEAVE", 10L);
        assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(150L);
    }

    @Test
    void plan_jobTypeConstraintsHold() {
        var result = planner.plan(ids150(), 7L);
        for (var e : result.entrySet()) {
            var axes = e.getValue();
            if ("JOBSEEKER".equals(axes.jobType())) {
                assertThat(axes.ageYears()).as("JOBSEEKER age " + e.getKey()).isBetween(23, 32);
            }
            if ("PROFESSIONAL".equals(axes.jobType())) {
                assertThat(axes.ageYears()).as("PROFESSIONAL age " + e.getKey()).isGreaterThanOrEqualTo(27);
            }
            if ("PARENT_LEAVE".equals(axes.jobType())) {
                assertThat(axes.marital()).as("PARENT_LEAVE marital " + e.getKey()).isEqualTo("MARRIED");
                assertThat(axes.hasKids()).as("PARENT_LEAVE hasKids " + e.getKey()).isTrue();
            }
        }
    }

    @Test
    void plan_marriedYearsWithinAgeConstraint() {
        var result = planner.plan(ids150(), 7L);
        for (var e : result.entrySet()) {
            var axes = e.getValue();
            if ("MARRIED".equals(axes.marital())) {
                assertThat(axes.marriedYears()).as("married_years present " + e.getKey()).isNotNull();
                assertThat(axes.marriedYears()).isBetween(0, 24);
                assertThat(axes.marriedYears()).isLessThanOrEqualTo(Math.max(0, axes.ageYears() - 25));
            } else {
                assertThat(axes.marriedYears()).isNull();
            }
        }
    }

    @Test
    void plan_styleAxesQuotaExact() {
        var result = planner.plan(ids150(), 7L);
        assertAxis(result, "directness", Map.of("BLUNT", 75L, "SOFT", 75L));
        assertAxis(result, "affect", Map.of("EMOTIONAL", 75L, "ANALYTIC", 75L));
        assertAxis(result, "humor", Map.of("JOKER", 75L, "SERIOUS", 75L));
        assertAxis(result, "stance", Map.of("OFFENSIVE", 75L, "DEFENSIVE", 75L));
        assertAxis(result, "length", Map.of("LONG", 75L, "SHORT", 75L));
        assertAxis(result, "speech", Map.of("BANMAL", 50L, "JONDAE", 50L, "MIXED", 50L));
        assertAxis(result, "emoticon", Map.of("NONE", 50L, "LOW", 50L, "HIGH", 50L));
        assertAxis(result, "spelling", Map.of("CLEAN", 75L, "SLOPPY", 75L));
        assertAxis(result, "linebreak", Map.of("WALL", 75L, "CHOPPED", 75L));
        assertAxis(result, "profanity", Map.of("NONE", 50L, "MILD", 50L, "HEAVY", 50L));
    }

    @Test
    void plan_noJondaeHeavyCombination() {
        var result = planner.plan(ids150(), 7L);
        for (var axes : result.values()) {
            Map<String, String> style = axes.styleAxes();
            boolean violation = "JONDAE".equals(style.get("speech")) && "HEAVY".equals(style.get("profanity"));
            assertThat(violation).as("no JONDAE+HEAVY combo").isFalse();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static String band(int age) {
        if (age <= 29) return "23-29";
        if (age <= 36) return "30-36";
        return "37-49";
    }

    private static <T> Map<String, Long> countBy(
            java.util.Collection<T> items, java.util.function.Function<T, String> keyFn) {
        return items.stream().collect(Collectors.groupingBy(keyFn, LinkedHashMap::new, Collectors.counting()));
    }

    private void assertAxis(Map<String, PersonaQuotaPlanner.IdentityAxes> result, String axis,
                             Map<String, Long> expected) {
        Map<String, Long> counts = countBy(result.values(), a -> a.styleAxes().get(axis));
        expected.forEach((k, v) -> assertThat(counts.getOrDefault(k, 0L))
                .as(axis + "=" + k).isEqualTo(v));
    }
}
