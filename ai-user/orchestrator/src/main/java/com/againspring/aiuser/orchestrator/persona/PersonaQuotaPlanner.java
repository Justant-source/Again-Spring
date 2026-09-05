package com.againspring.aiuser.orchestrator.persona;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 계약 2·3 (.request/persona-diversity-v4/00-shared.md) — 활성 페르소나 id 목록 + seed로
 * 150명 신원 축을 결정론적으로 배정한다. 같은 입력(id 집합 + seed)이면 항상 같은 출력.
 *
 * <p>구현 원칙: 각 축을 "가중치 목록(150명 기준 절대값을 가중치로 사용) → 실제 n명 크기로
 * 비례 배분(최대 나머지법) → seed로 셔플 → 정렬된 id 목록과 index로 zip" 순서로 배정한다.
 * 가중치를 그대로 쓰기 때문에 n이 150이 아니어도 같은 비율로 스케일된다(n=150이면 계약의
 * 절대값과 정확히 일치).
 */
@Component
public class PersonaQuotaPlanner {

    public record IdentityAxes(
            int ageYears,
            String gender,
            String marital,
            Integer marriedYears,
            boolean hasKids,
            String jobType,
            String tier,
            Map<String, String> styleAxes
    ) {
    }

    private static final List<Integer> AGE_BAND_YOUNG = range(23, 29);   // 23~29
    private static final List<Integer> AGE_BAND_MID = range(30, 36);     // 30~36
    private static final List<Integer> AGE_BAND_OLD = range(37, 49);     // 37~49

    public Map<String, IdentityAxes> plan(List<String> personaIds, long seed) {
        List<String> sortedIds = personaIds.stream().distinct().sorted().collect(Collectors.toList());
        int n = sortedIds.size();
        Random rng = new Random(seed);
        if (n == 0) return Map.of();

        // 1) 성별 M 75 : F 75
        Map<String, String> gender = zip(sortedIds, weightedLabels(n, linked(
                "M", 75.0, "F", 75.0), rng));

        // 2) 연령대 23~29:60 · 30~36:60 · 37~49:30
        Map<String, String> ageBand = zip(sortedIds, weightedLabels(n, linked(
                "YOUNG", 60.0, "MID", 60.0, "OLD", 30.0), rng));

        Map<String, List<String>> bandGroups = new LinkedHashMap<>();
        for (String band : List.of("YOUNG", "MID", "OLD")) {
            List<String> group = sortedIds.stream().filter(id -> band.equals(ageBand.get(id))).collect(Collectors.toList());
            bandGroups.put(band, group);
        }

        // 3) 연령대 내 실제 나이 균등 배정
        Map<String, Integer> ageYears = new LinkedHashMap<>();
        assignEvenAges(bandGroups.get("YOUNG"), AGE_BAND_YOUNG, rng, ageYears);
        assignEvenAges(bandGroups.get("MID"), AGE_BAND_MID, rng, ageYears);
        assignEvenAges(bandGroups.get("OLD"), AGE_BAND_OLD, rng, ageYears);

        // 4) 연령대 내 결혼 여부 (MARRIED 15/45/30 교차 쿼터, 밴드 명목 크기 60/60/30 기준)
        Map<String, String> marital = new LinkedHashMap<>();
        assignMaritalWithinBand(bandGroups.get("YOUNG"), 60.0, 15.0, rng, marital);
        assignMaritalWithinBand(bandGroups.get("MID"), 60.0, 45.0, rng, marital);
        assignMaritalWithinBand(bandGroups.get("OLD"), 30.0, 30.0, rng, marital);

        // 5) 미혼 60명을 SINGLE/DATING/ENGAGED 균등 3분할
        List<String> nonMarried = sortedIds.stream()
                .filter(id -> "NONMARRIED".equals(marital.get(id)))
                .collect(Collectors.toList());
        List<String> singleLabels = weightedLabels(nonMarried.size(), linked(
                "SINGLE", 20.0, "DATING", 20.0, "ENGAGED", 20.0), rng);
        for (int i = 0; i < nonMarried.size(); i++) marital.put(nonMarried.get(i), singleLabels.get(i));

        // 6) MARRIED 90명 중 45명 has_kids
        List<String> married = sortedIds.stream()
                .filter(id -> "MARRIED".equals(marital.get(id)))
                .collect(Collectors.toList());
        List<String> kidsLabels = weightedLabels(married.size(), linked("YES", 45.0, "NO", 45.0), rng);
        Map<String, Boolean> hasKids = new LinkedHashMap<>();
        for (String id : sortedIds) hasKids.put(id, false);
        for (int i = 0; i < married.size(); i++) hasKids.put(married.get(i), "YES".equals(kidsLabels.get(i)));

        // 7) MARRIED married_years: 0..max(0, min(24, age-25)) 균등 (계약1) — 1..min(24,age-25) 목표(WP1 상세)와
        //    상충 시 계약1(00-shared.md) 우선, 실현 불가 구간(age<26)은 0으로 클램프.
        Map<String, Integer> marriedYears = new LinkedHashMap<>();
        for (String id : married) {
            int age = ageYears.get(id);
            int upper = Math.max(0, Math.min(24, age - 25));
            marriedYears.put(id, upper == 0 ? 0 : rng.nextInt(upper + 1));
        }

        // 8) job_type — 제약 있는 3종 먼저 배정(전용 풀), 나머지 6종은 남은 인원에 배분
        Map<String, String> jobType = assignJobTypes(sortedIds, n, ageYears, marital, hasKids, rng);

        // 9) tier: HEAVY 20 · REGULAR 80 · LIGHT 50
        Map<String, String> tier = zip(sortedIds, weightedLabels(n, linked(
                "HEAVY", 20.0, "REGULAR", 80.0, "LIGHT", 50.0), rng));

        // 10) style_axes — 계약 3, 축마다 독립 셔플
        Map<String, Map<String, String>> styleAxes = assignStyleAxes(sortedIds, n, rng);

        Map<String, IdentityAxes> out = new LinkedHashMap<>();
        for (String id : sortedIds) {
            String maritalFinal = marital.get(id);
            out.put(id, new IdentityAxes(
                    ageYears.get(id),
                    gender.get(id),
                    maritalFinal,
                    "MARRIED".equals(maritalFinal) ? marriedYears.get(id) : null,
                    Boolean.TRUE.equals(hasKids.get(id)),
                    jobType.get(id),
                    tier.get(id),
                    styleAxes.get(id)
            ));
        }
        return out;
    }

