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
import java.util.Locale;

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
    // WP1B: register 단일화 — 허용 voice_type = NATEPAN · BLIND 뿐 (§16.1B / §16.7)
    // Soft target ratio 3:1 (crawl budget). Hard quota 아님.
    private static final String[] VOICES = {"NATEPAN", "NATEPAN", "NATEPAN", "BLIND"};
    private static final String[] POLITICS  = {"progressive","moderate","conservative"};
    private static final String[] REGIONS   = {"서울","경기","부산","대구","인천","광주","대전","기타"};
    private static final String[] JOBS      = {"직장인","주부","학생","자영업자","프리랜서","무직"};
    private static final String[] TIERS     = {"REGULAR","REGULAR","LIGHT","HEAVY"};  // 분포 가중

    private static final Random RNG = new Random();

    private static final Set<String> ALLOWED_STORY_VOICES = Set.of("NATEPAN", "BLIND");
    private static final Set<String> CATEGORIES = Set.of(
            "COUPLE", "MARRIED", "FRIEND", "FAMILY", "WORK", "OTHER");

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
                Optional<Persona> ok = generateOne(null, null, Map.of());
                if (ok.isPresent()) created++;
            } catch (Exception e) {
                log.warn("PersonaFactory attempt {} failed: {}", attempts, e.getMessage());
            }
        }
        log.info("PersonaFactory: created {} personas in {} attempts", created, attempts);
    }

    /**
     * WP3 minimal auto-persona: create one active persona for a story match miss.
     * voice_type is forced to NATEPAN|BLIND; interests biased toward {@code category}.
     *
     * @param register NATEPAN|BLIND (invalid → soft 3:1 pick)
     * @param category 6광장 category (COUPLE/…/OTHER)
     * @param identityHints optional age/gender/job/region/politics keys (string values)
     */
    public Optional<Persona> createForStory(
            String register, String category, Map<String, String> identityHints) {
        try {
            return generateOne(register, category, identityHints != null ? identityHints : Map.of());
        } catch (Exception e) {
            log.warn("PersonaFactory.createForStory failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @param forcedRegister null → random NATEPAN/BLIND mix; else clamped to allowed set
     * @param categoryBias null → default interest distribution; else boost that category
     * @param hints age/gender/job/region/politics overrides when non-blank
     */
    private Optional<Persona> generateOne(
            String forcedRegister, String categoryBias, Map<String, String> hints) throws Exception {
        String age = firstNonBlank(hints.get("age"), pick(AGES));
        String gender = firstNonBlank(hints.get("gender"), pick(GENDERS));
        if ("male".equalsIgnoreCase(gender) || "남".equals(gender) || "남성".equals(gender)) gender = "M";
        if ("female".equalsIgnoreCase(gender) || "여".equals(gender) || "여성".equals(gender)) gender = "F";
        if (!gender.equals("M") && !gender.equals("F")) gender = pick(GENDERS);

        String voice = normalizeStoryVoice(forcedRegister);
        if (voice == null) voice = pick(VOICES);

        String politics = firstNonBlank(hints.get("politics"), pick(POLITICS));
        String region = firstNonBlank(hints.get("region"), pick(REGIONS));
        String job = firstNonBlank(hints.get("job"), pick(JOBS));
        job = coerceJobToAge(age, job);

        // voice별 HEAVY≥1 보장: 이 voice에 HEAVY가 없으면 tier=HEAVY로 강제
        String tier = pick(TIERS);
        try {
            Long heavyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM personas WHERE tier='HEAVY' AND JSON_EXTRACT(voice_profile,'$.voice_type')=?",
                Long.class, voice);
            if (heavyCount != null && heavyCount == 0) {
                tier = "HEAVY";
                log.info("PersonaFactory: forcing HEAVY for {} (no HEAVY exists yet)", voice);
            }
        } catch (Exception e) {
            log.debug("PersonaFactory HEAVY check failed, using random tier: {}", e.getMessage());
        }
        double slang    = switch (voice) {
            case "BLIND"   -> 0.2 + RNG.nextDouble() * 0.2;
            case "NATEPAN" -> 0.4 + RNG.nextDouble() * 0.25;  // 사연=존댓말, 댓글=혼용
            default        -> 0.3 + RNG.nextDouble() * 0.25;
        };

        // LLM으로 voice 블록 생성
        String prompt = buildPersonaPrompt(age, gender, voice, politics, region, job);
        Optional<String> result = llmClient.generatePersonaVoice(prompt);
        if (result.isEmpty() || result.get().isBlank()) return Optional.empty();

        // JSON 파싱
        Map<String, Object> voiceMap = parseVoiceJson(result.get());
        if (voiceMap == null) return Optional.empty();

        // 닉네임 생성 (LLM 응답에 있으면 사용, 없으면 fallback)
        String nickname = extractNickname(voiceMap, age, gender);

        // ID 생성 (UUID 32자)
        String id = UUID.randomUUID().toString().replace("-", "");

        // users 테이블 INSERT — synthetic=1 포함, 충돌 시 suffix 재계산 후 재시도
        long nextNum = personaRepository.count() + 1;
        String email = String.format("ai-user-%03d@againspring.internal", nextNum);
        String pwHash = new BCryptPasswordEncoder().encode(props.getBotPassword());
        // synthetic=1 컬럼 포함 (V59 이후). 컬럼 없으면 fallback INSERT 사용.
        boolean inserted = false;
        for (int attempt = 0; attempt < 5 && !inserted; attempt++) {
            if (attempt > 0) {
                // email 중복(삭제/동시성) 시 MAX suffix 기반 재계산
                try {
                    Long maxSuffix = jdbcTemplate.queryForObject(
                        "SELECT MAX(CAST(REGEXP_REPLACE(email, '[^0-9]', '') AS UNSIGNED)) " +
                        "FROM users WHERE email LIKE 'ai-user-%@againspring.internal'", Long.class);
                    nextNum = (maxSuffix != null ? maxSuffix : 0L) + 1;
                    email = String.format("ai-user-%03d@againspring.internal", nextNum);
                } catch (Exception ignored) { nextNum++; email = String.format("ai-user-%03d@againspring.internal", nextNum); }
            }
            try {
                jdbcTemplate.update(
                    "INSERT INTO users (id, email, password_hash, nickname, roles, is_guest, must_change_password, synthetic, created_at, updated_at) " +
                    "VALUES (?,?,?,?,'[\"USER\"]',0,0,1,NOW(3),NOW(3))",
                    id, email, pwHash, nickname);
                inserted = true;
            } catch (org.springframework.dao.DuplicateKeyException dke) {
                log.debug("Email {} already exists, retrying with next suffix", email);
            } catch (Exception e) {
                // synthetic 컬럼 없는 경우(V59 미적용) — 컬럼 없이 삽입
                try {
                    jdbcTemplate.update(
                        "INSERT INTO users (id, email, password_hash, nickname, roles, is_guest, must_change_password, created_at, updated_at) " +
                        "VALUES (?,?,?,?,'[\"USER\"]',0,0,NOW(3),NOW(3))",
                        id, email, pwHash, nickname);
                    inserted = true;
                } catch (Exception e2) { log.warn("PersonaFactory insert failed: {}", e2.getMessage()); break; }
            }
        }
        if (!inserted) { log.error("PersonaFactory: failed to insert user after 5 attempts"); return Optional.empty(); }

        // interests, bias, circadian 생성
        Map<String, Double> interests = buildInterests(age, politics, job);
        biasInterestsToCategory(interests, categoryBias);
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

        // formality 결정: 반말 우세 원칙. 특정 Voice만 명시적으로 존댓말.
        String formality = switch (voice) {
            case "CLIEN"  -> "polite";                           // 논리적/정중한 voice
            case "NATEPAN" -> RNG.nextDouble() < 0.5 ? "polite" : "casual";  // 50% 확률로 혼용
            default -> slang < 0.25 ? "polite" : "casual";       // 나머지: slang < 0.25일 때만 polite
        };
        voiceMap.put("formality", formality);

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
        return Optional.of(persona);
    }

    /** NATEPAN|BLIND only; blank/invalid → null (caller picks soft mix). */
    public static String normalizeStoryVoice(String register) {
        if (register == null || register.isBlank()) return null;
        String v = register.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_STORY_VOICES.contains(v) ? v : null;
    }

    static void biasInterestsToCategory(Map<String, Double> interests, String category) {
        if (interests == null || category == null || category.isBlank()) return;
        String cat = category.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(cat)) return;
        double boosted = Math.max(interests.getOrDefault(cat, 0.0), 0.75 + RNG.nextDouble() * 0.2);
        interests.put(cat, Math.min(1.0, boosted));
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred.trim();
        return fallback;
    }

    private String buildPersonaPrompt(String age, String gender, String voice, String politics, String region, String job) {
        // voice별 커뮤니티 특성 가이드 (LLM general_style 품질 향상)
        String voiceGuide = switch (voice) {
            case "NATEPAN"  -> "따뜻하고 공감적인 사연 커뮤니티. 감정을 길게 풀어쓰는 서술형. 존댓말·반말 혼용. 자기고백형 갈등 서술.";
            case "DCINSIDE" -> "직설적이고 속어 자유. 초성체(ㄹㅇ/ㅇㅈ/ㄷㄷ) 사용. 짧고 임팩트 있는 반응. 솔직한 비판과 공감 혼재.";
            case "BLIND"    -> "직장인 커뮤니티. 냉정하고 현실적. 감정보다 상황 분석 우선. 쓴소리를 사실처럼 말함.";
            case "GENERAL"  -> "범용 인터넷 사용자. 중립적이고 무난한 표현. 표준 맞춤법 위주. 특정 커뮤니티 색채 없음.";
            case "FMKOREA"  -> "남초 커뮤니티. 초성체+신조어 사용. 유머와 직설적 반응 혼합. 밈 표현 자유.";
            case "RULIWEB"  -> "게임·만화 주제 커뮤니티. 인터넷 밈과 팬덤 용어 사용. 유쾌한 반응 위주.";
            case "THEQOO"   -> "여초 커뮤니티(더쿠). 감성·공감 중심. 헐·ㅠㅠ·당 자유사용. 짧은 문장 단위로 감정 표현. 연애·인간관계 서사 위주.";
            case "ARCALIVE" -> "Z세대 초성체·신조어 최다. 빠르고 가볍게 반응. 밈·짤 문화 흡수. 어쩔티비·ㄱㄱ 등 현재 유행어.";
            case "INVEN"    -> "게임 전문 커뮤니티. 하드코어 플레이어 어체. 논리적 분석과 강한 의견. 게임 전문용어·영어 약어 자연스럽게 사용.";
            case "MLBPARK"  -> "스포츠·시사 남초 커뮤니티. 직설적이나 비교적 점잖은 어체. 사실 기반 논쟁 스타일.";
            case "PPOMPPU"  -> "쇼핑·일상 주제 커뮤니티. 친근하고 정보 공유 지향. 실용적이고 긍정적 톤. 후기·경험 공유 형식.";
            case "CLIEN"    -> "IT 전문가·블루슈머 커뮤니티. 논리적이고 정중한 문체. 맞춤법 정확, 문어체 혼용. 근거 중심 의견 제시.";
            default         -> "";
        };

        return String.format("""
한국 커뮤니티 '%s' 풍의 synthetic 사용자 voice 프로필을 JSON으로 생성하라.
커뮤니티 특성: %s
사용자 특성: 연령=%s, 성별=%s, 지역=%s, 직업=%s, 정치성향=%s
실존 인물·실사용자 흉내 금지. 내부 synthetic 페르소나용 말투 패턴만 설계.

general_style은 위 커뮤니티 특성과 사용자 특성을 반영한 한 줄 묘사여야 함.
예시 (THEQOO, 20대 초반 여성): "더쿠의 20대 초반 여성 톤. 연애 불안·설렘 표현 빈번. 헐·ㅠㅠ·당 자유사용. 짧은 구어체."
예시 (CLIEN, 30대 남성 직장인): "클리앙의 30대 IT 직장인 톤. 논리적·정중한 문체. 근거 중심 의견 제시. 맞춤법 정확."

반드시 아래 JSON 구조로만 응답 (닉네임은 시스템이 자동 배정하므로 포함하지 말 것):
{
  "general_style": "한 줄 스타일 묘사 (커뮤니티 특성 반영 필수)",
  "example_post_openers": ["게시글 첫 줄 예시1", "예시2"],
  "example_comments": ["댓글 예시1 (40자 이내)", "댓글 예시2", "댓글 예시3"],
  "example_replies": ["대댓글 예시1 (20자 이내)", "예시2"],
  "reactions": {
    "agree": ["동의 반응1", "동의 반응2"],
    "disagree": ["반대 반응1"],
    "curious": ["궁금 반응1"]
  },
  "lexicon": {
    "signature_phrases": ["이 사람이 자주 쓰는 표현 5~6개"],
    "typing_habit": "타이핑 습관 1줄 (이모지, 줄바꿈, 신조어 사용 경향)"
  },
  "writing_quirks": {
    "features": "커뮤니티별 문체 구조 제약 1줄 (예: 짧은 문장·구어체·감정 중심·공감 요청형). 없으면 빈 문자열.",
    "spelling_level": "low|mid|high",
    "consistent_errors": ["이 사람 고정 오류 0~3개, 없으면 빈 배열"],
    "mobile_typos": true또는false
  },
  "hot_buttons": {
    "triggers": ["발끈 포인트 3개"],
    "soft_spots": ["공감 주제 1~2개"],
    "upvote_when": "좋아요 기준 1줄"
  }
}
JSON 이외의 텍스트 절대 금지. 온점(.) 금지. 쌍따옴표 안 내용에 쌍따옴표 금지.
생성하는 example_comments, example_replies, example_post_openers의 모든 문장 끝에도 온점을 붙이지 마라.
또한 간접화법 따옴표("", 역슬래시 따옴표 포함)를 이 예시들에 삽입하지 마라.
반말이 기본이며, 존댓말은 명시적으로 지정된 voice에서만 사용하라.
""", voice, voiceGuide, age, gender.equals("M") ? "남성" : "여성", region, job, politics);
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

    /** 닉네임 = 4스타일 혼합 생성기(꾸밈말+동물·영어숫자·바코드·보배드림 = 50:20:20:10). 중복 시 새 조합 재시도. */
    private String extractNickname(Map<String, Object> voiceMap, String age, String gender) {
        voiceMap.remove("nickname"); // LLM이 닉네임을 줘도 무시 — 시스템 생성기 사용
        try {
            for (int i = 0; i < 25; i++) {
                String candidate = PersonaNicknameGenerator.generate(RNG);
                if (!nicknameExists(candidate)) return candidate;
            }
        } catch (Exception ignored) {}
        // 조회 실패·충돌 폴백
        return PersonaNicknameGenerator.generate(RNG) + (RNG.nextInt(89) + 10);
    }

    private boolean nicknameExists(String nickname) {
        Integer c = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE nickname = ?", Integer.class, nickname);
        return c != null && c > 0;
    }

    /** Voice 타입별 닉네임 풀 (미래 확장용) */
    private String generateNicknameByVoice(String voice) {
        String[] pool = switch (voice) {
            case "DCINSIDE","FMKOREA","ARCALIVE" -> new String[]{"어둠의세력","철갑","야밤","급식왕","드립왕","새벽전사","픽셀","야근맨"};
            case "THEQOO" -> new String[]{"봄소녀","달빛소녀","새싹이","꽃새벽","별하나","해누리","꽃내음"};
            case "BLIND"  -> new String[]{"칼퇴요정","야근지옥","증거남겨","이직준비","퇴근후","월급날"};
            case "CLIEN","RULIWEB" -> new String[]{"논리왕","사색가","합리주의자","데이터냥","팩폭러"};
            case "MLBPARK","PPOMPPU" -> new String[]{"경험자","관록","동네형","알뜰살림","꽃주부","현모"};
            default -> new String[]{"별빛","산호","하늘","달","구름","바람","노을","새벽","이슬","숲길"};
        };
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

    /** 나이대에 맞지 않는 직업 조합을 현실적인 값으로 보정 */
    private String coerceJobToAge(String age, String job) {
        return switch (age) {
            case "10s"       -> "학생";  // 10대는 무조건 학생
            case "20s_early" -> job.equals("주부") || job.equals("은퇴자") ? "학생" : job;
            case "60s"       -> job.equals("학생") ? "은퇴자" : job;
            default          -> job.equals("학생") && !age.startsWith("2") ? "직장인" : job;
        };
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
        Files.writeString(
            dir.toPath().resolve("README.md"),
            renderPersonaSummaryMarkdown(shortId, nickname, age, gender, region, job, politics,
                voice, tier, slang, dailyTarget, interests, bias, archetype, voiceMap)
        );
    }

    private String renderPersonaSummaryMarkdown(
            String shortId,
            String nickname,
            String age,
            String gender,
            String region,
            String job,
            String politics,
            String voice,
            String tier,
            double slang,
            int dailyTarget,
            Map<String, Double> interests,
            Map<String, Double> bias,
            String archetype,
            Map<String, Object> voiceMap) {
        String topInterest = interests.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(e -> e.getKey() + " " + String.format("%.1f", e.getValue()))
            .orElse("-");
        String topBias = bias.entrySet().stream()
            .max((a, b) -> Double.compare(Math.abs(a.getValue()), Math.abs(b.getValue())))
            .map(e -> e.getKey() + " " + String.format("%.2f", e.getValue()))
            .orElse("-");
        String formality = String.valueOf(voiceMap.getOrDefault("formality", "casual"));
        String generalStyle = String.valueOf(voiceMap.getOrDefault("general_style", "-"));
        String signaturePhrases = summaryList(voiceMap, "lexicon", "signature_phrases");
        String hotButtons = summaryList(voiceMap, "hot_buttons", "triggers");

        return """
# %s

## Snapshot

- Nickname: `%s`
- Persona key: `%s`
- Archetype: `%s`
- Voice: `%s`
- Tier: `%s`
- Formality: `%s`

## Demographics

- Age band: `%s`
- Gender: `%s`
- Region: `%s`
- Job: `%s`
- Politics: `%s`

## Behavior

- Daily target: `%d`
- Slang level: `%.2f`
- Top interest: `%s`
- Strongest bias: `%s`

## Style

- General style: %s
- Signature phrases: %s
- Hot buttons: %s
""".formatted(
            nickname,
            nickname,
            shortId,
            archetype,
            voice,
            tier,
            formality,
            age,
            gender,
            region,
            job,
            politics,
            dailyTarget,
            slang,
            topInterest,
            topBias,
            generalStyle,
            signaturePhrases,
            hotButtons
        );
    }

    @SuppressWarnings("unchecked")
    private String summaryList(Map<String, Object> voiceMap, String outerKey, String innerKey) {
        Object outer = voiceMap.get(outerKey);
        if (!(outer instanceof Map<?, ?> outerMap)) {
            return "-";
        }
        Object inner = ((Map<String, Object>) outerMap).get(innerKey);
        if (!(inner instanceof List<?> list) || list.isEmpty()) {
            return "-";
        }
        return list.stream().limit(3).map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
    }
}
