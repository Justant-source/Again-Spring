package com.againspring.aiuser.orchestrator.task;

import com.againspring.aiuser.orchestrator.auth.BotTokenCache;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.*;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaActionLog;
import com.againspring.aiuser.orchestrator.domain.PersonaSeenPost;
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

/**
 * 계획된 행동 실행기.
 * (필요 시 LLM 본문 생성) → 안전 검사 → REST 제출 → 로그 기록.
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

    @Value("${ai-user.history-dir:/app/persona-history}")
    private String historyDir;

    private final ConcurrentHashMap<String, String> emailCache = new ConcurrentHashMap<>();

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

        // Generate comment via Haiku
        java.util.Optional<String> textOpt = llmClient.generateComment(GenDto.CommentRequest.builder()
            .personaId(persona.getId())
            .voiceProfile(voiceProfileStr(persona))
            .slangLevel(persona.getSlangLevel().doubleValue())
            .postTitle(postTitle)
            .postBodyExcerpt(postExcerpt)
            .stance(pickStance(persona, action.targetPost()))
            .category(action.targetPost().getCategory())
            .formality(voiceFormality(persona))
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

        java.util.Optional<String> textOpt = llmClient.generateReply(GenDto.ReplyRequest.builder()
            .personaId(persona.getId())
            .voiceProfile(voiceProfileStr(persona))
            .slangLevel(persona.getSlangLevel().doubleValue())
            .parentCommentExcerpt(action.parentCommentExcerpt())
            .threadContext(action.threadContext())
            .stance("CURIOUS")
            .formality(voiceFormality(persona))
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
        // Pick a random category from persona's top interests
        String category = topCategory(persona);

        java.util.Optional<String> bodyOpt = llmClient.generatePost(GenDto.PostRequest.builder()
            .personaId(persona.getId())
            .voiceProfile(voiceProfileStr(persona))
            .slangLevel(persona.getSlangLevel().doubleValue())
            .archetype(persona.getArchetype())
            .tier(persona.getTier())
            .category(category)
            .formality(voiceFormality(persona))
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * 페르소나 활동 히스토리를 외부 마크다운 파일에 기록.
     * 파일 경로: {historyDir}/{personaId}/{posts|comments}.md
     */
    private void writeHistory(String personaId, String type, String content, String postId, String category) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(historyDir, personaId);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve(type + ".md"); // posts.md or comments.md

            String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.of("Asia/Seoul"))
                .format(java.time.Instant.now());

            String entry;
            if ("posts".equals(type)) {
                // Table row + full text
                String titlePreview = content.length() > 40 ? content.substring(0, 40) + "..." : content;
                entry = String.format("\n| %s | %s | %s | %s | POSTED |\n\n### %s — %s\n%s\n\n---\n",
                    timestamp, category != null ? category : "-", postId != null ? postId : "-",
                    titlePreview, timestamp, category != null ? category : "-", content);
            } else {
                // comments.md
                String preview = content.length() > 60 ? content.substring(0, 60) + "..." : content;
                entry = String.format("\n| %s | %s | %s | %s |\n\n> %s\n\n---\n",
                    timestamp, type.equals("comments") ? "댓글" : "대댓글",
                    postId != null ? postId : "-", preview, content);
            }

            // Append to file (create if not exists)
            java.nio.file.Files.writeString(file, entry,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.debug("History write failed for persona {} type {}: {}", personaId, type, e.getMessage());
            // Non-critical — don't fail the action
        }
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

    private String voiceProfileStr(Persona persona) {
        try {
            if (persona.getVoiceProfile() == null) return "일반 커뮤니티 사용자";
            java.lang.Object desc = persona.getVoiceProfile().get("general_style");
            if (desc == null) desc = persona.getVoiceProfile().get("description");
            return desc != null ? java.lang.String.valueOf(desc) : "일반 커뮤니티 사용자";
        } catch (Exception e) { return "일반 커뮤니티 사용자"; }
    }

    private String voiceFormality(Persona persona) {
        try {
            if (persona.getVoiceProfile() == null) return "casual";
            java.lang.Object f = persona.getVoiceProfile().get("formality");
            return f != null ? java.lang.String.valueOf(f) : "casual";
        } catch (Exception e) { return "casual"; }
    }

    private String pickStance(Persona persona, PostDto post) {
        if (post == null || post.getCategory() == null) return "NEUTRAL";
        double bias = java.util.Optional.ofNullable(persona.getBiasProfile())
            .map(bp -> bp.getOrDefault(post.getCategory(), 0.0))
            .orElse(0.0);
        if (bias > 0.2) return "AUTHOR";
        if (bias < -0.2) return "PARTNER";
        return "NEUTRAL";
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
