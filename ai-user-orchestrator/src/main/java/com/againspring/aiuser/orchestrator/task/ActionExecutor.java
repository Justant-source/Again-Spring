package com.againspring.aiuser.orchestrator.task;

import com.againspring.aiuser.orchestrator.auth.BotTokenCache;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.*;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaActionLog;
import com.againspring.aiuser.orchestrator.domain.PersonaSeenPost;
import com.againspring.aiuser.orchestrator.engine.ArchetypeCatalog;
import com.againspring.aiuser.orchestrator.engine.PlannedAction;
import com.againspring.aiuser.orchestrator.repository.PersonaActionLogRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaSeenPostRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 계획된 행동 실행기.
 * (필요 시 LLM 본문 생성) → 안전 검사 → REST 제출 → 로그 기록.
 *
 * Phase 1·2·3·4: 페르소나 voice 예시 주입, archetype hot-button, 데모그래픽,
 * 기존 댓글 인지, 대댓글 실제 스레드 맥락, stance 다양화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionExecutor {

    private final BotTokenCache tokenCache;
    private final BackendBotClient backendBot;
    private final LlmAiUserClient llmClient;
    private final ContentSafetyGuard safetyGuard;
    private final PersonaSeenPostRepository seenPostRepo;
    private final PersonaActionLogRepository actionLogRepo;
    private final OrchestratorProperties props;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ArchetypeCatalog archetypeCatalog;

    @Value("${ai-user.history-dir:/app/persona-history}")
    private String historyDir;

    private final ConcurrentHashMap<String, String> emailCache = new ConcurrentHashMap<>();
    private static final Random RNG = new Random();

    public void execute(Persona persona, PlannedAction action) {
        String corrId = java.util.UUID.randomUUID().toString().substring(0, 8);
        String email = botEmail(persona);
        java.util.Optional<String> jwtOpt = tokenCache.getToken(persona.getId(), email, props.getBotPassword());
        if (jwtOpt.isEmpty()) {
            log.warn("Cannot execute action for persona {}: no JWT", persona.getId());
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "no_jwt"));
            return;
        }
        String jwt = jwtOpt.get();

        switch (action.type()) {
            case LIKE -> executeLike(persona, action, jwt, corrId);
            case VOTE -> executeVote(persona, action, jwt, corrId);
            case COMMENT -> executeComment(persona, action, jwt, corrId);
            case REPLY -> executeReply(persona, action, jwt, corrId);
            case POST -> executePost(persona, action, jwt, corrId);
            default -> log.warn("Unhandled action type: {}", action.type());
        }
    }

    private void executeLike(Persona persona, PlannedAction action, String jwt, String corrId) {
        if (action.targetPost() == null || action.targetPost().getId() == null) return;
        String postId = action.targetPost().getId();
        boolean ok = backendBot.likePost(jwt, postId);
        markSeen(persona, postId, true);
        logAction(persona, action, ok ? "POSTED" : "FAILED", corrId,
            java.util.Map.of("postId", postId, "usedLlm", false));
    }

    private void executeVote(Persona persona, PlannedAction action, String jwt, String corrId) {
        if (action.targetPost() == null || action.voteOptionId() == null) return;
        String postId = action.targetPost().getId();
        boolean ok = backendBot.vote(jwt, postId, action.voteOptionId());
        markSeen(persona, postId, true);
        logAction(persona, action, ok ? "POSTED" : "FAILED", corrId,
            java.util.Map.of("postId", postId, "optionId", action.voteOptionId(), "usedLlm", false));
    }

    private void executeComment(Persona persona, PlannedAction action, String jwt, String corrId) {
        if (action.targetPost() == null) return;
        String postId = action.targetPost().getId();
        String postTitle = action.targetPost().getUserTitle();
        String postExcerpt = truncate(action.targetPost().getBodyPublished(), 300);
        String stance = pickStanceWeighted(persona, action.targetPost());

        // Phase 4a: 기존 댓글 조회 (GET /api/community/posts/{id}/comments)
        String existingComments = fetchExistingComments(postId);

        // Phase 2d: archetype stance별 few-shot
        String archetypeCommentSamples = buildArchetypeCommentSamples(persona, action.targetPost());

        // Phase 3: demographic
        String demographic = demographicStr(persona);

        java.util.Optional<String> textOpt = llmClient.generateComment(GenDto.CommentRequest.builder()
            .personaId(persona.getId())
            .voiceProfile(voiceBlockForComment(persona, stance))
            .slangLevel(persona.getSlangLevel().doubleValue())
            .postTitle(postTitle)
            .postBodyExcerpt(postExcerpt)
            .stance(stance)
            .category(action.targetPost().getCategory())
            .formality(voiceFormality(persona))
            .demographic(demographic)
            .archetypeCommentSamples(archetypeCommentSamples)
            .existingComments(existingComments)
            .correlationId(corrId)
            .build());

        if (textOpt.isEmpty()) {
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "gen_failed"));
            return;
        }
        String text = textOpt.get();
        ContentSafetyGuard.GuardResult guard = safetyGuard.check(text);
        if (!guard.passed()) {
            log.warn("Comment blocked for persona {} post {}: {}", persona.getId(), postId, guard.reason());
            logAction(persona, action, "BLOCKED", corrId, java.util.Map.of("reason", guard.reason(), "usedLlm", true));
            return;
        }
        boolean ok = backendBot.addComment(jwt, postId, text, null);
        markSeen(persona, postId, true);
        if (ok) {
            writeHistory(persona.getId(), "comments", text, postId, null);
        }
        logAction(persona, action, ok ? "POSTED" : "FAILED", corrId,
            java.util.Map.of("postId", postId, "len", text.length(), "usedLlm", true));
    }

    private void executeReply(Persona persona, PlannedAction action, String jwt, String corrId) {
        if (action.targetPost() == null || action.parentCommentId() == null) return;
        String postId = action.targetPost().getId();

        // Phase 4b: 대댓글 stance 다양화 (CURIOUS 하드코딩 제거)
        String stance = pickReplyStance(persona);

        // Phase 4b: 원글 발췌 + 형제 댓글 (PlannedAction에서)
        String postBodyExcerpt = action.targetPost().getBodyPublished();
        String siblingComments = action.siblingComments();

        java.util.Optional<String> textOpt = llmClient.generateReply(GenDto.ReplyRequest.builder()
            .personaId(persona.getId())
            .voiceProfile(voiceBlockForReply(persona))
            .slangLevel(persona.getSlangLevel().doubleValue())
            .parentCommentExcerpt(action.parentCommentExcerpt())
            .threadContext(action.threadContext())
            .stance(stance)
            .formality(voiceFormality(persona))
            .demographic(demographicStr(persona))
            .postBodyExcerpt(postBodyExcerpt)
            .siblingComments(siblingComments)
            .correlationId(corrId)
            .build());

        if (textOpt.isEmpty()) {
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "gen_failed"));
            return;
        }
        String text = textOpt.get();
        ContentSafetyGuard.GuardResult guard = safetyGuard.check(text);
        if (!guard.passed()) {
            logAction(persona, action, "BLOCKED", corrId, java.util.Map.of("reason", guard.reason(), "usedLlm", true));
            return;
        }
        boolean ok = backendBot.addComment(jwt, postId, text, action.parentCommentId());
        if (ok) {
            writeHistory(persona.getId(), "comments", text, postId, null);
        }
        logAction(persona, action, ok ? "POSTED" : "FAILED", corrId,
            java.util.Map.of("commentId", action.parentCommentId(), "len", text.length(), "usedLlm", true));
    }

    private void executePost(Persona persona, PlannedAction action, String jwt, String corrId) {
        String category = topCategory(persona);

        // Phase 2c: archetype 기반 topic seed (hot_button_phrases, emotional_beats)
        String topicSeed = buildTopicSeed(persona);

        // 글 길이 다양화 — 페르소나 tier 기반 가중 랜덤
        String lengthTier = pickLengthTier(persona);

        java.util.Optional<String> bodyOpt = llmClient.generatePost(GenDto.PostRequest.builder()
            .personaId(persona.getId())
            .voiceProfile(voiceBlockForPost(persona))
            .slangLevel(persona.getSlangLevel().doubleValue())
            .archetype(persona.getArchetype())
            .tier(persona.getTier())
            .category(category)
            .topicSeed(topicSeed)
            .formality(voiceFormality(persona))
            .demographic(demographicStr(persona))
            .lengthTier(lengthTier)
            .correlationId(corrId)
            .build());

        if (bodyOpt.isEmpty()) {
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "gen_failed"));
            return;
        }
        String body = bodyOpt.get();
        ContentSafetyGuard.GuardResult guard = safetyGuard.check(body);
        if (!guard.passed()) {
            logAction(persona, action, "BLOCKED", corrId, java.util.Map.of("reason", guard.reason(), "usedLlm", true));
            return;
        }
        String title = extractTitle(body);
        java.util.Optional<PostDto> postOpt = backendBot.createPost(jwt, CreatePostDto.builder()
            .userTitle(title)
            .bodyRaw(body)
            .category(category)
            .visibility("PUBLIC")
            .jurorCount(0)
            .build());

        postOpt.ifPresent(post -> {
            if (post.getId() != null) {
                markSeen(persona, post.getId(), true);
                writeHistory(persona.getId(), "posts", body, post.getId(), category);
                logAction(persona, action, "POSTED", corrId,
                    java.util.Map.of("postId", post.getId(), "category", category, "usedLlm", true));
            }
        });
        if (postOpt.isEmpty()) {
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "post_failed", "usedLlm", true));
        }
    }

    // ── Voice Profile Blocks (Phase 1b) ──────────────────────────────────────

    /** 글 생성용 voice 블록: general_style + example_post_openers + age/political notes */
    private String voiceBlockForPost(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return "일반 커뮤니티 사용자";
        StringBuilder sb = new StringBuilder();
        appendStr(sb, vp, "general_style");
        appendExamples(sb, vp, "example_post_openers", "글 시작 예시", 2);
        appendStr(sb, vp, "age_voice_notes", "\n[연령 말투] ");
        appendStr(sb, vp, "political_voice_notes", "\n[성향 표현] ");
        return sb.toString().trim();
    }

    /** 댓글 생성용 voice 블록: general_style + example_comments(stance별) + notes */
    @SuppressWarnings("unchecked")
    private String voiceBlockForComment(Persona persona, String stance) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return "일반 커뮤니티 사용자";
        StringBuilder sb = new StringBuilder();
        appendStr(sb, vp, "general_style");
        appendExamples(sb, vp, "example_comments", "댓글 예시", 3);
        // Reactions: pick by stance
        Object reactionsObj = vp.get("reactions");
        if (reactionsObj instanceof Map) {
            Map<String, Object> reactions = (Map<String, Object>) reactionsObj;
            String stanceKey = "AUTHOR".equals(stance) ? "agree"
                : "PARTNER".equals(stance) ? "disagree" : "curious";
            Object examples = reactions.get(stanceKey);
            if (examples instanceof List) {
                List<?> list = (List<?>) examples;
                int n = Math.min(2, list.size());
                List<?> shuffled = new ArrayList<>(list);
                Collections.shuffle((List<Object>) shuffled, RNG);
                sb.append("\n[").append(stanceKey).append(" 반응] ");
                for (int i = 0; i < n; i++) sb.append(shuffled.get(i)).append(" / ");
            }
        }
        appendStr(sb, vp, "age_voice_notes", "\n[연령] ");
        appendStr(sb, vp, "political_voice_notes", "\n[성향] ");
        return sb.toString().trim();
    }

    /** 대댓글 생성용 voice 블록: general_style + example_replies + reactions */
    @SuppressWarnings("unchecked")
    private String voiceBlockForReply(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return "일반 커뮤니티 사용자";
        StringBuilder sb = new StringBuilder();
        appendStr(sb, vp, "general_style");
        appendExamples(sb, vp, "example_replies", "대댓글 예시", 2);
        // Add all reactions as reference
        Object reactionsObj = vp.get("reactions");
        if (reactionsObj instanceof Map) {
            Map<String, Object> reactions = (Map<String, Object>) reactionsObj;
            Object agree = reactions.get("agree");
            if (agree instanceof List && !((List<?>) agree).isEmpty()) {
                List<?> list = (List<?>) agree;
                sb.append("\n[공감 표현] ").append(list.get(RNG.nextInt(list.size())));
            }
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private void appendExamples(StringBuilder sb, Map<String, Object> vp, String key, String label, int maxN) {
        Object raw = vp.get(key);
        if (!(raw instanceof List)) return;
        List<Object> list = new ArrayList<>((List<Object>) raw);
        Collections.shuffle(list, RNG);
        int n = Math.min(maxN, list.size());
        if (n == 0) return;
        sb.append("\n[").append(label).append("] ");
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(" / ");
            sb.append(list.get(i));
        }
    }

    private void appendStr(StringBuilder sb, Map<String, Object> vp, String key) {
        Object v = vp.get(key);
        if (v != null && !v.toString().isBlank()) sb.append(v.toString().trim());
    }

    private void appendStr(StringBuilder sb, Map<String, Object> vp, String key, String prefix) {
        Object v = vp.get(key);
        if (v != null && !v.toString().isBlank()) sb.append(prefix).append(v.toString().trim());
    }

    // ── 글 길이 다양화 ────────────────────────────────────────────────────────

    /**
     * 페르소나 tier에 따른 가중 랜덤 길이 티어 선택.
     * SHORT=25%, MEDIUM=35%, LONG=25%, VERYLONG=15%
     * HEAVY 페르소나는 LONG+VERYLONG 비중 2배
     */
    private String pickLengthTier(Persona persona) {
        double r = RNG.nextDouble();
        boolean heavy = "HEAVY".equals(persona.getTier());
        if (r < 0.25) return "SHORT";
        if (r < 0.60) return "MEDIUM";
        if (r < (heavy ? 0.85 : 0.80)) return "LONG";
        return "VERYLONG";
    }

    // ── Phase 2c: ArchetypeCatalog topicSeed ─────────────────────────────────

    private String buildTopicSeed(Persona persona) {
        String archetypeId = persona.getArchetype();
        ArchetypeCatalog.Archetype arch = archetypeCatalog.get(archetypeId)
            .orElseGet(() -> archetypeCatalog.byCategory(topCategory(persona)).orElse(null));
        if (arch == null) return null;
        return archetypeCatalog.buildTopicSeed(arch, RNG);
    }

    // ── Phase 2d: ArchetypeCatalog comment samples ────────────────────────────

    private String buildArchetypeCommentSamples(Persona persona, PostDto post) {
        if (post == null || post.getCategory() == null) return null;
        // Use post category to find matching archetype
        String archetypeId = persona.getArchetype();
        ArchetypeCatalog.Archetype arch = archetypeCatalog.get(archetypeId)
            .orElseGet(() -> archetypeCatalog.byCategory(post.getCategory()).orElse(null));
        if (arch == null) return null;
        String political = voiceProfileField(persona, "political_orientation");
        return archetypeCatalog.buildCommentSamples(arch, political);
    }

    // ── Phase 3: demographic ──────────────────────────────────────────────────

    private String demographicStr(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return null;
        String age = toKoreanAge(voiceProfileField(persona, "age"));
        String gender = toKoreanGender(voiceProfileField(persona, "gender"));
        String political = toKoreanPolitical(voiceProfileField(persona, "political_orientation"));
        List<String> parts = new ArrayList<>();
        if (age != null) parts.add(age);
        if (gender != null) parts.add(gender);
        if (political != null) parts.add(political + " 성향");
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private String toKoreanAge(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase().trim()) {
            case "20s", "20s_early" -> "20대 초반";
            case "20s_late" -> "20대 후반";
            case "30s", "30s_early" -> "30대 초반";
            case "30s_late" -> "30대 후반";
            case "40s" -> "40대";
            case "50s" -> "50대";
            default -> null;
        };
    }

    private String toKoreanGender(String raw) {
        if (raw == null) return null;
        return switch (raw.toUpperCase().trim()) {
            case "M", "MALE" -> "남성";
            case "F", "FEMALE" -> "여성";
            default -> null;
        };
    }

    private String toKoreanPolitical(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase().trim()) {
            case "conservative" -> "보수";
            case "progressive" -> "진보";
            case "moderate" -> "중도";
            default -> null;
        };
    }

    // ── Phase 4a: Existing comments context ──────────────────────────────────

    private String fetchExistingComments(String postId) {
        try {
            List<CommentThreadDto> comments = backendBot.getComments(postId, 0, 5);
            if (comments == null || comments.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (CommentThreadDto c : comments) {
                if (c.getBody() == null || c.getBody().isBlank()) continue;
                sb.append("- ").append(truncate(c.getBody(), 80)).append("\n");
                if (++count >= 4) break;
            }
            return sb.length() > 0 ? sb.toString().trim() : null;
        } catch (Exception e) {
            log.debug("fetchExistingComments failed for post {}: {}", postId, e.getMessage());
            return null;
        }
    }

    // ── Phase 4b: Reply stance diversification ────────────────────────────────

    /** AGREE/DISAGREE/CURIOUS 가중 랜덤 — persona bias 활용 */
    private String pickReplyStance(Persona persona) {
        double bias = 0.0;
        Map<String, Double> bp = persona.getBiasProfile();
        if (bp != null && !bp.isEmpty()) {
            bias = bp.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        // bias >0: AGREE 선호, <0: DISAGREE 선호, 기본: CURIOUS
        double agreeProb = 0.5 + bias * 0.3;
        agreeProb = Math.max(0.1, Math.min(0.8, agreeProb));
        double disagreeProb = 0.5 - bias * 0.3;
        disagreeProb = Math.max(0.1, Math.min(0.8, disagreeProb));
        double r = RNG.nextDouble() * (agreeProb + disagreeProb + 0.3);
        if (r < agreeProb) return "AGREE";
        if (r < agreeProb + disagreeProb) return "DISAGREE";
        return "CURIOUS";
    }

    // ── Phase 4c: Stance weighted sampling (댓글 stance) ─────────────────────

    /** 댓글 stance 무작위 가중 샘플링 — bias ±0.2 임계 → 확률적 */
    private String pickStanceWeighted(Persona persona, PostDto post) {
        if (post == null || post.getCategory() == null) return randomNeutralOrBias(0.0);
        double bias = Optional.ofNullable(persona.getBiasProfile())
            .map(bp -> bp.getOrDefault(post.getCategory(), 0.0))
            .orElse(0.0);
        return randomNeutralOrBias(bias);
    }

    private String randomNeutralOrBias(double bias) {
        // 0.5 + bias/2 → AUTHOR probability (clamped 0.05-0.95)
        double authorProb = Math.max(0.05, Math.min(0.95, 0.5 + bias / 2.0));
        double partnerProb = Math.max(0.05, Math.min(0.95, 0.5 - bias / 2.0));
        double neutralProb = 1.0 - Math.abs(bias) * 0.6;
        neutralProb = Math.max(0.1, neutralProb);
        double total = authorProb + partnerProb + neutralProb;
        double r = RNG.nextDouble() * total;
        if (r < authorProb) return "AUTHOR";
        if (r < authorProb + partnerProb) return "PARTNER";
        return "NEUTRAL";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String voiceProfileField(Persona persona, String key) {
        try {
            if (persona.getVoiceProfile() == null) return null;
            Object v = persona.getVoiceProfile().get(key);
            return v != null ? v.toString() : null;
        } catch (Exception e) { return null; }
    }

    private String voiceFormality(Persona persona) {
        String f = voiceProfileField(persona, "formality");
        return f != null ? f : "casual";
    }

    private String writeHistory(String personaId, String type, String content, String postId, String category) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(historyDir, personaId);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve(type + ".md");

            String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.of("Asia/Seoul"))
                .format(java.time.Instant.now());

            String entry;
            if ("posts".equals(type)) {
                String titlePreview = content.length() > 40 ? content.substring(0, 40) + "..." : content;
                entry = String.format("\n| %s | %s | %s | %s | POSTED |\n\n### %s — %s\n%s\n\n---\n",
                    timestamp, category != null ? category : "-", postId != null ? postId : "-",
                    titlePreview, timestamp, category != null ? category : "-", content);
            } else {
                String preview = content.length() > 60 ? content.substring(0, 60) + "..." : content;
                entry = String.format("\n| %s | %s | %s | %s |\n\n> %s\n\n---\n",
                    timestamp, "댓글", postId != null ? postId : "-", preview, content);
            }
            java.nio.file.Files.writeString(file, entry,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.debug("History write failed for persona {} type {}: {}", personaId, type, e.getMessage());
        }
        return null;
    }

    private void markSeen(Persona persona, String postId, boolean acted) {
        if (postId == null) return;
        try {
            if (!seenPostRepo.existsByPersonaIdAndPostId(persona.getId(), postId)) {
                seenPostRepo.save(PersonaSeenPost.builder()
                    .personaId(persona.getId())
                    .postId(postId)
                    .seenAt(Instant.now())
                    .acted(acted)
                    .build());
            }
        } catch (Exception e) {
            log.warn("markSeen failed for persona={} post={}: {}", persona.getId(), postId, e.getMessage());
        }
    }

    private void logAction(Persona persona, PlannedAction action, String status, String corrId, java.util.Map<String, java.lang.Object> detail) {
        try {
            boolean usedLlm = (boolean) detail.getOrDefault("usedLlm", false);
            String targetId = action.targetPost() != null ? action.targetPost().getId() : null;
            if (targetId == null && action.parentCommentId() != null) {
                targetId = java.lang.String.valueOf(action.parentCommentId());
            }
            String targetType = switch (action.type()) {
                case LIKE, VOTE, COMMENT, POST -> "POST";
                case REPLY, INVITE_ANSWER -> "COMMENT";
            };
            actionLogRepo.save(PersonaActionLog.builder()
                .personaId(persona.getId())
                .actionType(action.type().name())
                .targetType(targetType)
                .targetId(targetId)
                .usedLlm(usedLlm)
                .status(status)
                .correlationId(corrId)
                .detail(detail)
                .createdAt(Instant.now())
                .build());
        } catch (Exception e) {
            log.warn("logAction failed: {}", e.getMessage());
        }
    }

    private String topCategory(Persona persona) {
        java.util.Map<String, Double> interests = persona.getInterests();
        if (interests == null || interests.isEmpty()) return "OTHER";
        return interests.entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .map(java.util.Map.Entry::getKey)
            .orElse("OTHER");
    }

    private String extractTitle(String body) {
        if (body == null) return "갈등 사연";
        String firstLine = body.split("[\\n\\r]+")[0].trim();
        return firstLine.length() > 80 ? firstLine.substring(0, 80) : firstLine;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String botEmail(Persona persona) {
        return emailCache.computeIfAbsent(persona.getId(), id -> {
            try {
                String email = jdbcTemplate.queryForObject(
                    "SELECT email FROM users WHERE id = ?", String.class, id);
                return email != null ? email : "unknown@againspring.com";
            } catch (Exception e) {
                return "unknown@againspring.com";
            }
        });
    }
}
