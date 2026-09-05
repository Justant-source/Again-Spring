package com.againspring.aiuser.orchestrator.persona;

import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaActionLog;
import com.againspring.aiuser.orchestrator.repository.PersonaActionLogRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.LlmErrorSignatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WP1 — 150명 페르소나 신원 축 재생성 오케스트레이션 (01-wp1-persona-data.md §3).
 * 흐름: {@link PersonaQuotaPlanner} → 페르소나별 llm 워커 {@code /generate/persona-profile} →
 * 응답 검증(고유성 포함) → {@code personas} UPDATE(신규 컬럼 + voice_profile 병합) →
 * {@code persona_action_log}에 {@code PROFILE_REGENERATED} 감사.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonaProfileRegenerator {

    private static final double JACCARD_REJECT_THRESHOLD = 0.30;
    private static final int MAX_ATTEMPTS_PER_PERSONA = 3;
    private static final int USED_PHRASES_WINDOW = 300;
    /** 한도 소진이 아닌 실패라도 이 횟수 연속되면 배치를 중단한다(무한 실패 반복 방지). */
    private static final int DEFAULT_MAX_CONSECUTIVE_FAILURES = 5;

    /**
     * 완료 판정 마커 — {@code voice_profile.profile_rev}. {@code style_axes IS NOT NULL}만으로는
     * "완료"를 판정할 수 없다: {@link #mergeIntoPersona}가 {@code personaRepo.save} 이전에
     * {@code style_axes}를 축(axes, 항상 결정론적으로 채워짐)과 voice_profile(LLM 응답 의존)을
     * 함께 채우더라도, 감사 기록(persona_action_log)이 별도 저장 호출이라 하나만 성공하는 상태가
     * 생길 수 있었다(오염 12명 사례, 2026-09 dev). 이제 이 마커는 §2 필수 키 검증을 통과하고
     * §4 원자적 저장(감사 포함)까지 끝난 뒤에만 찍힌다 — 재개 필터·remaining·게이트 스크립트가
     * 전부 이 마커 기준으로 통일된다.
     */
    static final String PROFILE_REV_KEY = "profile_rev";
    /**
     * 프로필 리비전. {@code PersonaQuotaPlanner}의 축 배정 알고리즘이 바뀌면 올린다 —
     * 같은 seed라도 배정 결과가 달라지므로, 옛 리비전으로 만든 프로필을 그대로 두면
     * 집단 전체의 쿼터(계약 2)가 어긋난다. 값을 올리면 재개 필터가 옛 프로필을
     * 미완료로 보고 자동으로 다시 처리한다.
     *
     * <p>v5(2026-09-05): 결혼 최소 연령 25→23세 개정으로 4단계 이후 RNG 소비 순서가 바뀌었다.</p>
     */
    static final String CURRENT_PROFILE_REV = "v5";

    /** §4 응답 스키마(01-wp1-persona-data.md §4) 필수 키 — 값이 비어 있으면 그 시도는 실패다. */
    private static final List<String> REQUIRED_RESPONSE_KEYS = List.of(
            "general_style", "lexicon", "writing_quirks", "hot_buttons", "reactions",
            "example_post_openers", "example_comments", "example_replies",
            "post_style", "comment_style", "reply_style");

    private final PersonaRepository personaRepo;
    private final PersonaQuotaPlanner quotaPlanner;
    private final PersonaProfileLlmClient llmClient;
    private final PersonaActionLogRepository actionLogRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    /** dryRun=true — LLM 호출 없이 QuotaPlanner 분포만 반환(게이트 a 검증용). */
    public Map<String, Object> dryRun(long seed) {
        List<String> fullIds = personaRepo.findActiveIdsOrderById();
        Map<String, PersonaQuotaPlanner.IdentityAxes> planMap = quotaPlanner.plan(fullIds, seed);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dryRun", true);
        out.put("totalActive", fullIds.size());
        out.put("distribution", buildDistribution(planMap));
        return out;
    }

    /**
     * @param onlyIds null/empty면 전체 활성 대상(단, force=false면 style_axes 이미 있는 행은 skip)
     */
    public Map<String, Object> regenerate(long seed, int batchSize, List<String> onlyIds, boolean force) {
        return regenerate(seed, batchSize, onlyIds, force, DEFAULT_MAX_CONSECUTIVE_FAILURES);
    }

    /**
     * @param onlyIds                null/empty면 전체 활성 대상(단, force=false면 style_axes 이미
     *                                있는 행은 skip — 재개 시 {@code only} 없이 같은 호출을 반복하면
     *                                남은 인원만 이어서 처리된다)
     * @param maxConsecutiveFailures 한도 소진 외의 사유로도 이 횟수만큼 연속 실패하면 배치를 중단한다
     */
    public Map<String, Object> regenerate(
            long seed, int batchSize, List<String> onlyIds, boolean force, int maxConsecutiveFailures) {
        int failureCap = maxConsecutiveFailures > 0 ? maxConsecutiveFailures : DEFAULT_MAX_CONSECUTIVE_FAILURES;
        List<String> fullIds = personaRepo.findActiveIdsOrderById();
        Map<String, PersonaQuotaPlanner.IdentityAxes> planMap = quotaPlanner.plan(fullIds, seed);

        List<Persona> allActive = personaRepo.findByActiveTrue();
        Map<String, Persona> byId = allActive.stream()
                .collect(Collectors.toMap(Persona::getId, p -> p, (a, b) -> a, LinkedHashMap::new));

        Set<String> onlySet = (onlyIds == null || onlyIds.isEmpty()) ? null : new HashSet<>(onlyIds);
        List<String> targets = new ArrayList<>();
        for (String id : fullIds) {
            if (!byId.containsKey(id)) continue;
            if (onlySet != null) {
                if (onlySet.contains(id)) targets.add(id);
                continue;
            }
            Persona p = byId.get(id);
            PersonaQuotaPlanner.IdentityAxes planned = planMap.get(id);
            boolean voiceMatches = planned == null || planned.voiceType() == null
                    || planned.voiceType().equals(voiceTypeOf(p));
            // voice_type이 계획과 다르면 미완료로 본다. 이 축은 크롤 예시 풀 소스를 가르므로
            // 프로필 본문과 짝이 맞아야 한다 — 값만 갈아끼우면 옛 소스로 쓴 문체가 남는다.
            if (force || !isProfileCurrent(p) || !voiceMatches) targets.add(id);
        }

        // 진행률 로그·remaining 계산의 기준선 — 루프가 personas를 변형하기 전에 현재 완료분을 먼저 센다.
        // ID 집합으로 추적하는 이유: 원자적 저장이 실패(롤백)해도 mergeIntoPersona가 이미 메모리상의
        // persona 객체(voiceProfile 등)를 변형해둔 상태라, allActive를 그대로 다시 스캔하면 실제로는
        // 커밋되지 않은 변경을 "완료"로 잘못 세게 된다 — 성공이 확정된 id만 추가해 이를 피한다.
        long totalActive = allActive.size();
        Set<String> currentIds = allActive.stream()
                .filter(PersonaProfileRegenerator::isProfileCurrent)
                .filter(p -> {
                    PersonaQuotaPlanner.IdentityAxes planned = planMap.get(p.getId());
                    return planned == null || planned.voiceType() == null
                            || planned.voiceType().equals(voiceTypeOf(p));
                })
                .map(Persona::getId)
                .collect(Collectors.toCollection(HashSet::new));
        long alreadyDoneBefore = currentIds.size();

        // 고유성 검사 기준 population: DB에 이미 있는 signature_phrases 전부 + 이번 실행에서 새로 생성되는 것
        List<Set<String>> previousPhraseSets = new ArrayList<>();
        for (Persona p : allActive) {
            Set<String> phrases = extractExistingPhrases(p);
            if (!phrases.isEmpty()) previousPhraseSets.add(phrases);
        }
        List<String> recentUsedPhrases = new ArrayList<>();

        int succeeded = 0, skipped = 0, processed = 0, consecutiveFailures = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        String haltedReason = null;

        LlmErrorSignatures errorSignatures = LlmErrorSignatures.get();

        outer:
        for (String id : targets) {
            processed++;
            Persona persona = byId.get(id);
            PersonaQuotaPlanner.IdentityAxes axes = planMap.get(id);
            if (axes == null) {
                skipped++;
                consecutiveFailures++;
                failures.add(Map.of("personaId", id, "reason", "NO_AXES_PLANNED"));
                if (consecutiveFailures >= failureCap) {
                    haltedReason = "CONSECUTIVE_FAILURES(" + consecutiveFailures + ")";
                    log.error("[PersonaProfileRegenerator] halting after {} consecutive failures at persona {}",
                            consecutiveFailures, id);
                    break;
                }
                continue;
            }
            String nickname = lookupNickname(id);
            String region = regionOf(persona);
            String voiceType = axes.voiceType() != null ? axes.voiceType() : voiceTypeOf(persona);

            Map<String, Object> accepted = null;
            String lastReason = "UNKNOWN";
            for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_PERSONA; attempt++) {
                PersonaProfileLlmClient.ProfileResult result = llmClient.generatePersonaProfile(
                        id, nickname, axes, region, voiceType, capWindow(recentUsedPhrases));
                if (!result.isSuccess()) {
                    lastReason = "LLM_CALL_FAILED";
                    String errorText = result.errorText() == null ? "" : result.errorText();
                    if (errorSignatures.containsSignature(errorText.toLowerCase(Locale.ROOT))) {
                        // 계정 한도·인증·거절 시그니처 — 재시도해도 낭비이므로 이 페르소나도, 남은
                        // 대상도 더 이상 시도하지 않고 즉시 배치를 중단한다(절대 규칙 #7).
                        haltedReason = "LLM_ERROR_SIGNATURE: " + truncate(errorText);
                        log.error("[PersonaProfileRegenerator] halting on error signature for persona {}: {}",
                                id, truncate(errorText));
                        failures.add(Map.of("personaId", id, "reason", lastReason));
                        break outer;
                    }
                    continue;
                }
                Map<String, Object> resp = result.response();
                List<String> missingKeys = findMissingRequiredKeys(resp);
                if (!missingKeys.isEmpty()) {
                    // 응답에 필수 키가 없으면 mergeIntoPersona의 putIfPresent가 옛 값을 조용히
                    // 유지한다 — 그걸 "성공"으로 세면 나이·결혼 축은 새 값인데 문체·시그니처는
                    // 옛 템플릿 그대로인 모순 상태가 생긴다(오염 12명 사례). 성공으로 세지 않고
                    // 재시도(그래도 부족하면 skip)한다.
                    lastReason = "INCOMPLETE_PROFILE(" + String.join(",", missingKeys) + ")";
                    continue;
                }
                Set<String> phraseSet = extractPhrases(resp);
                double maxJaccard = previousPhraseSets.stream()
                        .mapToDouble(prev -> jaccard(prev, phraseSet))
                        .max().orElse(0.0);
                if (maxJaccard >= JACCARD_REJECT_THRESHOLD) {
                    lastReason = "JACCARD_TOO_SIMILAR(" + String.format(Locale.ROOT, "%.2f", maxJaccard) + ")";
                    continue;
                }
                accepted = resp;
                break;
            }

            if (accepted == null) {
                skipped++;
                consecutiveFailures++;
                failures.add(Map.of("personaId", id, "reason", lastReason));
                log.warn("[PersonaProfileRegenerator] persona {} skipped after {} attempts: {}",
                        id, MAX_ATTEMPTS_PER_PERSONA, lastReason);
                if (consecutiveFailures >= failureCap) {
                    haltedReason = "CONSECUTIVE_FAILURES(" + consecutiveFailures + ")";
                    log.error("[PersonaProfileRegenerator] halting after {} consecutive failures at persona {}",
                            consecutiveFailures, id);
                    break;
                }
                continue;
            }

            boolean persisted = persistProfileAtomically(id, persona, axes, accepted, seed);
            if (!persisted) {
                skipped++;
                consecutiveFailures++;
                failures.add(Map.of("personaId", id, "reason", "PERSIST_FAILED"));
                log.error("[PersonaProfileRegenerator] persist failed (rolled back, no audit) for persona {}", id);
                if (consecutiveFailures >= failureCap) {
                    haltedReason = "CONSECUTIVE_FAILURES(" + consecutiveFailures + ")";
                    log.error("[PersonaProfileRegenerator] halting after {} consecutive failures at persona {}",
                            consecutiveFailures, id);
                    break;
                }
                continue;
            }

            currentIds.add(id);
            Set<String> newPhrases = extractPhrases(accepted);
            previousPhraseSets.add(newPhrases);
            recentUsedPhrases.addAll(0, newPhrases);
            succeeded++;
            consecutiveFailures = 0;

            if (batchSize > 0 && processed % batchSize == 0) {
                log.info("[PersonaProfileRegenerator] progress {}/{} total done (batch: succeeded={}, skipped={})",
                        alreadyDoneBefore + succeeded, totalActive, succeeded, skipped);
            }
        }

        long remaining = totalActive - currentIds.size();

        log.info("[PersonaProfileRegenerator] run finished: {}/{} total done, remaining={}, halted={}",
                alreadyDoneBefore + succeeded, totalActive, remaining, haltedReason);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dryRun", false);
        out.put("targetCount", targets.size());
        out.put("processed", processed);
        out.put("succeeded", succeeded);
        out.put("skipped", skipped);
        out.put("failures", failures);
        out.put("remaining", remaining);
        out.put("haltedReason", haltedReason);
        return out;
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    // ── 완료 판정 마커 ──────────────────────────────────────────────────

    /**
     * {@code style_axes} 유무가 아니라 {@code voice_profile.profile_rev} 마커로 완료를 판정한다.
     * {@code style_axes}는 {@link #mergeIntoPersona}가 {@code personaRepo.save} 훨씬 이전,
     * LLM 응답 완전성과 무관하게 axes(quotaPlanner 산출물)로 항상 채워지므로 이것만으론
     * "재생성 완료"를 보장하지 못한다 — 마커는 §2 필수 키 검증과 §4 원자적 저장(persona
     * save + persona_action_log 감사)이 전부 끝난 뒤에만 찍힌다.
     */
    private static boolean isProfileCurrent(Persona p) {
        if (p.getStyleAxes() == null || p.getStyleAxes().isEmpty()) return false;
        if (p.getVoiceProfile() == null) return false;
        return CURRENT_PROFILE_REV.equals(p.getVoiceProfile().get(PROFILE_REV_KEY));
    }

    // ── 응답 완전성 검증 ────────────────────────────────────────────────

    /** 필수 키가 없거나(null) "비어 있으면" 그 키 이름을 반환한다(빈 리스트=전부 있음). */
    @SuppressWarnings("unchecked")
    private static List<String> findMissingRequiredKeys(Map<String, Object> resp) {
        List<String> missing = new ArrayList<>();
        if (resp == null) {
            missing.addAll(REQUIRED_RESPONSE_KEYS);
            return missing;
        }
        for (String key : REQUIRED_RESPONSE_KEYS) {
            if (isBlankValue(resp.get(key))) missing.add(key);
        }
        Object lexiconObj = resp.get("lexicon");
        if (lexiconObj instanceof Map) {
            Object phrases = ((Map<String, Object>) lexiconObj).get("signature_phrases");
            if (isBlankValue(phrases) && !missing.contains("lexicon")) missing.add("lexicon.signature_phrases");
        }
        return missing;
    }

    private static boolean isBlankValue(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.isBlank();
        if (v instanceof Map<?, ?> m) return m.isEmpty();
        if (v instanceof List<?> l) return l.isEmpty();
        return false;
    }

    // ── 원자적 저장(프로필 병합 + persona 저장 + 감사) ──────────────────

    /**
     * {@code personaRepo.save}와 {@code persona_action_log} 감사 기록을 한 트랜잭션으로 묶는다.
     * 기존에는 두 저장이 별개 호출이라 {@code logAudit}가 예외를 삼키면(구 코드) save만 커밋되고
     * 감사가 누락되는 상태가 가능했다 — 이 클래스가 {@code @Service} 빈이라 {@code regenerate()}가
     * 이 메서드를 self-invocation으로 호출하면 {@code @Transactional} 프록시를 우회하므로,
     * {@link TransactionTemplate}으로 명시적 트랜잭션 경계를 만든다. 이 안에서 던진 예외는
     * 트랜잭션을 롤백시키고(=persona save도 함께 취소) 그대로 호출자에게 전파된다.
     */
    private boolean persistProfileAtomically(
            String personaId, Persona persona, PersonaQuotaPlanner.IdentityAxes axes,
            Map<String, Object> accepted, long seed) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                mergeIntoPersona(persona, axes, accepted);
                personaRepo.save(persona);
                logAudit(personaId, seed, axes);
            });
            return true;
        } catch (Exception e) {
            log.error("[PersonaProfileRegenerator] atomic persist/audit failed for {}: {}",
                    personaId, e.getMessage(), e);
            return false;
        }
    }

    // ── voice_profile 병합 (§3 병합 규칙) ────────────────────────────────

    @SuppressWarnings("unchecked")
    private void mergeIntoPersona(Persona persona, PersonaQuotaPlanner.IdentityAxes axes, Map<String, Object> resp) {
        persona.setAgeYears(axes.ageYears());
        persona.setGender(axes.gender());
        persona.setMarital(axes.marital());
        persona.setMarriedYears(axes.marriedYears());
        persona.setHasKids(axes.hasKids());
        persona.setJobType(axes.jobType());
        persona.setStyleAxes(axes.styleAxes());
        persona.setTier(axes.tier());

        String jobTitle = strOf(resp.get("job_title"));
        if (!jobTitle.isBlank()) persona.setJobTitle(jobTitle);

        Map<String, Object> vp = persona.getVoiceProfile() != null
                ? new LinkedHashMap<>(persona.getVoiceProfile()) : new LinkedHashMap<>();

        putIfPresent(vp, "general_style", resp.get("general_style"));
        putIfPresent(vp, "lexicon", resp.get("lexicon"));
        putIfPresent(vp, "writing_quirks", resp.get("writing_quirks"));
        putIfPresent(vp, "hot_buttons", resp.get("hot_buttons"));
        putIfPresent(vp, "reactions", resp.get("reactions"));
        putIfPresent(vp, "example_post_openers", resp.get("example_post_openers"));
        putIfPresent(vp, "example_comments", resp.get("example_comments"));
        putIfPresent(vp, "example_replies", resp.get("example_replies"));
        putIfPresent(vp, "post_style", resp.get("post_style"));
        putIfPresent(vp, "comment_style", resp.get("comment_style"));
        putIfPresent(vp, "reply_style", resp.get("reply_style"));
        // hot_topics: §3 병합 목록엔 있으나 §4 응답 스키마엔 산출 필드가 없다 — 기존 값 유지(변경 없음).

        vp.put("age", ageBand(axes.ageYears()));
        vp.put("gender", axes.gender());
        if (!jobTitle.isBlank()) vp.put("job", jobTitle);
        // region: 응답 스키마에 새 신호 없음 — 기존 값 유지(패스스루)

        Map<String, Double> interests = toDoubleMap(resp.get("interests"));
        correctInterests(interests, axes.marital());
        if (!interests.isEmpty()) {
            persona.setInterests(interests);
            vp.put("interests", interests);
        }

        String formality = switch (axes.styleAxes().getOrDefault("speech", "MIXED")) {
            case "BANMAL" -> "casual";
            case "JONDAE" -> "polite";
            default -> "casual";
        };
        vp.put("formality", formality);
        // 완료 마커 — 이 시점까지 도달했다는 건 findMissingRequiredKeys를 통과한 완전한 응답이라는
        // 뜻이다. persistProfileAtomically가 이 persona 저장 + 감사를 한 트랜잭션으로 묶으므로,
        // 이 마커가 DB에 실제로 찍혔다면 감사 행도 반드시 존재한다.
        if (axes.voiceType() != null) vp.put("voice_type", axes.voiceType());
        vp.put(PROFILE_REV_KEY, CURRENT_PROFILE_REV);
        persona.setVoiceProfile(vp);

        double slang = slangFromAxes(axes.styleAxes());
        persona.setSlangLevel(BigDecimal.valueOf(slang).setScale(2, RoundingMode.HALF_UP));
    }

    private static void putIfPresent(Map<String, Object> vp, String key, Object value) {
        if (value != null) vp.put(key, value);
    }

    private static String ageBand(int age) {
        if (age <= 29) return "20s_late";
        if (age <= 36) return "30s_early";
        if (age <= 39) return "30s_late";
        return "40s";
    }

    private static void correctInterests(Map<String, Double> interests, String marital) {
        if (interests == null || interests.isEmpty()) return;
        if (!"MARRIED".equals(marital)) {
            if (interests.getOrDefault("MARRIED", 0.0) > 0.2) interests.put("MARRIED", 0.2);
        } else {
            if (interests.getOrDefault("COUPLE", 0.0) > 0.3) interests.put("COUPLE", 0.3);
        }
    }

    private static double slangFromAxes(Map<String, String> axes) {
        double e = levelScore(axes.get("emoticon"));
        double p = levelScore(axes.get("profanity"));
        return Math.round(((e + p) / 2.0) * 100.0) / 100.0;
    }

    private static double levelScore(String v) {
        if (v == null) return 0.45;
        return switch (v) {
            case "NONE" -> 0.2;
            case "LOW", "MILD" -> 0.45;
            case "HIGH", "HEAVY" -> 0.7;
            default -> 0.45;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> toDoubleMap(Object raw) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (!(raw instanceof Map)) return out;
        ((Map<String, Object>) raw).forEach((k, v) -> {
            if (v instanceof Number n) out.put(k, n.doubleValue());
        });
        return out;
    }

    // ── 고유성(Jaccard) ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Set<String> extractPhrases(Map<String, Object> resp) {
        Object lexiconObj = resp.get("lexicon");
        if (!(lexiconObj instanceof Map)) return Set.of();
        Object phrasesObj = ((Map<String, Object>) lexiconObj).get("signature_phrases");
        if (!(phrasesObj instanceof List<?> list)) return Set.of();
        return list.stream().map(o -> String.valueOf(o).trim()).filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractExistingPhrases(Persona p) {
        if (p.getVoiceProfile() == null) return Set.of();
        Object lexiconObj = p.getVoiceProfile().get("lexicon");
        if (!(lexiconObj instanceof Map)) return Set.of();
        Object phrasesObj = ((Map<String, Object>) lexiconObj).get("signature_phrases");
        if (!(phrasesObj instanceof List<?> list)) return Set.of();
        return list.stream().map(o -> String.valueOf(o).trim()).filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    private static List<String> capWindow(List<String> phrases) {
        return phrases.size() > USED_PHRASES_WINDOW ? phrases.subList(0, USED_PHRASES_WINDOW) : phrases;
    }

    // ── 감사 로그 ────────────────────────────────────────────────────────

    /**
     * 예외를 삼키지 않는다(구 버전의 근본 원인) — {@link #persistProfileAtomically}의 트랜잭션
     * 안에서 호출되므로, 여기서 던진 예외는 같은 트랜잭션에 있는 {@code personaRepo.save}까지
     * 롤백시켜서 "축만 저장되고 감사는 없는" 상태를 원천 차단한다.
     */
    private void logAudit(String personaId, long seed, PersonaQuotaPlanner.IdentityAxes axes) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("seed", seed);
        detail.put("jobType", axes.jobType());
        detail.put("tier", axes.tier());
        actionLogRepository.save(PersonaActionLog.builder()
                .personaId(personaId)
                .actionType("PROFILE_REGENERATED")
                .targetType("PERSONA")
                .targetId(personaId)
                .usedLlm(true)
                .status("POSTED")
                .correlationId("persona-profile-regen-" + seed)
                .detail(detail)
                .createdAt(Instant.now())
                .build());
    }

    // ── 분포 요약 (dryRun 게이트 a) ──────────────────────────────────────

    private Map<String, Object> buildDistribution(Map<String, PersonaQuotaPlanner.IdentityAxes> planMap) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gender", countBy(planMap.values(), PersonaQuotaPlanner.IdentityAxes::gender));
        out.put("ageBand", countBy(planMap.values(), a -> ageQuotaBand(a.ageYears())));
        out.put("marital", countBy(planMap.values(), PersonaQuotaPlanner.IdentityAxes::marital));
        out.put("marriedByAgeBand", countBy(
                planMap.values().stream().filter(a -> "MARRIED".equals(a.marital())).toList(),
                a -> ageQuotaBand(a.ageYears())));
        out.put("hasKids", countBy(planMap.values(), a -> String.valueOf(a.hasKids())));
        out.put("jobType", countBy(planMap.values(), PersonaQuotaPlanner.IdentityAxes::jobType));
        out.put("tier", countBy(planMap.values(), PersonaQuotaPlanner.IdentityAxes::tier));

        Map<String, Object> styleAxes = new LinkedHashMap<>();
        for (String axis : List.of("directness", "affect", "humor", "stance", "length",
                "speech", "emoticon", "spelling", "linebreak", "profanity")) {
            styleAxes.put(axis, countBy(planMap.values(), a -> a.styleAxes().get(axis)));
        }
        out.put("styleAxes", styleAxes);
        return out;
    }

    private static String ageQuotaBand(int age) {
        if (age <= 29) return "23-29";
        if (age <= 36) return "30-36";
        return "37-49";
    }

    private static <T> Map<String, Long> countBy(
            java.util.Collection<T> items, java.util.function.Function<T, String> keyFn) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (T item : items) {
            out.merge(keyFn.apply(item), 1L, Long::sum);
        }
        return out;
    }

    // ── 잡다한 조회 ──────────────────────────────────────────────────────

    private String lookupNickname(String personaId) {
        try {
            List<String> rows = jdbcTemplate.queryForList(
                    "SELECT nickname FROM users WHERE id = ?", String.class, personaId);
            return rows.isEmpty() || rows.get(0) == null ? personaId : rows.get(0);
        } catch (Exception e) {
            return personaId;
        }
    }

    private static String regionOf(Persona p) {
        if (p.getVoiceProfile() == null) return "";
        Object v = p.getVoiceProfile().get("region");
        return v == null ? "" : String.valueOf(v);
    }

    private static String voiceTypeOf(Persona p) {
        if (p.getVoiceProfile() == null) return "";
        Object v = p.getVoiceProfile().get("voice_type");
        return v == null ? "" : String.valueOf(v);
    }

    private static String strOf(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
