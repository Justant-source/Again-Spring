package com.againspring.aiuser.orchestrator.service.persona;

import com.againspring.aiuser.orchestrator.domain.Persona;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * WP3 — 작성자·댓글자 가중 비복원 추첨 (00-shared.md 계약 6).
 *
 * <p>{@code weight(p) = tierW(p) * (1 + hoursSinceLast(p) / 24) ^ 1.5}, tierW는
 * HEAVY=3.0 / REGULAR=1.5 / LIGHT=1.0. hoursSinceLast는 작성자 추첨엔
 * {@link Persona#getLastPostAt()}, 댓글자 추첨엔 {@link Persona#getLastCommentAt()}를
 * 쓰며 둘 다 null이면 720시간(30일)으로 취급한다.</p>
 *
 * <p>비복원 추첨은 Efraimidis-Spirakis 키 방식(각 후보에 {@code u^(1/weight)} 키를 부여하고
 * 키가 큰 순으로 상위 n개를 뽑는다)을 쓴다 — 결정론 정렬(personaId 등 보조키)로 타이브레이크
 * 하지 않는다.</p>
 */
@Component
public class PersonaLottery {

    private static final double HEAVY_WEIGHT = 3.0;
    private static final double REGULAR_WEIGHT = 1.5;
    private static final double LIGHT_WEIGHT = 1.0;
    private static final double DEFAULT_HOURS_SINCE_LAST = 720.0;

    /** 작성자 추첨: active + {@link CategoryMixPlanner#authorEligible} 통과자 중 가중 비복원 n명. */
    public List<Persona> drawAuthors(List<Persona> pool, String category, int n, Random rng) {
        if (pool == null || pool.isEmpty() || n <= 0) return List.of();
        List<Persona> eligible = new ArrayList<>();
        for (Persona p : pool) {
            if (p == null || !p.isActive()) continue;
            if (!CategoryMixPlanner.authorEligible(p, category)) continue;
            eligible.add(p);
        }
        return weightedSampleWithoutReplacement(eligible, n, rng, false);
    }

    /**
     * 댓글자 추첨: active + {@code exclude}(글 작성자·이미 뽑힌 자·B 시점 파트너 등) 제외 후
     * 가중 비복원 n명. 카테고리별 작성자 자격 제한은 댓글자에는 적용하지 않는다(계약 6).
     */
    public List<Persona> drawCommenters(List<Persona> pool, String category, Set<String> exclude, int n, Random rng) {
        if (pool == null || pool.isEmpty() || n <= 0) return List.of();
        Set<String> ex = exclude == null ? Set.of() : exclude;
        List<Persona> eligible = new ArrayList<>();
        for (Persona p : pool) {
            if (p == null || !p.isActive()) continue;
            if (p.getId() != null && ex.contains(p.getId())) continue;
            eligible.add(p);
        }
        return weightedSampleWithoutReplacement(eligible, n, rng, true);
    }

    static double weightOf(Persona p, boolean useLastComment) {
        double tierW = tierWeight(p.getTier());
        Instant last = useLastComment ? p.getLastCommentAt() : p.getLastPostAt();
        double hours = last == null ? DEFAULT_HOURS_SINCE_LAST
                : Math.max(0.0, Duration.between(last, Instant.now()).toMinutes() / 60.0);
        return tierW * Math.pow(1.0 + hours / 24.0, 1.5);
    }

    private static double tierWeight(String tier) {
        if (tier == null) return LIGHT_WEIGHT;
        return switch (tier.toUpperCase(Locale.ROOT)) {
            case "HEAVY" -> HEAVY_WEIGHT;
            case "REGULAR" -> REGULAR_WEIGHT;
            default -> LIGHT_WEIGHT;
        };
    }

    /**
     * Efraimidis-Spirakis 가중 비복원 샘플링. u~Uniform(0,1)에 대해 key=u^(1/weight)를 매기고
     * 키 내림차순 상위 n개를 취한다. 부동소수 키라 동점 처리(=결정론 정렬)가 실질적으로 없다.
     */
    private static List<Persona> weightedSampleWithoutReplacement(
            List<Persona> candidates, int n, Random rng, boolean useLastComment) {
        if (candidates.isEmpty()) return List.of();
        Random r = rng != null ? rng : new Random();
        record Keyed(double key, Persona persona) {}
        List<Keyed> keyed = new ArrayList<>(candidates.size());
        for (Persona p : candidates) {
            double weight = Math.max(1e-9, weightOf(p, useLastComment));
            double u = r.nextDouble();
            if (u <= 0.0) u = Double.MIN_VALUE;
            double key = Math.pow(u, 1.0 / weight);
            keyed.add(new Keyed(key, p));
        }
        keyed.sort((a, b) -> Double.compare(b.key(), a.key()));
        int take = Math.min(n, keyed.size());
        List<Persona> out = new ArrayList<>(take);
        for (int i = 0; i < take; i++) out.add(keyed.get(i).persona());
        return out;
    }
}
