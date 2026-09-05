package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.Persona;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * persona-diversity-v4 계약5 — 카테고리 비율과 시점(A/B) 제한.
 * {@link SourceMixPlanner}의 voice_type 매칭 대신, 광장 카테고리 비율 기반으로 슬롯을 뽑는다.
 * (SourceMixPlanner는 {@code PairedPostScheduler}·{@code NightlyScheduledFillService}가 아직
 * 참조하고 있어 이 브랜치에서는 남겨둔다 — WP3가 호출부를 이 클래스로 옮긴 뒤 정리 대상.)
 */
public final class CategoryMixPlanner {

    public static final String SOURCE_BLIND = "blind";
    public static final String SOURCE_NATEPAN = "natepan";

    public static final String WORK = "WORK";
    public static final String COUPLE = "COUPLE";
    public static final String FRIEND = "FRIEND";
    public static final String FAMILY = "FAMILY";
    public static final String MARRIED = "MARRIED";

    /** 계약5 — 광장 카테고리 비율. 순서가 largest-remainder 동점 처리 시 우선순위를 겸한다. */
    private static final List<String> CATEGORIES = List.of(WORK, COUPLE, FRIEND, FAMILY, MARRIED);
    private static final Map<String, Double> RATIO = Map.of(
            WORK, 0.35, COUPLE, 0.25, FRIEND, 0.15, FAMILY, 0.15, MARRIED, 0.10);

    private CategoryMixPlanner() {}

    /** 계약5 카테고리 + paired 허용 여부 + 소스 선호(blind/natepan 무작위 50:50). */
    public record Slot(String category, boolean pairedAllowed, String preferredSource) {}

    /**
     * {@code need}개의 슬롯을 계약5 비율(largest remainder, 누적 오차 최소화)로 배정한다.
     * 예: need=10 → WORK 4·COUPLE 3·FRIEND 1·FAMILY 1·MARRIED 1(동점 우선순위는 CATEGORIES 순서).
     */
    public static List<Slot> plan(int need, Random rng) {
        if (need <= 0) return List.of();
        Random r = rng != null ? rng : new Random();
        List<String> categories = allocateCategories(need);
        Collections.shuffle(categories, r);
        List<Slot> slots = new ArrayList<>(need);
        for (String category : categories) {
            String preferredSource = r.nextBoolean() ? SOURCE_BLIND : SOURCE_NATEPAN;
            slots.add(new Slot(category, pairedAllowed(category), preferredSource));
        }
        return slots;
    }

    /** 계약5 — paired(양면) 글은 B 허용 카테고리(COUPLE·MARRIED·FRIEND)에서만. */
    public static boolean pairedAllowed(String category) {
        return COUPLE.equals(category) || MARRIED.equals(category) || FRIEND.equals(category);
    }

    /**
     * 계약5 하드 필터 — 작성자(A) 자격.
     * WORK/FRIEND/FAMILY = 전원(FAMILY의 시부모·처가 세부 제한은 스켈레톤/스토리 레벨에서 처리 —
     * 이 메서드는 persona×category 레벨 자격만 본다).
     * COUPLE = 미혼(marital != MARRIED). MARRIED = 기혼(marital == MARRIED).
     */
    public static boolean authorEligible(Persona p, String category) {
        if (p == null || category == null) return false;
        return switch (category) {
            case COUPLE -> !PersonaMaritalReader.isMarried(p);
            case MARRIED -> PersonaMaritalReader.isMarried(p);
            case WORK, FRIEND, FAMILY -> true;
            default -> true;
        };
    }

    /** Largest-remainder 배정 — CATEGORIES 순서 고정, 동점은 그 순서로 우선 배정. */
    static List<String> allocateCategories(int need) {
        int n = CATEGORIES.size();
        double[] raw = new double[n];
        int[] floor = new int[n];
        int usedSum = 0;
        for (int i = 0; i < n; i++) {
            raw[i] = need * RATIO.get(CATEGORIES.get(i));
            floor[i] = (int) Math.floor(raw[i]);
            usedSum += floor[i];
        }
        int remainder = need - usedSum;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        // 내림차순 소수부, 동점은 원래 인덱스(=CATEGORIES 순서) 유지 — Arrays.sort는 안정 정렬.
        final double[] fraction = new double[n];
        for (int i = 0; i < n; i++) fraction[i] = raw[i] - floor[i];
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(fraction[b], fraction[a]));
        for (int k = 0; k < remainder; k++) floor[idx[k]]++;

        List<String> out = new ArrayList<>(need);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < floor[i]; j++) out.add(CATEGORIES.get(i));
        }
        return out;
    }

    /** 테스트/디버그용 — 카테고리별 배정 개수 맵. */
    static Map<String, Integer> countsByCategory(int need) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String c : CATEGORIES) counts.put(c, 0);
        for (String c : allocateCategories(need)) counts.merge(c, 1, Integer::sum);
        return counts;
    }
}
