package com.againspring.aiuser.orchestrator.seed;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PersonaDuplicateDetectorTest {

    @Test
    void distance_exactMatchIsZero() {
        var a = PersonaDuplicateDetector.Identity.of("20s_early", "F", "직장인", "NATEPAN");
        var b = PersonaDuplicateDetector.Identity.of("20s_early", "f", "직장인", "natepan");
        assertThat(PersonaDuplicateDetector.distance(a, b)).isZero();
    }

    @Test
    void distance_blankJobIsSoftOne() {
        var a = PersonaDuplicateDetector.Identity.of("30s_late", "M", "직장인", "BLIND");
        var b = PersonaDuplicateDetector.Identity.of("30s_late", "M", "", "BLIND");
        assertThat(PersonaDuplicateDetector.distance(a, b)).isEqualTo(1);
    }

    @Test
    void distance_adjacentAgeSameJobIsSoftOne() {
        var a = PersonaDuplicateDetector.Identity.of("20s_early", "F", "학생", "NATEPAN");
        var b = PersonaDuplicateDetector.Identity.of("20s_late", "F", "학생", "NATEPAN");
        assertThat(PersonaDuplicateDetector.distance(a, b)).isEqualTo(1);
    }

    @Test
    void distance_differentGenderIsNotDuplicate() {
        var a = PersonaDuplicateDetector.Identity.of("40s", "M", "주부", "NATEPAN");
        var b = PersonaDuplicateDetector.Identity.of("40s", "F", "주부", "NATEPAN");
        assertThat(PersonaDuplicateDetector.distance(a, b)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void distance_differentVoiceIsNotDuplicate() {
        var a = PersonaDuplicateDetector.Identity.of("40s", "F", "주부", "NATEPAN");
        var b = PersonaDuplicateDetector.Identity.of("40s", "F", "주부", "BLIND");
        assertThat(PersonaDuplicateDetector.distance(a, b)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void distance_differentJobIsNotDuplicate() {
        var a = PersonaDuplicateDetector.Identity.of("30s_early", "M", "직장인", "BLIND");
        var b = PersonaDuplicateDetector.Identity.of("30s_early", "M", "자영업자", "BLIND");
        assertThat(PersonaDuplicateDetector.distance(a, b)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void findNearDuplicate_respectsSoftMax() {
        var existing = List.of(
                PersonaDuplicateDetector.Identity.of("20s_early", "F", "학생", "NATEPAN"),
                PersonaDuplicateDetector.Identity.of("40s", "M", "직장인", "BLIND")
        );
        var candidate = PersonaDuplicateDetector.Identity.of("20s_late", "F", "학생", "NATEPAN");

        Optional<PersonaDuplicateDetector.Identity> soft =
                PersonaDuplicateDetector.findNearDuplicate(existing, candidate, 1);
        assertThat(soft).isPresent();
        assertThat(soft.get().age()).isEqualTo("20s_early");

        Optional<PersonaDuplicateDetector.Identity> exactOnly =
                PersonaDuplicateDetector.findNearDuplicate(existing, candidate, 0);
        assertThat(exactOnly).isEmpty();
    }

    @Test
    void normalizeGenderAliases() {
        var a = PersonaDuplicateDetector.Identity.of("50s", "여성", "주부", "NATEPAN");
        var b = PersonaDuplicateDetector.Identity.of("50s", "F", "주부", "NATEPAN");
        assertThat(PersonaDuplicateDetector.distance(a, b)).isZero();
    }
}
