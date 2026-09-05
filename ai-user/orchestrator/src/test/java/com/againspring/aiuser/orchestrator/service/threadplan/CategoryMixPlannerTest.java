package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.Persona;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** persona-diversity-v4 계약5 — 카테고리 비율·paired 허용·작성자 자격. */
class CategoryMixPlannerTest {

    @Test
    void allocatesExactRatioForNeed20() {
        Map<String, Integer> counts = CategoryMixPlanner.countsByCategory(20);
        assertThat(counts.get("WORK")).isEqualTo(7);
        assertThat(counts.get("COUPLE")).isEqualTo(5);
        assertThat(counts.get("FRIEND")).isEqualTo(3);
        assertThat(counts.get("FAMILY")).isEqualTo(3);
        assertThat(counts.get("MARRIED")).isEqualTo(2);
        assertThat(counts.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(20);
    }

    @Test
    void allocatesExactRatioForNeed100() {
        Map<String, Integer> counts = CategoryMixPlanner.countsByCategory(100);
        assertThat(counts.get("WORK")).isEqualTo(35);
        assertThat(counts.get("COUPLE")).isEqualTo(25);
        assertThat(counts.get("FRIEND")).isEqualTo(15);
        assertThat(counts.get("FAMILY")).isEqualTo(15);
        assertThat(counts.get("MARRIED")).isEqualTo(10);
        assertThat(counts.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
    }

    /** 문서 예시: need=10 → WORK 3~4·COUPLE 2~3·FRIEND 1~2·FAMILY 1~2·MARRIED 1 (largest-remainder). */
    @Test
    void allocatesWithinDocumentedRangeForNeed10() {
        Map<String, Integer> counts = CategoryMixPlanner.countsByCategory(10);
        assertThat(counts.get("WORK")).isBetween(3, 4);
        assertThat(counts.get("COUPLE")).isBetween(2, 3);
        assertThat(counts.get("FRIEND")).isBetween(1, 2);
        assertThat(counts.get("FAMILY")).isBetween(1, 2);
        assertThat(counts.get("MARRIED")).isEqualTo(1);
        assertThat(counts.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
    }

    @Test
    void planReturnsExactlyNeedSlotsMatchingCategoryCounts() {
        List<CategoryMixPlanner.Slot> slots = CategoryMixPlanner.plan(20, new Random(42));
        assertThat(slots).hasSize(20);
        Map<String, Long> byCategory = slots.stream()
                .collect(java.util.stream.Collectors.groupingBy(CategoryMixPlanner.Slot::category,
                        java.util.stream.Collectors.counting()));
        assertThat(byCategory.get("WORK")).isEqualTo(7L);
        assertThat(byCategory.get("MARRIED")).isEqualTo(2L);
        for (CategoryMixPlanner.Slot slot : slots) {
            assertThat(slot.preferredSource()).isIn("blind", "natepan");
        }
    }

    @Test
    void zeroOrNegativeNeedYieldsEmptyPlan() {
        assertThat(CategoryMixPlanner.plan(0, new Random())).isEmpty();
        assertThat(CategoryMixPlanner.plan(-5, new Random())).isEmpty();
    }

    @Test
    void pairedAllowedOnlyForCoupleMarriedFriend() {
        assertThat(CategoryMixPlanner.pairedAllowed("COUPLE")).isTrue();
        assertThat(CategoryMixPlanner.pairedAllowed("MARRIED")).isTrue();
        assertThat(CategoryMixPlanner.pairedAllowed("FRIEND")).isTrue();
        assertThat(CategoryMixPlanner.pairedAllowed("WORK")).isFalse();
        assertThat(CategoryMixPlanner.pairedAllowed("FAMILY")).isFalse();
        assertThat(CategoryMixPlanner.pairedAllowed("OTHER")).isFalse();
    }

    @Test
    void authorEligibleAppliesMaritalHardFilterForCoupleAndMarried() {
        Persona single = persona("SINGLE");
        Persona married = persona("MARRIED");

        assertThat(CategoryMixPlanner.authorEligible(single, "COUPLE")).isTrue();
        assertThat(CategoryMixPlanner.authorEligible(married, "COUPLE")).isFalse();
        assertThat(CategoryMixPlanner.authorEligible(single, "MARRIED")).isFalse();
        assertThat(CategoryMixPlanner.authorEligible(married, "MARRIED")).isTrue();
    }

    @Test
    void authorEligibleAllowsEveryoneForWorkFriendFamily() {
        Persona single = persona("SINGLE");
        Persona married = persona("MARRIED");
        for (String category : List.of("WORK", "FRIEND", "FAMILY")) {
            assertThat(CategoryMixPlanner.authorEligible(single, category)).isTrue();
            assertThat(CategoryMixPlanner.authorEligible(married, category)).isTrue();
        }
    }

    @Test
    void authorEligibleRejectsNullPersonaOrCategory() {
        assertThat(CategoryMixPlanner.authorEligible(null, "WORK")).isFalse();
        assertThat(CategoryMixPlanner.authorEligible(persona("SINGLE"), null)).isFalse();
    }

    private static Persona persona(String marital) {
        Map<String, Object> voiceProfile = new LinkedHashMap<>();
        voiceProfile.put("marital", marital);
        return Persona.builder()
                .id("p-" + marital)
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
