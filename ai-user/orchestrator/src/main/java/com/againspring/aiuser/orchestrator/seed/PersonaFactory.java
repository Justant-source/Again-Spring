package com.againspring.aiuser.orchestrator.seed;

import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.GenDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

/**
 * LLM으로 다양한 페르소나를 생성해 DB에 저장.
 * 기존 15개 앵커 페르소나는 유지하고 부족분만 생성(멱등).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonaFactory {

    private final LlmAiUserClient llmClient;
    private final PersonaRepository personaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final OrchestratorProperties props;
    private final ObjectMapper objectMapper;

    // 다양성 매트릭스 — 부족분 생성에 사용
    private static final String[] AGES      = {"10s","20s_early","20s_late","30s_early","30s_late","40s","50s","60s"};
    private static final String[] GENDERS   = {"M","F"};
    private static final String[] VOICES    = {"NATEPAN","BLIND","DCINSIDE","GENERAL"};
    private static final String[] POLITICS  = {"progressive","moderate","conservative"};
    private static final String[] REGIONS   = {"서울","경기","부산","대구","인천","광주","대전","기타"};
    private static final String[] JOBS      = {"직장인","주부","학생","자영업자","프리랜서","무직"};
    private static final String[] TIERS     = {"REGULAR","REGULAR","LIGHT","HEAVY"};  // 분포 가중

    private static final Random RNG = new Random();

    /**
     * 현재 페르소나 수가 target 미만이면 부족분 생성.
     * @param target 목표 페르소나 수
     */
    public void ensureCount(int target) {
        long current = personaRepository.count();
        if (current >= target) {
            log.info("PersonaFactory: already {} personas (target={}), skip", current, target);
            return;
        }
        int needed = (int)(target - current);
        log.info("PersonaFactory: generating {} new personas (current={}, target={})", needed, current, target);

        int created = 0;
        int attempts = 0;
        int maxAttempts = needed * 3;

        while (created < needed && attempts < maxAttempts) {
            attempts++;
            try {
                boolean ok = generateOne();
                if (ok) created++;
            } catch (Exception e) {
                log.warn("PersonaFactory attempt {} failed: {}", attempts, e.getMessage());
            }
        }
        log.info("PersonaFactory: created {} personas in {} attempts", created, attempts);
    }

    private boolean generateOne() throws Exception {
        // 랜덤 조합 선택
        String age      = pick(AGES);
        String gender   = pick(GENDERS);
        String voice    = pick(VOICES);
        String politics = pick(POLITICS);
        String region   = pick(REGIONS);
        String job      = pick(JOBS);
        String tier     = pick(TIERS);
        double slang    = switch (voice) {
            case "DCINSIDE" -> 0.7 + RNG.nextDouble() * 0.3;
            case "BLIND"    -> 0.2 + RNG.nextDouble() * 0.2;
            case "NATEPAN"  -> 0.4 + RNG.nextDouble() * 0.3;
            default         -> 0.3 + RNG.nextDouble() * 0.3;
        };

        // LLM으로 voice 블록 생성
        String prompt = buildPersonaPrompt(age, gender, voice, politics, region, job);
        Optional<String> result = llmClient.generatePersonaVoice(prompt);
        if (result.isEmpty() || result.get().isBlank()) return false;

        // JSON 파싱
        Map<String, Object> voiceMap = parseVoiceJson(result.get());
        if (voiceMap == null) return false;

        // 닉네임 생성 (LLM 응답에 있으면 사용, 없으면 fallback)
        String nickname = extractNickname(voiceMap, age, gender);

        // ID 생성 (UUID 32자)
        String id = UUID.randomUUID().toString().replace("-", "");

        // users 테이블 INSERT — 순번 기반 이메일 (ai-user-{NNN}@againspring.internal)
        long nextNum = personaRepository.count() + 1;
        String email = String.format("ai-user-%03d@againspring.internal", nextNum);
        String pwHash = new BCryptPasswordEncoder().encode(props.getBotPassword());
        jdbcTemplate.update(
            "INSERT INTO users (id, email, password_hash, nickname, roles, is_guest, must_change_password, created_at, updated_at) " +
            "VALUES (?,?,?,?,'[\"USER\"]',0,0,NOW(3),NOW(3))",
            id, email, pwHash, nickname
        );

        // synthetic=1 마킹 (컬럼이 없으면 무시)
        try {
            jdbcTemplate.update("UPDATE users SET synthetic=1 WHERE id=?", id);
        } catch (Exception ignored) {}

        // interests, bias, circadian 생성
        Map<String, Double> interests = buildInterests(age, politics, job);
        Map<String, Double> bias      = buildBias(politics);
        List<Double> circadian        = buildCircadian(job, age);

        // voiceProfile에 demography 추가
        voiceMap.put("age", age);
        voiceMap.put("gender", gender);
        voiceMap.put("political_orientation", politics);
        voiceMap.put("region", region);
        voiceMap.put("job", job);
        voiceMap.put("voice_type", voice);
        voiceMap.put("like_score", 0.4 + RNG.nextDouble() * 0.4);
        voiceMap.put("vote_score", 0.2 + RNG.nextDouble() * 0.4);
        voiceMap.put("formality", slang < 0.4 ? "polite" : "casual");

        // archetype 선택
        String archetype = pickArchetype(age, politics, job);

        // personas 테이블 INSERT
        Persona persona = Persona.builder()
            .id(id)
            .archetype(archetype)
            .tier(tier)
            .voiceProfile(voiceMap)
            .interests(interests)
            .biasProfile(bias)
            .circadian(circadian)
            .slangLevel(BigDecimal.valueOf(slang).setScale(2, java.math.RoundingMode.HALF_UP))
            .dailyTarget(switch (tier) { case "HEAVY" -> 10; case "REGULAR" -> 6; default -> 3; })
            .active(true)
            .createdAt(Instant.now())
            .build();
        personaRepository.save(persona);

        // YAML 파일로도 저장 (ai-user/docs/personas/profiles/ai-gen-{id8}/)
        try {
            writePersonaYaml(id, email, nickname, age, gender, region, job, politics, voice, tier,
                           slang, persona.getDailyTarget(), interests, bias, circadian, archetype, voiceMap);
        } catch (Exception e) {
            log.debug("PersonaFactory YAML write skipped: {}", e.getMessage());
        }

        log.info("PersonaFactory: created persona id={} age={} gender={} voice={} politics={}", id.substring(0,8), age, gender, voice, politics);
        return true;
    }

    private String buildPersonaPrompt(String age, String gender, String voice, String politics, String region, String job) {
        return String.format("""
한국 커뮤니티 사이트 '%s' 스타일의 사용자 voice 프로필을 JSON으로 생성하라.
사용자 특성: 연령=%s, 성별=%s, 지역=%s, 직업=%s, 정치성향=%s

반드시 아래 JSON 구조로만 응답:
{
  "nickname": "2~4글자 순수 한글 닉네임 (예: 별빛, 산호, 하늘이, 달팽이)",
  "general_style": "한 줄 스타일 묘사",
  "example_post_openers": ["게시글 첫 줄 예시1", "예시2"],
  "example_comments": ["댓글 예시1 (40자 이내)", "댓글 예시2", "댓글 예시3"],
  "example_replies": ["대댓글 예시1 (20자 이내)", "예시2"],
  "reactions": {
    "agree": ["동의 반응1", "동의 반응2"],
    "disagree": ["반대 반응1"],
    "curious": ["궁금 반응1"]
  }
}
JSON 이외의 텍스트 절대 금지. 온점(.) 금지. 쌍따옴표 안 내용에 쌍따옴표 금지.
""", voice, age, gender.equals("M") ? "남성" : "여성", region, job, politics);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseVoiceJson(String raw) {
        try {
            // JSON 블록 추출
            int start = raw.indexOf('{');
            int end   = raw.lastIndexOf('}');
            if (start < 0 || end < 0) return null;
            return objectMapper.readValue(raw.substring(start, end + 1), Map.class);
        } catch (Exception e) {
            log.debug("PersonaFactory JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    private String extractNickname(Map<String, Object> voiceMap, String age, String gender) {
        Object n = voiceMap.remove("nickname");
        if (n instanceof String s && !s.isBlank()) return s;
        // fallback
        String[] pool = {"별빛","산호","하늘","달","구름","바람","노을","새벽","이슬","숲길"};
        return pick(pool) + (RNG.nextInt(99) + 1);
    }

    private Map<String, Double> buildInterests(String age, String politics, String job) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("COUPLE",  0.3 + RNG.nextDouble() * 0.5);
        m.put("MARRIED", job.equals("주부") ? 0.6 + RNG.nextDouble() * 0.3 : 0.2 + RNG.nextDouble() * 0.4);
        m.put("FAMILY",  0.4 + RNG.nextDouble() * 0.4);
        m.put("FRIEND",  0.3 + RNG.nextDouble() * 0.4);
        m.put("WORK",    job.equals("직장인") ? 0.5 + RNG.nextDouble() * 0.4 : 0.2 + RNG.nextDouble() * 0.3);
        m.put("OTHER",   0.1 + RNG.nextDouble() * 0.2);
        return m;
    }

    private Map<String, Double> buildBias(String politics) {
        double base = switch (politics) {
            case "progressive" ->  0.3;
            case "conservative" -> -0.3;
            default -> 0.0;
        };
        Map<String, Double> m = new LinkedHashMap<>();
        for (String cat : new String[]{"COUPLE","MARRIED","FAMILY","FRIEND","WORK","OTHER"}) {
            m.put(cat, base + (RNG.nextDouble() * 0.3 - 0.15));
        }
        return m;
    }

    private List<Double> buildCircadian(String job, String age) {
        // 24-slot weights (0=midnight, 23=11pm)
        double[] w = new double[24];
        switch (job) {
            case "주부"   -> { for (int h=9;  h<=14; h++) w[h]=0.7; for (int h=19;h<=21;h++) w[h]=0.8; }
            case "직장인" -> { for (int h=7;  h<=8;  h++) w[h]=0.5; for (int h=20;h<=23;h++) w[h]=0.9; }
            case "학생"   -> { for (int h=14; h<=16; h++) w[h]=0.6; for (int h=21;h<=23;h++) w[h]=1.0; w[0]=0.7; }
            default       -> { for (int h=10; h<=22; h++) w[h]=0.4 + RNG.nextDouble()*0.4; }
        }
        List<Double> list = new ArrayList<>();
        for (double v : w) list.add(Math.min(1.0, v));
        return list;
    }

    private String pickArchetype(String age, String politics, String job) {
        String[] pool = {"couple_communication","couple_money_dating","family_care_burden",
                         "work_toxic","friend_betrayal","couple_opposite_sex_friend",
                         "family_generation_gap","work_colleague_conflict"};
        return pick(pool);
    }

    private <T> T pick(T[] arr) {
        return arr[RNG.nextInt(arr.length)];
    }

    private void writePersonaYaml(String id, String email, String nickname,
                                    String age, String gender, String region, String job,
                                    String politics, String voice, String tier, double slang,
                                    int dailyTarget, Map<String, Double> interests,
                                    Map<String, Double> bias, List<Double> circadian,
                                    String archetype, Map<String, Object> voiceMap) throws Exception {
        String shortId = email.replace("@againspring.internal", "").replace("ai-user-", "ai-user-");
        File dir = new File(props.getPersonasDir() + "/profiles/" + shortId);
        dir.mkdirs();
        new File(dir, "history").mkdirs();

        // profile.yml
        StringBuilder prof = new StringBuilder();
        prof.append("id: ").append(id).append("\n");
        prof.append("email: ").append(email).append("\n");
        prof.append("nickname: ").append(nickname).append("\n");
        prof.append("demographics:\n");
        prof.append("  age_band: ").append(age).append("\n");
        prof.append("  gender: ").append(gender).append("\n");
        prof.append("  region: ").append(region).append("\n");
        prof.append("  job: ").append(job).append("\n");
        prof.append("orientation:\n");
        prof.append("  political: ").append(politics).append("\n");
        prof.append("  political_strength: 0.5\n");
        prof.append("activity:\n");
        prof.append("  tier: ").append(tier).append("\n");
        prof.append("  daily_target: ").append(dailyTarget).append("\n");
        prof.append("  slang_level: ").append(String.format("%.2f", slang)).append("\n");
        prof.append("  voice: ").append(voice).append("\n");
        prof.append("  circadian:\n");
        for (Double v : circadian) {
            prof.append("  - ").append(String.format("%.1f", v)).append("\n");
        }
        prof.append("interests:\n");
        interests.forEach((k, v) -> prof.append("  ").append(k).append(": ").append(String.format("%.1f", v)).append("\n"));
        prof.append("bias_profile:\n");
        bias.forEach((k, v) -> prof.append("  ").append(k).append(": ").append(String.format("%.2f", v)).append("\n"));
        prof.append("archetype_preferences:\n- ").append(archetype).append("\n");

        Files.writeString(dir.toPath().resolve("profile.yml"), prof.toString());

        // voice.yml — voiceMap에서 주요 필드 추출
        StringBuilder voc = new StringBuilder();
        voc.append("persona_id: ").append(id).append("\n");
        voc.append("nickname: ").append(nickname).append("\n");
        voc.append("formality: ").append(voiceMap.getOrDefault("formality", "casual")).append("\n");
        voc.append("like_score: ").append(String.format("%.2f", ((Number)voiceMap.getOrDefault("like_score", 0.45)).doubleValue())).append("\n");
        voc.append("vote_score: ").append(String.format("%.2f", ((Number)voiceMap.getOrDefault("vote_score", 0.30)).doubleValue())).append("\n");
        voc.append("voice_type: ").append(voice).append("\n");
        voc.append("age: ").append(age).append("\n");
        voc.append("political_orientation: ").append(politics).append("\n");

        // 나머지 키는 voiceMap에서 단순 직렬화
        Set<String> skipKeys = Set.of("formality", "like_score", "vote_score", "voice_type", "age", "gender",
                                       "political_orientation", "region", "job");
        for (Map.Entry<String, Object> entry : voiceMap.entrySet()) {
            String key = entry.getKey();
            if (skipKeys.contains(key)) continue;
            Object val = entry.getValue();
            if (val instanceof String s) {
                if (s.contains("\n")) {
                    voc.append(key).append(": |\n");
                    for (String l : s.split("\n")) voc.append("  ").append(l).append("\n");
                } else {
                    voc.append(key).append(": ").append(s).append("\n");
                }
            } else if (val instanceof List<?> list) {
                voc.append(key).append(":\n");
                for (Object item : list) voc.append("  - \"").append(item).append("\"\n");
            } else if (val instanceof Map<?,?> map) {
                voc.append(key).append(":\n");
                map.forEach((k2, v2) -> {
                    if (v2 instanceof List<?> l2) {
                        voc.append("  ").append(k2).append(":\n");
                        l2.forEach(item -> voc.append("    - \"").append(item).append("\"\n"));
                    } else {
                        voc.append("  ").append(k2).append(": ").append(v2).append("\n");
                    }
                });
            } else {
                voc.append(key).append(": ").append(val).append("\n");
            }
        }
        Files.writeString(dir.toPath().resolve("voice.yml"), voc.toString());

        // history README
        Files.writeString(
            dir.toPath().resolve("history").resolve("README.md"),
            "# " + nickname + " 활동 이력\n\n" +
            "AI 유저 행동 로그는 persona-history/" + id + "/ 에 저장됩니다.\n"
        );
    }
}
