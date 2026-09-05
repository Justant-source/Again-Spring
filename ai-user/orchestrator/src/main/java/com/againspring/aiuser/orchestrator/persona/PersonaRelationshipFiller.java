package com.againspring.aiuser.orchestrator.persona;

import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaRelationship;
import com.againspring.aiuser.orchestrator.repository.PersonaRelationshipRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WP1 — 150명 전원 관계 ≥1 보장 (00-shared.md 계약, 01-wp1-persona-data.md §6).
 * MARRIED끼리 MARRIAGE(성별 M-F, 나이차 ≤8, 같은 지역 우선) → DATING/ENGAGED끼리 COUPLE(성별 M-F)
 * → 아직 커버 안 된 나머지 전원에 FRIEND 1~2개(같은 연령대 ±5세). 기존 관계는 유지·존중한다.
 *
 * <p><b>재실행 안전성</b> — {@code PersonaProfileRegenerator}가 age_years·gender·marital을
 * 덮어쓰기 때문에(축 재배정), 프로필 재생성 전에 만들어진 커버링 관계(FRIEND/COUPLE/MARRIAGE)가
 * 재생성 후에는 나이차·성별·marital 제약을 더 이상 만족하지 않을 수 있다. 이 클래스는 매 실행마다
 * 기존 ACTIVE 커버링 관계를 현재 페르소나 속성으로 재검증해, 더 이상 유효하지 않은 관계는
 * {@code status=STALE}로 표시하고(삭제 아님 — 감사 가능) 커버 집합·중복 방지 목록에서 제외한다.
 * 그 결과 같은 실행 안에서 그 페르소나에게 올바른 새 관계가 재배정된다. 커버링 대상이 아닌
 * 관계 유형(ACQUAINTANCE·COLLEAGUE·FAMILY 등)은 건드리지 않는다.
 */
@Slf4j
@Component
public class PersonaRelationshipFiller {

    private final PersonaRepository personaRepo;
    private final PersonaRelationshipRepository relationshipRepo;

    public PersonaRelationshipFiller(PersonaRepository personaRepo, PersonaRelationshipRepository relationshipRepo) {
        this.personaRepo = personaRepo;
        this.relationshipRepo = relationshipRepo;
    }

    private static final Set<String> COVERING_TYPES = Set.of("COUPLE", "MARRIAGE", "FRIEND");
    private static final String STALE_STATUS = "STALE";

    public record FillResult(int totalActive, int coveredBefore, int coveredAfter, int created,
                              int invalidated, List<String> stillUncovered) {
    }

    /** DB에서 활성 페르소나 + 기존 관계를 읽어 부족분을 채우고(+ 무효화된 관계 STALE 처리) 저장한다. */
    public FillResult fillAndPersist(long seed) {
        List<Persona> active = personaRepo.findByActiveTrue();
        List<PersonaRelationship> existing = relationshipRepo.findAll();
        FillPlan planResult = plan(active, existing, seed);
        List<PersonaRelationship> toSave = new ArrayList<>();
        toSave.addAll(planResult.toInvalidate());
        toSave.addAll(planResult.toCreate());
        if (!toSave.isEmpty()) {
            relationshipRepo.saveAll(toSave);
        }
        return planResult.result();
    }

    public record FillPlan(List<PersonaRelationship> toCreate, List<PersonaRelationship> toInvalidate,
                            FillResult result) {
    }

    /** 순수 계산부 — 단위 테스트가 DB 없이 검증할 수 있도록 분리. */
    public FillPlan plan(List<Persona> active, List<PersonaRelationship> existing, long seed) {
        Random rng = new Random(seed);
        Map<String, Persona> byId = active.stream().collect(Collectors.toMap(Persona::getId, p -> p, (a, b) -> a));

        Set<String> covered = new HashSet<>();
        List<Pair> existingPairs = new ArrayList<>();
        List<PersonaRelationship> toInvalidate = new ArrayList<>();
        for (PersonaRelationship r : existing) {
            Persona a = byId.get(r.getPersonaId());
            Persona b = byId.get(r.getOtherId());
            boolean bothActive = a != null && b != null;
            boolean isCoveringActive = "ACTIVE".equals(r.getStatus()) && COVERING_TYPES.contains(r.getRelationType());

            if (isCoveringActive && bothActive && !isStillValid(r.getRelationType(), a, b)) {
                // 재생성으로 age_years/gender/marital이 바뀌어 더 이상 이 관계 유형의 제약을
                // 만족하지 못한다 — STALE 처리하고 existingPairs/covered에서 제외해 같은 실행
                // 안에서 올바른 관계로 재배정될 수 있게 한다.
                r.setStatus(STALE_STATUS);
                toInvalidate.add(r);
                continue;
            }

            existingPairs.add(new Pair(r.getPersonaId(), r.getOtherId(), r.getRelationType()));
            if (isCoveringActive && bothActive) {
                covered.add(r.getPersonaId());
                covered.add(r.getOtherId());
            }
        }
        int coveredBefore = covered.size();

        List<PersonaRelationship> toCreate = new ArrayList<>();

        // 1) MARRIED끼리 MARRIAGE (나이차 ≤8, 같은 지역 우선)
        List<Persona> marriedPool = active.stream().filter(p -> "MARRIED".equals(p.getMarital())).toList();
        pairByGender(marriedPool, "MARRIAGE", 8, rng, existingPairs, toCreate, covered);

        // 2) DATING/ENGAGED끼리 COUPLE
        List<Persona> datingPool = active.stream()
                .filter(p -> "DATING".equals(p.getMarital()) || "ENGAGED".equals(p.getMarital())).toList();
        pairByGender(datingPool, "COUPLE", Integer.MAX_VALUE, rng, existingPairs, toCreate, covered);

        // 3) 나머지 전원 → FRIEND 1~2개 (같은 연령대 ±5세)
        List<Persona> shuffledActive = new ArrayList<>(active);
        Collections.shuffle(shuffledActive, rng);
        for (Persona p : shuffledActive) {
            if (covered.contains(p.getId())) continue;
            List<Persona> candidates = active.stream()
                    .filter(o -> !o.getId().equals(p.getId()))
                    .filter(o -> Math.abs(o.getAgeYears() - p.getAgeYears()) <= 5)
                    .filter(o -> !relationExists(existingPairs, toCreate, p.getId(), o.getId(), "FRIEND"))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(candidates, rng);
            int friendTarget = 1 + rng.nextInt(2); // 1~2
            int made = 0;
            for (Persona cand : candidates) {
                if (made >= friendTarget) break;
                toCreate.add(newRelationship(p.getId(), cand.getId(), "FRIEND", "0.50"));
                existingPairs.add(new Pair(p.getId(), cand.getId(), "FRIEND"));
                covered.add(p.getId());
                covered.add(cand.getId());
                made++;
            }
        }

        List<String> stillUncovered = active.stream().map(Persona::getId)
                .filter(id -> !covered.contains(id)).toList();

        return new FillPlan(toCreate, toInvalidate, new FillResult(active.size(), coveredBefore, covered.size(),
                toCreate.size(), toInvalidate.size(), stillUncovered));
    }

