package com.againspring.aiuser.orchestrator.service.persona;

import com.againspring.aiuser.orchestrator.domain.Persona;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * WP3 임시 구현 — 병합 시 WP2 버전으로 대체.
 *
 * <p>00-shared.md 계약 5 그대로의 최소 구현: WORK 35% / COUPLE 25% / FRIEND 15% / FAMILY 15% /
 * MARRIED 10%, 작성자 하드 필터, paired(양면) 허용 카테고리. WP2가 실제 소스 claim·B side
 * viability 로직을 갖고 온다 — 여기서는 {@link com.againspring.aiuser.orchestrator.service.persona.PersonaLottery}·
 * {@code NightlyScheduledFillService}·{@code PairedPostScheduler}가 컴파일/테스트되도록 계약
 * 시그니처만 제공한다.</p>
 */
public final class CategoryMixPlanner {

    public static final String WORK = "WORK";
    public static final String COUPLE = "COUPLE";
    public static final String FRIEND = "FRIEND";
    public static final String FAMILY = "FAMILY";
    public static final String MARRIED = "MARRIED";

    private CategoryMixPlanner() {}

    /** 슬롯 하나 = 카테고리 + 이 카테고리에서 양면(paired) 글이 허용되는지. */
    public record Slot(String category, boolean pairedAllowed) {}

    private record Bucket(String category, double share, boolean pairedAllowed) {}

    private static final List<Bucket> BUCKETS = List.of(
            new Bucket(WORK, 0.35, false),
            new Bucket(COUPLE, 0.25, true),
            new Bucket(FRIEND, 0.15, true),
            new Bucket(FAMILY, 0.15, false),
            new Bucket(MARRIED, 0.10, true)
    );

    /**
     * 계약 5 비율대로 {@code need}개의 슬롯을 만들고 섞는다. 최대잔여법(largest remainder
     * method)을 쓴다 — 단순 반올림은 need가 작을 때(특히 1) 마지막 버킷(MARRIED)에 나머지를
     * 몰아줘 소량 배치에서 카테고리가 왜곡된다.
     */
    public static List<Slot> plan(int need, Random rng) {
        if (need <= 0) return List.of();
        int n = BUCKETS.size();
        int[] base = new int[n];
        double[] remainder = new double[n];
        int assigned = 0;
        for (int i = 0; i < n; i++) {
            double exact = need * BUCKETS.get(i).share();
            base[i] = (int) Math.floor(exact);
            remainder[i] = exact - base[i];
            assigned += base[i];
        }
        int leftover = need - assigned;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(remainder[b], remainder[a]));
        for (int k = 0; k < leftover; k++) base[order[k % n]]++;

        List<Slot> slots = new ArrayList<>(need);
        for (int i = 0; i < n; i++) {
            Bucket b = BUCKETS.get(i);
            for (int j = 0; j < base[i]; j++) slots.add(new Slot(b.category(), b.pairedAllowed()));
        }
        Random r = rng != null ? rng : new Random();
        Collections.shuffle(slots, r);
        return slots;
    }

    /** 카테고리가 양면(paired) 글을 허용하는지. */
    public static boolean pairedAllowed(String category) {
        for (Bucket b : BUCKETS) {
            if (b.category().equalsIgnoreCase(category)) return b.pairedAllowed();
        }
        return false;
    }

    /** 계약 5 작성자(A) 하드 필터. */
    public static boolean authorEligible(Persona persona, String category) {
        if (persona == null || category == null) return false;
        String marital = persona.getMarital() == null ? "SINGLE" : persona.getMarital();
        return switch (category.toUpperCase(java.util.Locale.ROOT)) {
            case COUPLE -> !"MARRIED".equalsIgnoreCase(marital);
            case MARRIED -> "MARRIED".equalsIgnoreCase(marital);
            case WORK, FRIEND, FAMILY -> true;
            default -> true;
        };
    }
}
