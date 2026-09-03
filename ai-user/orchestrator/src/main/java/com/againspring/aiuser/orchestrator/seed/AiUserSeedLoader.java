package com.againspring.aiuser.orchestrator.seed;

import com.againspring.aiuser.orchestrator.client.BackendInternalClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaRelationship;
import com.againspring.aiuser.orchestrator.repository.PersonaRelationshipRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * AI 유저 봇 계정 + 페르소나 시더.
 * 개별 디렉토리 구조에서 로드: classpath:personas/profiles/ai-user{N}/profile.yml + voice.yml
 *
 * 3중 가드: enabled 설정 + profiles 디렉토리 존재 + 이미 시드됨 체크
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUserSeedLoader {

    private final PersonaRepository personaRepo;
    private final PersonaRelationshipRepository relationshipRepo;
    private final JdbcTemplate jdbcTemplate;
    private final OrchestratorProperties props;
    private final PersonaFactory personaFactory;
    private final BackendInternalClient internalClient;

    @Value("${ai-user.seed.enabled:true}")
    private boolean seedEnabled;

    private static final String SENTINEL_EMAIL = "ai-user-001@againspring.internal";

    @PostConstruct
    public void seed() {
        if (!seedEnabled) {
            log.info("AI user seed disabled. Skipping.");
            return;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " +
                    "FROM personas p " +
                    "JOIN users u ON u.id = p.id " +
                    "WHERE u.email = ?",
                Integer.class,
                SENTINEL_EMAIL
            );
            if (count != null && count > 0) {
                log.info("AI users already seeded. Skipping anchor seed.");
                repairBotUserAccounts();
                // 앵커 시드 스킵해도 PersonaFactory는 항상 실행 (부족분 생성)
                try {
                    personaFactory.ensureCount(props.getPersonaTarget());
                } catch (Exception e) {
                    log.warn("PersonaFactory.ensureCount failed (non-critical): {}", e.getMessage());
                }
                // 관계 시딩은 항상 실행 — 새 페어가 추가될 때 자동 반영 (중복은 save()가 무시)
                try {
                    seedRelationships(new org.yaml.snakeyaml.Yaml());
                } catch (Exception e) {
                    log.warn("Relationship re-seed failed (non-critical): {}", e.getMessage());
                }
                return;
            }
        } catch (Exception e) {
            log.warn("Cannot check seed status: {}", e.getMessage());
            return;
        }
        log.info("=== Starting AI user seed from profiles/ directories ===");
        try {
            loadAndInsert();
        } catch (Exception e) {
            log.error("Seed failed: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadAndInsert() throws Exception {
        // Load profiles from filesystem
        File profilesDir = new File(props.getPersonasDir() + "/profiles");
        if (!profilesDir.exists() || !profilesDir.isDirectory()) {
            log.warn("Personas directory not found: {}", profilesDir.getAbsolutePath());
            return;
        }

        File[] profileDirs = profilesDir.listFiles(
            dir -> dir.isDirectory() && new File(dir, "profile.yml").exists()
        );
        if (profileDirs == null || profileDirs.length == 0) {
            log.warn("No profile.yml found under {}", profilesDir.getAbsolutePath());
            return;
        }
        log.info("Found {} persona profiles in {}", profileDirs.length, profilesDir.getAbsolutePath());

        Instant now = Instant.now();
        int userCount = 0, personaCount = 0;

        Yaml yaml = new Yaml();
        for (File profileDir : profileDirs) {
            Map<String, Object> profile;
            File profileFile = new File(profileDir, "profile.yml");
            try (FileInputStream is = new FileInputStream(profileFile)) {
                profile = yaml.load(is);
            } catch (Exception e) {
                log.warn("Failed to parse {}: {}", profileFile.getAbsolutePath(), e.getMessage());
                continue;
            }

            String id = str(profile.get("id"));
            String email = str(profile.get("email"));
            String nickname = str(profile.get("nickname"));
            if (id == null || email == null || nickname == null) {
                log.warn("Skipping profile with missing id/email/nickname: {}", profileFile.getAbsolutePath());
                continue;
            }

            // Load voice.yml from sibling path
            Map<String, Object> voiceData = loadSiblingVoice(profileDir, id, yaml);

            // users 계정 upsert — backend 내부 API 경유(soft-delete 존중, synthetic=1은 backend가 보장)
            Optional<String> upsertStatus = internalClient.upsertPersona(id, email, nickname, props.getBotPassword());
            applyUpsertOutcome(id, upsertStatus);
            if (upsertStatus.isEmpty()) {
                log.error("Failed to upsert user {}", email);
                continue;
            }
            if ("DELETED_SKIPPED".equals(upsertStatus.get())) {
                continue;
            }
            userCount++;

            // Build and insert Persona
            if (!personaRepo.existsById(id)) {
                try {
                    personaRepo.save(buildPersona(id, profile, voiceData, now));
                    personaCount++;
                } catch (Exception e) {
                    log.error("Failed to save persona {}: {}", id, e.getMessage());
                }
            }
        }
        log.info("Seeded {} users, {} personas", userCount, personaCount);

        // Load relationships
        seedRelationships(yaml);

        repairBotUserAccounts();

        // LLM으로 부족분 페르소나 생성
        try {
            personaFactory.ensureCount(props.getPersonaTarget());
        } catch (Exception e) {
            log.warn("PersonaFactory.ensureCount failed (non-critical): {}", e.getMessage());
        }
        log.info("=== Seed complete ===");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadSiblingVoice(File profileDir, String personaId, Yaml yaml) {
        try {
            File voiceFile = new File(profileDir, "voice.yml");
            if (!voiceFile.exists()) {
                return Collections.emptyMap();
            }
            try (FileInputStream is = new FileInputStream(voiceFile)) {
                Object loaded = yaml.load(is);
                if (loaded instanceof Map<?, ?>) {
                    return (Map<String, Object>) loaded;
                }
            }
        } catch (Exception e) {
            log.debug("No voice.yml for {}: {}", personaId, e.getMessage());
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private Persona buildPersona(String id, Map<String, Object> p, Map<String, Object> voice, Instant now) {
        Map<String, Object> activity = mapOf(p.get("activity"));
        String tier = str(activity.get("tier"), "REGULAR");
        double slangLevel = num(activity.get("slang_level"), activity.get("slangLevel"), 0.5);

        // Build voiceProfile from voice.yml content
        Map<String, Object> voiceProfile = new HashMap<>();
        if (!voice.isEmpty()) {
            voiceProfile.put("voice_type", str(voice.get("voice_type"), str(voice.get("voiceType"), tier)));
            voiceProfile.put("general_style", str(voice.get("general_style"), voice.get("generalStyle")));
            // Post examples
            Map<String, Object> postBlock = mapOf(voice.get("post"));
            voiceProfile.put("post_style", str(postBlock.get("style"), "커뮤니티 반말 서술형"));
            Object postOpeners = postBlock.get("example_post_openers");
            if (postOpeners instanceof java.util.List) voiceProfile.put("example_post_openers", postOpeners);
            // Comment examples
            Map<String, Object> commentBlock = mapOf(voice.get("comment"));
            voiceProfile.put("comment_style", str(commentBlock.get("style"), "공감형 짧은 댓글"));
            Object commentExamples = commentBlock.get("example_comments");
            if (commentExamples instanceof java.util.List) voiceProfile.put("example_comments", commentExamples);
            // Reply examples
            Map<String, Object> replyBlock = mapOf(voice.get("reply"));
            Object replyExamples = replyBlock.get("example_replies");
            if (replyExamples instanceof java.util.List) voiceProfile.put("example_replies", replyExamples);
            // Criteria & notes
            voiceProfile.put("like_criteria", str(voice.get("like_criteria"), voice.get("likeCriteria"), "관심 주제에 공감 시"));
            voiceProfile.put("vote_notes", str(voice.get("vote_notes"), voice.get("voteNotes"), "편향 없음"));
            voiceProfile.put("formality", str(voice.get("formality"), "casual"));
            // 페르소나별 좋아요/투표 성향 점수 (ActionPlanner가 사용)
            if (voice.containsKey("like_score")) voiceProfile.put("like_score", voice.get("like_score"));
            if (voice.containsKey("vote_score")) voiceProfile.put("vote_score", voice.get("vote_score"));
            // Voice notes for age/political character
            voiceProfile.put("political_voice_notes", str(voice.get("political_voice_notes"), ""));
            voiceProfile.put("age_voice_notes", str(voice.get("age_voice_notes"), ""));
            // Reactions (agree/disagree/curious examples)
            Object reactions = voice.get("reactions");
            if (reactions instanceof Map) voiceProfile.put("reactions", reactions);
            // 새 필드: lexicon (말투 습관)
            Object lexicon = voice.get("lexicon");
            if (lexicon instanceof Map) voiceProfile.put("lexicon", lexicon);
            // 새 필드: writing_quirks (맞춤법/오탈자 패턴)
            Object writingQuirks = voice.get("writing_quirks");
            if (writingQuirks instanceof Map) voiceProfile.put("writing_quirks", writingQuirks);
            // 새 필드: hot_buttons (감정 트리거)
            Object hotButtons = voice.get("hot_buttons");
            if (hotButtons instanceof Map) voiceProfile.put("hot_buttons", hotButtons);
            // Demographics from voice.yml top-level (with profile.yml fallback for gender)
            voiceProfile.put("age", str(voice.get("age"), ""));
            String genderVal = str(voice.get("gender"), "");
            if (genderVal.isBlank()) {
                Object demog = p.get("demographics");
                if (demog instanceof Map<?,?> demogMap) {
                    genderVal = str(demogMap.get("gender"), "");
                }
            }
            voiceProfile.put("gender", genderVal);
            voiceProfile.put("political_orientation", str(voice.get("political_orientation"), ""));
            voiceProfile.put("political_strength", voice.containsKey("political_strength")
                ? String.valueOf(voice.get("political_strength")) : "");
        } else {
            // Fallback from profile if voice.yml missing
            voiceProfile.put("voice_type", str(activity.get("voice"), "GENERAL"));
            voiceProfile.put("general_style", str(p.get("voice_description"), "일반 커뮤니티 사용자, 반말 위주"));
            voiceProfile.put("post_style", str(p.get("post_style"), "서술형"));
            voiceProfile.put("comment_style", str(p.get("comment_style"), "공감형"));
            voiceProfile.put("like_criteria", str(p.get("like_criteria"), "관심 주제"));
            voiceProfile.put("vote_notes", str(p.get("vote_tendency"), "중립"));
        }

        List<Double> circadian = toDoubleList(activity.get("circadian"));
        int dailyTarget = ((Number) activity.getOrDefault("daily_target",
            activity.getOrDefault("dailyTarget", 6))).intValue();
        String archetype = extractFirstArchetype(p);

        return Persona.builder()
            .id(id)
            .archetype(archetype)
            .tier(tier)
            .voiceProfile(voiceProfile)
            .interests(toDoubleMap(p.get("interests")))
            .biasProfile(toDoubleMap(p.getOrDefault("bias_profile", p.get("biasProfile"))))
            .circadian(circadian != null ? circadian : defaultCircadian())
            .slangLevel(new BigDecimal(String.valueOf(slangLevel)).setScale(2, RoundingMode.HALF_UP))
            .dailyTarget(dailyTarget)
            .active(true)
            .createdAt(now)
            .build();
    }

    @SuppressWarnings("unchecked")
    private void seedRelationships(Yaml yaml) {
        try {
            File relFile = new File(props.getPersonasDir() + "/profiles/relationships.yml");
            if (!relFile.exists()) {
                log.info("No relationships.yml found — skipping relationship seeding");
                return;
            }
            Map<String, Object> data;
            try (FileInputStream is = new FileInputStream(relFile)) {
                data = yaml.load(is);
            }
            List<Map<String, Object>> relList = (List<Map<String, Object>>) data.getOrDefault("relationships", List.of());
            int count = 0;
            for (Map<String, Object> r : relList) {
                try {
                    String pId = str(r.get("persona_id"), r.get("personaId"));
                    String oId = str(r.get("other_id"), r.get("otherId"));
                    if (pId == null || oId == null) continue;
                    String relType = str(r.get("relation_type"), r.get("relationType"), "FRIEND");
                    // 중복 방지 — UNIQUE KEY uk_pair(persona_id, other_id, relation_type)
                    boolean exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM persona_relationships WHERE persona_id=? AND other_id=? AND relation_type=?",
                        Integer.class, pId, oId, relType) > 0;
                    if (exists) continue;
                    relationshipRepo.save(PersonaRelationship.builder()
                        .personaId(pId).otherId(oId)
                        .relationType(relType)
                        .closeness(new BigDecimal(String.valueOf(num(r.get("closeness"), 0.5))).setScale(2, RoundingMode.HALF_UP))
                        .status(str(r.get("status"), "ACTIVE"))
                        .build());
                    count++;
                } catch (Exception e) {
                    log.warn("Failed to save relationship: {}", e.getMessage());
                }
            }
            log.info("Seeded {} relationships", count);
        } catch (Exception e) {
            log.warn("Relationship seeding failed: {}", e.getMessage());
        }
    }

    /**
     * Runtime repair for the invariant personas.id == users.id.
     *
     * Existing deployments may have personas without matching users, or bot users
     * hashed with an older AI_USER_BOT_PASSWORD. Runtime DB schema does not carry
     * nickname/email separately on personas, so nickname repair must source those
     * fields from the mounted profile.yml files. All writes go through
     * {@link BackendInternalClient} — soft-deleted accounts are never resurrected
     * (backend returns {@code DELETED_SKIPPED}; see {@link #applyUpsertOutcome}).
     */
    private void repairBotUserAccounts() {
        int upserted = 0;

        File profilesDir = new File(props.getPersonasDir() + "/profiles");
        File[] profileDirs = profilesDir.listFiles(
            dir -> dir.isDirectory() && new File(dir, "profile.yml").exists()
        );
        if (profileDirs == null || profileDirs.length == 0) {
            log.warn("repairBotUserAccounts: no profile.yml found under {} — nickname repair skipped, " +
                "password sync still runs for all synthetic accounts", profilesDir.getAbsolutePath());
        } else {
            Yaml yaml = new Yaml();
            for (File profileDir : profileDirs) {
                File profileFile = new File(profileDir, "profile.yml");
                Map<String, Object> profile;
                try (FileInputStream is = new FileInputStream(profileFile)) {
                    profile = yaml.load(is);
                } catch (Exception e) {
                    log.warn("repairBotUserAccounts: failed to parse {}: {}", profileFile.getAbsolutePath(), e.getMessage());
                    continue;
                }

                String id = str(profile.get("id"));
                String email = str(profile.get("email"));
                String nickname = str(profile.get("nickname"));
                if (id == null || email == null || nickname == null) {
                    log.warn("repairBotUserAccounts: skipping incomplete profile {}", profileFile.getAbsolutePath());
                    continue;
                }

                Optional<String> status = internalClient.upsertPersona(id, email, nickname, props.getBotPassword());
                applyUpsertOutcome(id, status);
                if (status.isPresent() && !"DELETED_SKIPPED".equals(status.get())) {
                    upserted++;
                }
            }
        }

        // Fallback pass: keep every synthetic bot account's password in sync with the
        // current AI_USER_BOT_PASSWORD, even when it has no mounted profile.yml.
        int finalUpserted = upserted;
        internalClient.rotatePassword(props.getBotPassword()).ifPresentOrElse(
            passwordSynced -> {
                if (finalUpserted > 0 || passwordSynced > 0) {
                    log.info("repairBotUserAccounts: upserted={} passwordSynced={}", finalUpserted, passwordSynced);
                }
            },
            () -> log.warn("repairBotUserAccounts: rotatePassword returned empty")
        );
    }

    /**
     * backend upsert 결과를 반영한다. {@code DELETED_SKIPPED}면 (soft-delete된 계정을
     * 되살리지 않고) 대응 persona를 비활성화한다. HTTP 실패({@link Optional#empty()})는
     * 로그만 남기고 다른 페르소나 시딩을 막지 않는다.
     */
    void applyUpsertOutcome(String id, Optional<String> statusOpt) {
        statusOpt.ifPresentOrElse(status -> {
            if ("DELETED_SKIPPED".equals(status)) {
                personaRepo.findById(id).ifPresent(p -> {
                    p.setActive(false);
                    personaRepo.save(p);
                });
                log.warn("Persona {} is soft-deleted in backend; marked inactive, not resurrected", id);
            }
        }, () -> log.warn("upsertPersona returned empty for {}", id));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String str(Object... vals) {
        for (Object v : vals) { if (v != null) return String.valueOf(v); }
        return null;
    }

    private double num(Object... vals) {
        for (Object v : vals) {
            if (v instanceof Number n) return n.doubleValue();
            if (v == null) continue;
            try { return Double.parseDouble(String.valueOf(v)); } catch (Exception ignored) {}
        }
        return 0.0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object v) {
        if (v instanceof Map) return (Map<String, Object>) v;
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> toDoubleMap(Object raw) {
        if (!(raw instanceof Map)) return new HashMap<>();
        Map<String, Object> m = (Map<String, Object>) raw;
        Map<String, Double> result = new HashMap<>();
        m.forEach((k, v) -> { if (v instanceof Number n) result.put(k, n.doubleValue()); });
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Double> toDoubleList(Object raw) {
        if (!(raw instanceof List)) return null;
        List<Object> list = (List<Object>) raw;
        List<Double> result = new ArrayList<>();
        list.forEach(v -> { if (v instanceof Number n) result.add(n.doubleValue()); });
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private String extractFirstArchetype(Map<String, Object> p) {
        Object prefs = p.getOrDefault("archetype_preferences", p.get("archetypePreferences"));
        if (prefs instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.get(0));
        return "general";
    }

    private List<Double> defaultCircadian() {
        return List.of(0.0,0.0,0.0,0.0,0.0,0.0,0.1,0.2,0.4,0.5,0.5,0.5,
                       0.4,0.4,0.4,0.5,0.5,0.6,0.7,0.8,0.9,0.8,0.6,0.2);
    }
}