    // ── 세부 배정 헬퍼 ───────────────────────────────────────────────────

    private void assignEvenAges(List<String> group, List<Integer> ages, Random rng, Map<String, Integer> out) {
        if (group == null || group.isEmpty()) return;
        LinkedHashMap<Integer, Double> weights = new LinkedHashMap<>();
        for (Integer age : ages) weights.put(age, 1.0);
        List<Integer> labels = weightedLabels(group.size(), weights, rng);
        for (int i = 0; i < group.size(); i++) out.put(group.get(i), labels.get(i));
    }

    /**
     * @param bandNominalSize 계약상 이 밴드의 150명 기준 명목 크기(YOUNG/MID=60, OLD=30) — 실제
     *                        group.size()가 다르면 이 크기 대비 비율로 스케일된다.
     * @param marriedWeight   같은 명목 기준의 MARRIED 절대값(15/45/30)
     */
    private void assignMaritalWithinBand(List<String> group, double bandNominalSize, double marriedWeight,
                                          Random rng, Map<String, String> out) {
        if (group == null || group.isEmpty()) return;
        LinkedHashMap<String, Double> weights = linked(
                "MARRIED", marriedWeight, "NONMARRIED", Math.max(0.0, bandNominalSize - marriedWeight));
        List<String> labels = weightedLabels(group.size(), weights, rng);
        for (int i = 0; i < group.size(); i++) out.put(group.get(i), labels.get(i));
    }

