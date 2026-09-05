package com.againspring.aiuser.orchestrator.service.persona;

import com.againspring.aiuser.orchestrator.service.threadplan.CategoryMixPlanner;
import com.againspring.aiuser.orchestrator.domain.Persona;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 00-shared.md 계약 6 + 03-wp3-selection.md 완료 조건 검증.
 */
class PersonaLotteryTest {

    private final PersonaLottery lottery = new PersonaLottery();

    private static Persona persona(String id, String tier, String marital, Instant lastPostAt, Instant lastCommentAt) {
        return Persona.builder()
                .id(id)
                .archetype("test")
                .tier(tier)
                .voiceProfile(Map.of())
                .interests(Map.of())
                .biasProfile(Map.of())
                .circadian(List.of())
                .active(true)
                .marital(marital)
                .lastPostAt(lastPostAt)
                .lastCommentAt(lastCommentAt)
                .createdAt(Instant.now())
                .build();
    }

    // ---- 하드 필터 ----

    @Test
    void drawAuthorsNeverViolatesCategoryHardFilter() {
        List<Persona> pool = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            String marital = i % 2 == 0 ? "MARRIED" : "SINGLE";
            pool.add(persona("p" + i, "REGULAR", marital, null, null));
        }
        Random rng = new Random(42);
        int violations = 0;
        for (int i = 0; i < 500; i++) {
            List<Persona> drawn = lottery.drawAuthors(pool, CategoryMixPlanner.COUPLE, 1, rng);
            for (Persona p : drawn) {
                if (!CategoryMixPlanner.authorEligible(p, CategoryMixPlanner.COUPLE)) violations++;
            }
            drawn = lottery.drawAuthors(pool, CategoryMixPlanner.MARRIED, 1, rng);
            for (Persona p : drawn) {
                if (!CategoryMixPlanner.authorEligible(p, CategoryMixPlanner.MARRIED)) violations++;
            }
        }
        assertThat(violations).isZero();
    }

    @Test
    void drawCommentersNeverIncludesExcludedIds() {
        List<Persona> pool = new ArrayList<>();
        for (int i = 0; i < 30; i++) pool.add(persona("c" + i, "REGULAR", "SINGLE", null, null));
        Set<String> exclude = Set.of("c0", "c1", "c2");
        Random rng = new Random(7);
        for (int i = 0; i < 200; i++) {
            List<Persona> drawn = lottery.drawCommenters(pool, CategoryMixPlanner.WORK, exclude, 11, rng);
            assertThat(drawn.stream().map(Persona::getId)).doesNotContainAnyElementsOf(exclude);
            assertThat(drawn).doesNotHaveDuplicates();
            assertThat(drawn.size()).isLessThanOrEqualTo(11);
        }
    }

    // ---- 통계 검증 (10,000회) ----

    @Test
    void nullLastPostAtIsPickedMoreOftenThanRecentActivity() {
        Persona neverPosted = persona("never", "REGULAR", "SINGLE", null, null);
        Persona justPosted = persona("recent", "REGULAR", "SINGLE", Instant.now().minus(1, ChronoUnit.HOURS), null);
        List<Persona> pool = List.of(neverPosted, justPosted);
        Random rng = new Random(123);
        int neverWins = 0;
        int trials = 10_000;
        for (int i = 0; i < trials; i++) {
            List<Persona> drawn = lottery.drawAuthors(pool, CategoryMixPlanner.WORK, 1, rng);
            if (!drawn.isEmpty() && "never".equals(drawn.get(0).getId())) neverWins++;
        }
        // weight(never)=(1+720/24)^1.5 ≈ 172, weight(recent)=(1+1/24)^1.5 ≈ 1.06 → never
        // should win the overwhelming majority of draws.
        assertThat(neverWins).isGreaterThan((int) (trials * 0.9));
    }

    @Test
    void heavyTierIsPickedAboutThreeTimesMoreThanLightAtEqualRecency() {
        Persona heavy = persona("heavy", "HEAVY", "SINGLE", null, null);
        Persona regular = persona("regular", "REGULAR", "SINGLE", null, null);
        Persona light = persona("light", "LIGHT", "SINGLE", null, null);
        List<Persona> pool = List.of(heavy, regular, light);
        Random rng = new Random(999);
        Map<String, Integer> wins = new HashMap<>();
        int trials = 10_000;
        for (int i = 0; i < trials; i++) {
            List<Persona> drawn = lottery.drawAuthors(pool, CategoryMixPlanner.WORK, 1, rng);
            if (!drawn.isEmpty()) wins.merge(drawn.get(0).getId(), 1, Integer::sum);
        }
        double heavyShare = wins.getOrDefault("heavy", 0) / (double) trials;
        double lightShare = wins.getOrDefault("light", 0) / (double) trials;
        double ratio = heavyShare / lightShare;
        // tierW ratio is exactly 3.0 (HEAVY 3.0 / LIGHT 1.0); allow generous sampling noise.
        assertThat(ratio).isBetween(2.0, 4.2);
    }

    @Test
    void drawAuthorsIsNotDeterministicallySortedByPersonaId() {
        List<Persona> pool = new ArrayList<>();
        for (int i = 0; i < 20; i++) pool.add(persona("z" + (20 - i), "REGULAR", "SINGLE", null, null));
        Set<String> seenFirstPicks = new java.util.LinkedHashSet<>();
        Random rng = new Random(55);
        for (int i = 0; i < 50; i++) {
            List<Persona> drawn = lottery.drawAuthors(pool, CategoryMixPlanner.WORK, 1, rng);
            if (!drawn.isEmpty()) seenFirstPicks.add(drawn.get(0).getId());
        }
        // A deterministic personaId-ordered pick would always return the same one persona.
        assertThat(seenFirstPicks.size()).isGreaterThan(1);
    }

    // ---- 30일 시뮬레이션 (게이트 c 오프라인 버전) ----

    @Test
    void thirtyDaySimulationKeepsZeroPostShareLowAndTopTenShareBounded() {
        // 계약 2 쿼터: HEAVY 20 / REGULAR 80 / LIGHT 50 = 150명.
        List<Persona> pool = new ArrayList<>();
        for (int i = 0; i < 20; i++) pool.add(persona("heavy" + i, "HEAVY", "SINGLE", null, null));
        for (int i = 0; i < 80; i++) pool.add(persona("regular" + i, "REGULAR", "SINGLE", null, null));
        for (int i = 0; i < 50; i++) pool.add(persona("light" + i, "LIGHT", "SINGLE", null, null));

        Random rng = new Random(2026);
        Map<String, Integer> postCounts = new HashMap<>();
        for (Persona p : pool) postCounts.put(p.getId(), 0);

        Instant clock = Instant.now().minus(30, ChronoUnit.DAYS);
        for (int day = 0; day < 30; day++) {
            Set<String> usedToday = new java.util.LinkedHashSet<>();
            for (int slot = 0; slot < 10; slot++) {
                List<Persona> available = pool.stream()
                        .filter(p -> !usedToday.contains(p.getId()))
                        .collect(Collectors.toList());
                List<Persona> drawn = lottery.drawAuthors(available, CategoryMixPlanner.WORK, 1, rng);
                if (drawn.isEmpty()) continue;
                Persona author = drawn.get(0);
                usedToday.add(author.getId());
                postCounts.merge(author.getId(), 1, Integer::sum);
                author.setLastPostAt(clock);
            }
            // 댓글 200개도 같은 시계열에서 소비되며 last_comment_at을 갱신 — author 분포엔
            // 영향 없지만 시뮬레이션이 완료 조건의 "하루 10글·200댓글" 규모를 실제로 돈다.
            for (int c = 0; c < 200; c++) {
                List<Persona> drawnCommenters = lottery.drawCommenters(pool, CategoryMixPlanner.WORK, Set.of(), 1, rng);
                if (!drawnCommenters.isEmpty()) drawnCommenters.get(0).setLastCommentAt(clock);
            }
            clock = clock.plus(1, ChronoUnit.DAYS);
        }

        long zeroPostPersonas = postCounts.values().stream().filter(c -> c == 0).count();
        double zeroPostShare = zeroPostPersonas / (double) pool.size();

        int totalPosts = postCounts.values().stream().mapToInt(Integer::intValue).sum();
        int top10Posts = postCounts.values().stream()
                .sorted((a, b) -> Integer.compare(b, a))
                .limit(10)
                .mapToInt(Integer::intValue)
                .sum();
        double top10Share = top10Posts / (double) totalPosts;

        System.out.printf(
                "[PersonaLottery 30-day sim] totalPosts=%d zeroPostPersonas=%d/%d (%.1f%%) top10Share=%.1f%%%n",
                totalPosts, zeroPostPersonas, pool.size(), zeroPostShare * 100, top10Share * 100);

        assertThat(zeroPostShare).isLessThanOrEqualTo(0.10);
        assertThat(top10Share).isLessThan(0.25);
    }
}