    /**
     * 기존 커버링 관계가 현재(=최신 재생성 반영) 페르소나 속성으로도 여전히 유효한지 판정한다.
     * MARRIAGE: 성별 M-F + 나이차 ≤8 + 양측 marital=MARRIED.
     * COUPLE: 성별 M-F + 양측 marital ∈ {DATING, ENGAGED}.
     * FRIEND: 나이차 ≤5 (marital 무관).
     */
    private static boolean isStillValid(String type, Persona a, Persona b) {
        return switch (type) {
            case "MARRIAGE" -> !Objects.equals(a.getGender(), b.getGender())
                    && Math.abs(a.getAgeYears() - b.getAgeYears()) <= 8
                    && "MARRIED".equals(a.getMarital()) && "MARRIED".equals(b.getMarital());
            case "COUPLE" -> !Objects.equals(a.getGender(), b.getGender())
                    && isCoupleMarital(a.getMarital()) && isCoupleMarital(b.getMarital());
            case "FRIEND" -> Math.abs(a.getAgeYears() - b.getAgeYears()) <= 5;
            default -> true;
        };
    }

    private static boolean isCoupleMarital(String marital) {
        return "DATING".equals(marital) || "ENGAGED".equals(marital);
    }

    private void pairByGender(List<Persona> pool, String relType, int maxAgeDiff, Random rng,
                               List<Pair> existingPairs, List<PersonaRelationship> toCreate, Set<String> covered) {
        List<Persona> males = new ArrayList<>(pool.stream().filter(p -> "M".equals(p.getGender())).toList());
        List<Persona> females = new ArrayList<>(pool.stream().filter(p -> "F".equals(p.getGender())).toList());
        Collections.shuffle(males, rng);
        Collections.shuffle(females, rng);

        Set<String> usedThisType = new HashSet<>();
        for (Pair existingPair : existingPairs) {
            if (relType.equals(existingPair.type())) {
                usedThisType.add(existingPair.a());
                usedThisType.add(existingPair.b());
            }
        }

        for (Persona m : males) {
            if (usedThisType.contains(m.getId())) continue;
            Persona best = null;
            int bestScore = Integer.MAX_VALUE;
            for (Persona f : females) {
                if (usedThisType.contains(f.getId())) continue;
                int ageDiff = Math.abs(m.getAgeYears() - f.getAgeYears());
                if (ageDiff > maxAgeDiff) continue;
                boolean sameRegion = Objects.equals(regionOf(m), regionOf(f)) && regionOf(m) != null;
                int score = ageDiff - (sameRegion ? 1000 : 0);
                if (score < bestScore) {
                    bestScore = score;
                    best = f;
                }
            }
            if (best != null) {
                toCreate.add(newRelationship(m.getId(), best.getId(), relType, "0.70"));
                existingPairs.add(new Pair(m.getId(), best.getId(), relType));
                usedThisType.add(m.getId());
                usedThisType.add(best.getId());
                covered.add(m.getId());
                covered.add(best.getId());
            }
        }
    }

    private static boolean relationExists(List<Pair> existingPairs, List<PersonaRelationship> toCreate,
                                           String a, String b, String type) {
        for (Pair p : existingPairs) {
            if (p.type().equals(type) && ((p.a().equals(a) && p.b().equals(b)) || (p.a().equals(b) && p.b().equals(a)))) {
                return true;
            }
        }
        for (PersonaRelationship r : toCreate) {
            if (r.getRelationType().equals(type)
                    && ((r.getPersonaId().equals(a) && r.getOtherId().equals(b))
                        || (r.getPersonaId().equals(b) && r.getOtherId().equals(a)))) {
                return true;
            }
        }
        return false;
    }

    private static PersonaRelationship newRelationship(String personaId, String otherId, String type, String closeness) {
        return PersonaRelationship.builder()
                .personaId(personaId)
                .otherId(otherId)
                .relationType(type)
                .closeness(new BigDecimal(closeness))
                .status("ACTIVE")
                .build();
    }

    private static String regionOf(Persona p) {
        if (p.getVoiceProfile() == null) return null;
        Object v = p.getVoiceProfile().get("region");
        return v == null ? null : String.valueOf(v).trim();
    }

    private record Pair(String a, String b, String type) {
    }
}