    private Map<String, String> assignJobTypes(
            List<String> sortedIds, int n,
            Map<String, Integer> ageYears, Map<String, String> marital, Map<String, Boolean> hasKids,
            Random rng) {
        Map<String, String> jobType = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();

        // PARENT_LEAVE(10/150) — MARRIED + has_kids 전용 풀
        List<String> parentLeaveEligible = sortedIds.stream()
                .filter(id -> "MARRIED".equals(marital.get(id)) && Boolean.TRUE.equals(hasKids.get(id)))
                .collect(Collectors.toList());
        int parentLeaveCount = scaledCount(10, n);
        List<String> parentLeavePicked = pickShuffled(parentLeaveEligible, parentLeaveCount, rng);
        parentLeavePicked.forEach(id -> {
            jobType.put(id, "PARENT_LEAVE");
            used.add(id);
        });

        // JOBSEEKER(10/150) — 23~32세, 아직 미배정
        List<String> jobseekerEligible = sortedIds.stream()
                .filter(id -> !used.contains(id) && ageYears.get(id) >= 23 && ageYears.get(id) <= 32)
                .collect(Collectors.toList());
        int jobseekerCount = scaledCount(10, n);
        List<String> jobseekerPicked = pickShuffled(jobseekerEligible, jobseekerCount, rng);
        jobseekerPicked.forEach(id -> {
            jobType.put(id, "JOBSEEKER");
            used.add(id);
        });

        // PROFESSIONAL(15/150) — 27세 이상, 아직 미배정
        List<String> professionalEligible = sortedIds.stream()
                .filter(id -> !used.contains(id) && ageYears.get(id) >= 27)
                .collect(Collectors.toList());
        int professionalCount = scaledCount(15, n);
        List<String> professionalPicked = pickShuffled(professionalEligible, professionalCount, rng);
        professionalPicked.forEach(id -> {
            jobType.put(id, "PROFESSIONAL");
            used.add(id);
        });

        // 나머지 6종 — 무제약, 남은 인원에 비례 배분
        List<String> remaining = sortedIds.stream().filter(id -> !used.contains(id)).collect(Collectors.toList());
        List<String> remainingLabels = weightedLabels(remaining.size(), linked(
                "CORP_LARGE", 30.0, "CORP_MID", 25.0, "STARTUP", 20.0,
                "PUBLIC", 15.0, "SELF_EMPLOYED", 15.0, "FREELANCER", 10.0), rng);
        for (int i = 0; i < remaining.size(); i++) jobType.put(remaining.get(i), remainingLabels.get(i));

        return jobType;
    }

    private Map<String, Map<String, String>> assignStyleAxes(List<String> sortedIds, int n, Random rng) {
        Map<String, String> directness = zip(sortedIds, weightedLabels(n, linked("BLUNT", 75.0, "SOFT", 75.0), rng));
        Map<String, String> affect = zip(sortedIds, weightedLabels(n, linked("EMOTIONAL", 75.0, "ANALYTIC", 75.0), rng));
        Map<String, String> humor = zip(sortedIds, weightedLabels(n, linked("JOKER", 75.0, "SERIOUS", 75.0), rng));
        Map<String, String> stance = zip(sortedIds, weightedLabels(n, linked("OFFENSIVE", 75.0, "DEFENSIVE", 75.0), rng));
        Map<String, String> length = zip(sortedIds, weightedLabels(n, linked("LONG", 75.0, "SHORT", 75.0), rng));
        Map<String, String> speech = zip(sortedIds, weightedLabels(n, linked(
                "BANMAL", 50.0, "JONDAE", 50.0, "MIXED", 50.0), rng));
        Map<String, String> emoticon = zip(sortedIds, weightedLabels(n, linked(
                "NONE", 50.0, "LOW", 50.0, "HIGH", 50.0), rng));
        Map<String, String> spelling = zip(sortedIds, weightedLabels(n, linked("CLEAN", 75.0, "SLOPPY", 75.0), rng));
        Map<String, String> linebreak = zip(sortedIds, weightedLabels(n, linked("WALL", 75.0, "CHOPPED", 75.0), rng));
        Map<String, String> profanity = new LinkedHashMap<>(zip(sortedIds, weightedLabels(n, linked(
                "NONE", 50.0, "MILD", 50.0, "HEAVY", 50.0), rng)));

        // speech=JONDAE + profanity=HEAVY 조합 금지 — 쿼터를 보존하는 스왑으로 재추첨
        resolveJondaeHeavyConflict(sortedIds, speech, profanity, rng);

        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (String id : sortedIds) {
            Map<String, String> axes = new LinkedHashMap<>();
            axes.put("directness", directness.get(id));
            axes.put("affect", affect.get(id));
            axes.put("humor", humor.get(id));
            axes.put("stance", stance.get(id));
            axes.put("length", length.get(id));
            axes.put("speech", speech.get(id));
            axes.put("emoticon", emoticon.get(id));
            axes.put("spelling", spelling.get(id));
            axes.put("linebreak", linebreak.get(id));
            axes.put("profanity", profanity.get(id));
            out.put(id, axes);
        }
        return out;
    }

