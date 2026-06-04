package com.againspring.aiuser.orchestrator.seed;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaRelationship;
import com.againspring.aiuser.orchestrator.repository.PersonaRelationshipRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
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

    @Value("${ai-user.seed.enabled:true}")
    private boolean seedEnabled;

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);
    private static final String SENTINEL_EMAIL = "ai-user01@againspring.com";
    private static final String PROFILES_PATTERN = "classpath:personas/profiles/*/profile.yml";
    private static final String RELATIONSHIPS_PATH = "classpath:personas/profiles/relationships.yml";

    @PostConstruct
    public void seed() {
        if (!seedEnabled) {
            log.info("AI user seed disabled. Skipping.");
            return;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, SENTINEL_EMAIL);
            if (count != null && count > 0) {
                log.info("AI users already seeded. Skipping (run with AI_USER_SEED_ENABLED=false to suppress).");
                markSyntheticFlag();
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
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] profileResources = resolver.getResources(PROFILES_PATTERN);

        if (profileResources.length == 0) {
            log.warn("No profile.yml found at {}. Skipping seed.", PROFILES_PATTERN);
            return;
        }
        log.info("Found {} persona profiles", profileResources.length);

        String hashedPassword = PASSWORD_ENCODER.encode(props.getBotPassword());
        Instant now = Instant.now();
        int userCount = 0, personaCount = 0;

        Yaml yaml = new Yaml();
        for (Resource profileRes : profileResources) {
            Map<String, Object> profile;
            try (InputStream is = profileRes.getInputStream()) {
                profile = yaml.load(is);
            } catch (Exception e) {
                log.warn("Failed to parse {}: {}", profileRes.getFilename(), e.getMessage());
                continue;
            }

            String id = str(profile.get("id"));
            String email = str(profile.get("email"));
            String nickname = str(profile.get("nickname"));
            if (id == null || email == null || nickname == null) {
                log.warn("Skipping profile with missing id/email/nickname: {}", profileRes.getURI());
                continue;
            }

            // Load voice.yml from sibling path
            Map<String, Object> voiceData = loadSiblingVoice(profileRes, id, yaml);

            // Insert user
            try {
                jdbcTemplate.update(
                    "INSERT IGNORE INTO users (id, email, password_hash, nickname, roles, " +
                    "is_guest, must_change_password, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, '[\"USER\"]', 0, 0, ?, ?)",
                    id, email, hashedPassword, nickname, now, now);
                userCount++;
            } catch (Exception e) {
                log.error("Failed to insert user {}: {}", email, e.getMessage());
                continue;
            }

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

        // Mark synthetic flag
        markSyntheticFlag();
        log.info("=== Seed complete ===");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadSiblingVoice(Resource profileRes, String personaId, Yaml yaml) {
        try {
            String profileUri = profileRes.getURI().toString();
            String voiceUri = profileUri.replace("profile.yml", "voice.yml");
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            // Try loading from sibling path
            Resource voiceRes = resolver.getResource(voiceUri);
            if (voiceRes.exists()) {
                try (InputStream is = voiceRes.getInputStream()) {
                    return yaml.load(is);
                }
            }
        } catch (Exception e) {
            log.debug("voice.yml not found for persona {}: {}", personaId, e.getMessage());
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
            // Voice notes for age/political character
            voiceProfile.put("political_voice_notes", str(voice.get("political_voice_notes"), ""));
            voiceProfile.put("age_voice_notes", str(voice.get("age_voice_notes"), ""));
            // Reactions (agree/disagree/curious examples)
            Object reactions = voice.get("reactions");
            if (reactions instanceof Map) voiceProfile.put("reactions", reactions);
            // Demographics from voice.yml top-level
            voiceProfile.put("age", str(voice.get("age"), ""));
            voiceProfile.put("gender", str(voice.get("gender"), ""));
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
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource relRes = resolver.getResource(RELATIONSHIPS_PATH);
            if (!relRes.exists()) {
                log.info("No relationships.yml found — skipping relationship seeding");
                return;
            }
            Map<String, Object> data;
            try (InputStream is = relRes.getInputStream()) {
                data = yaml.load(is);
            }
            List<Map<String, Object>> relList = (List<Map<String, Object>>) data.getOrDefault("relationships", List.of());
            int count = 0;
            for (Map<String, Object> r : relList) {
                try {
                    String pId = str(r.get("persona_id"), r.get("personaId"));
                    String oId = str(r.get("other_id"), r.get("otherId"));
                    if (pId == null || oId == null) continue;
                    relationshipRepo.save(PersonaRelationship.builder()
                        .personaId(pId).otherId(oId)
                        .relationType(str(r.get("relation_type"), r.get("relationType"), "FRIEND"))
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

    private void markSyntheticFlag() {
        try {
            int updated = jdbcTemplate.update(
                "UPDATE users SET synthetic = 1 WHERE email LIKE 'ai-user%@againspring.com'");
            if (updated > 0) log.info("Marked {} users as synthetic=1", updated);
        } catch (Exception e) {
            log.debug("synthetic column not available yet (V59 pending): {}", e.getMessage());
        }
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