    /** speech[i]==JONDAE && profanity[i]==HEAVY인 i를, 안전한 j(speech!=JONDAE && profanity!=HEAVY)와 profanity를 스왑해 해소한다. */
    private void resolveJondaeHeavyConflict(List<String> sortedIds, Map<String, String> speech,
                                             Map<String, String> profanity, Random rng) {
        List<String> violators = sortedIds.stream()
                .filter(id -> "JONDAE".equals(speech.get(id)) && "HEAVY".equals(profanity.get(id)))
                .collect(Collectors.toList());
        if (violators.isEmpty()) return;

        List<String> safePool = new ArrayList<>(sortedIds.stream()
                .filter(id -> !"JONDAE".equals(speech.get(id)) && !"HEAVY".equals(profanity.get(id)))
                .toList());
        Collections.shuffle(safePool, rng);

        int cursor = 0;
        for (String violatorId : violators) {
            if (cursor >= safePool.size()) {
                // 이론상 도달 불가(150명 쿼터 마진 확보) — 안전망으로 강제 완화
                profanity.put(violatorId, "MILD");
                continue;
            }
            String partnerId = safePool.get(cursor++);
            String violatorProfanity = profanity.get(violatorId);
            String partnerProfanity = profanity.get(partnerId);
            profanity.put(violatorId, partnerProfanity);
            profanity.put(partnerId, violatorProfanity);
        }
    }

    // ── 범용 유틸 ────────────────────────────────────────────────────────

    /** count150 기준 절대값을 n명 규모로 비례 스케일(반올림). */
    private static int scaledCount(int count150, int n) {
        return (int) Math.round(count150 / 150.0 * n);
    }

    private static List<String> pickShuffled(List<String> eligible, int count, Random rng) {
        List<String> sorted = new ArrayList<>(new HashSet<>(eligible));
        Collections.sort(sorted);
        Collections.shuffle(sorted, rng);
        int take = Math.max(0, Math.min(count, sorted.size()));
        return new ArrayList<>(sorted.subList(0, take));
    }

    private static <T> Map<String, T> zip(List<String> ids, List<T> labels) {
        Map<String, T> out = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) out.put(ids.get(i), labels.get(i));
        return out;
    }

    private static LinkedHashMap<String, Double> linked(Object... kv) {
        LinkedHashMap<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Double) kv[i + 1]);
        return m;
    }

    /**
     * 가중치 맵(임의 스케일)을 n개 라벨 목록으로 비례 배분(최대 나머지법)한 뒤 seed로 셔플한다.
     * 가중치 합으로 정규화하므로 n=150이 아니어도 같은 비율을 유지한다.
     */
    private static <T> List<T> weightedLabels(int n, LinkedHashMap<T, Double> weights, Random rng) {
        if (n <= 0) return List.of();
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            List<T> fallback = new ArrayList<>(Collections.nCopies(n, weights.keySet().iterator().next()));
            return fallback;
        }
        Map<T, Double> exact = new LinkedHashMap<>();
        Map<T, Integer> floor = new LinkedHashMap<>();
        int floorSum = 0;
        for (var e : weights.entrySet()) {
            double exactCount = e.getValue() / total * n;
            exact.put(e.getKey(), exactCount);
            int f = (int) Math.floor(exactCount + 1e-9);
            floor.put(e.getKey(), f);
            floorSum += f;
        }
        int remainder = n - floorSum;
        List<T> keysByFrac = new ArrayList<>(weights.keySet());
        keysByFrac.sort((a, b) -> Double.compare(
                fractionalPart(exact.get(b), floor.get(b)), fractionalPart(exact.get(a), floor.get(a))));
        for (int i = 0; i < remainder && !keysByFrac.isEmpty(); i++) {
            floor.merge(keysByFrac.get(i % keysByFrac.size()), 1, Integer::sum);
        }
        List<T> labels = new ArrayList<>(n);
        for (var e : floor.entrySet()) {
            for (int i = 0; i < e.getValue(); i++) labels.add(e.getKey());
        }
        // 반올림 오차로 n을 살짝 넘거나 못 채우면 마지막 키로 보정
        while (labels.size() < n) labels.add(keysByFrac.isEmpty() ? weights.keySet().iterator().next() : keysByFrac.get(0));
        while (labels.size() > n) labels.remove(labels.size() - 1);
        Collections.shuffle(labels, rng);
        return labels;
    }

    private static double fractionalPart(double exact, int floor) {
        return exact - floor;
    }

    private static List<Integer> range(int fromInclusive, int toInclusive) {
        List<Integer> out = new ArrayList<>();
        for (int i = fromInclusive; i <= toInclusive; i++) out.add(i);
        return out;
    }
}
