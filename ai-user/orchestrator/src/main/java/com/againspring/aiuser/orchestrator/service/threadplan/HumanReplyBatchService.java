package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.CommentThreadDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.*;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemStatus;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemType;
import com.againspring.aiuser.orchestrator.repository.*;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.util.LiteralNewlineNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bounded 30-minute human interaction batch: up to N comments across at most M posts, one retry only.
 * Injects real post/comment/responder payloads so the structured LLM endpoint does not 400.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HumanReplyBatchService {
    private final HumanInteractionInboxService inbox;
    private final AiThreadPlanRepository plans;
    private final AiThreadPlanItemRepository planItems;
    private final PersonaRepository personaRepository;
    private final LlmAiUserClient llm;
    private final ContentSafetyGuard guard;
    private final OrchestratorProperties props;
    private final AiUserGenerationConfigRepository configRepository;
    private final BackendBotClient backend;
    private final JdbcTemplate jdbc;

    public void run() {
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        if (!props.isEnabled() || !props.getThreadPlan().isEnabled() || !props.getThreadPlan().isHumanReplyBatchEnabled() || config == null
                || config.isAiUserKillSwitch() || "OFF".equalsIgnoreCase(config.getProviderHumanInteraction())) return;
        String worker = "human-reply-batch";
        Instant now = Instant.now();
        int maxComments = config.getHumanBatchMaxInteractions() > 0 ? config.getHumanBatchMaxInteractions() : props.getThreadPlan().getHumanReplyMaxComments();
        int maxPosts = config.getHumanBatchMaxPosts() > 0 ? config.getHumanBatchMaxPosts() : props.getThreadPlan().getHumanReplyMaxPosts();
        List<AiHumanInteractionInbox> claimed = inbox.claimPending(worker, maxComments, Duration.ofMinutes(29), now);
        List<AiHumanInteractionInbox> selected = new ArrayList<>();
        Set<String> posts = new HashSet<>();
        for (AiHumanInteractionInbox entry : claimed) {
            if (posts.contains(entry.getPostId()) || posts.size() < maxPosts) {
                selected.add(entry);
                posts.add(entry.getPostId());
            }
        }
        claimed.stream().filter(e -> !selected.contains(e)).forEach(e -> inbox.release(e.getId(), worker));
        if (selected.isEmpty()) return;

        List<Map<String, Object>> items = new ArrayList<>();
        List<AiHumanInteractionInbox> ready = new ArrayList<>();
        for (AiHumanInteractionInbox entry : selected) {
            Optional<Map<String, Object>> item = buildItem(entry);
            if (item.isEmpty()) {
                inbox.markSkipped(entry.getId(), worker, "MISSING_CONTEXT");
                continue;
            }
            items.add(item.get());
            ready.add(entry);
        }
        if (ready.isEmpty()) return;

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("provider", config.getProviderHumanInteraction());
        if (!props.getThreadPlan().getHumanPlanModel().isBlank()) request.put("model", props.getThreadPlan().getHumanPlanModel());
        request.put("correlationId", "human-replies-" + now.toEpochMilli());
        request.put("timeoutMs", props.getThreadPlan().getBundleTimeoutMs());
        request.put("items", items);
        Optional<Map<String, Object>> response = llm.generateHumanReplies(request);
        if (response.isEmpty()) response = llm.generateHumanReplies(request);
        if (response.isEmpty()) {
            ready.forEach(e -> inbox.release(e.getId(), worker));
            return;
        }
        persist(worker, ready, response.get(), now);
    }

    Optional<Map<String, Object>> buildItem(AiHumanInteractionInbox entry) {
        CommentContext ctx = loadCommentContext(entry);
        if (ctx == null || ctx.humanBody().isBlank()) return Optional.empty();
        Map<String, Object> responder = pickResponder(entry.getPostId());
        if (responder.isEmpty()) return Optional.empty();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("postId", entry.getPostId());
        long humanCommentId;
        try {
            humanCommentId = Long.parseLong(entry.getSourceCommentId());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        item.put("humanCommentId", humanCommentId);
        long parentId = 0L;
        if (entry.getParentCommentId() != null && !entry.getParentCommentId().isBlank()) {
            try { parentId = Long.parseLong(entry.getParentCommentId()); } catch (NumberFormatException ignored) {}
        } else if (ctx.parentCommentId() != null) {
            parentId = ctx.parentCommentId();
        }
        item.put("parentCommentId", parentId);
        item.put("postTitle", ctx.postTitle());
        item.put("postBody", ctx.postBody());
        item.put("humanBody", ctx.humanBody());
        if (ctx.parentBody() != null && !ctx.parentBody().isBlank()) item.put("parentBody", ctx.parentBody());
        item.put("responder", responder);
        return Optional.of(item);
    }

    private CommentContext loadCommentContext(AiHumanInteractionInbox entry) {
        CommentContext fromDb = loadFromJdbc(entry);
        if (fromDb != null && !fromDb.humanBody().isBlank()) return fromDb;
        return loadFromBackend(entry);
    }

    private CommentContext loadFromJdbc(AiHumanInteractionInbox entry) {
        try {
            long commentId = Long.parseLong(entry.getSourceCommentId());
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT pc.body AS body, pc.parent_comment_id AS parent_id, " +
                    "LEFT(COALESCE(p.user_title, ''), 200) AS post_title, " +
                    "LEFT(COALESCE(p.body_published, ''), 2000) AS post_body " +
                    "FROM post_comments pc JOIN posts p ON p.id = pc.post_id " +
                    "WHERE pc.id = ? AND pc.post_id = ? AND pc.deleted_at IS NULL",
                    commentId, entry.getPostId());
            if (rows.isEmpty()) return null;
            Map<String, Object> row = rows.get(0);
            String body = string(row.get("body"));
            Long parentId = number(row.get("parent_id"));
            String parentBody = null;
            if (parentId != null) {
                List<Map<String, Object>> parents = jdbc.queryForList(
                        "SELECT body FROM post_comments WHERE id = ? AND deleted_at IS NULL", parentId);
                if (!parents.isEmpty()) parentBody = string(parents.get(0).get("body"));
            }
            String title = string(row.get("post_title"));
            String postBody = string(row.get("post_body"));
            if (title.isBlank() || postBody.isBlank()) {
                AiThreadPlan plan = plans.findTopByPostIdOrderByPostRevisionDesc(entry.getPostId()).orElse(null);
                if (plan != null) {
                    if (title.isBlank()) title = nullToEmpty(plan.getSourceTitle());
                    if (postBody.isBlank()) postBody = nullToEmpty(plan.getSourceBody());
                }
            }
            return new CommentContext(body, title, postBody, parentId, parentBody);
        } catch (Exception e) {
            log.debug("jdbc comment load failed for {}: {}", entry.getSourceCommentId(), e.getMessage());
            return null;
        }
    }

    private CommentContext loadFromBackend(AiHumanInteractionInbox entry) {
        try {
            long commentId = Long.parseLong(entry.getSourceCommentId());
            String title = "";
            String postBody = "";
            Optional<Map<String, Object>> post = backend.getPost(entry.getPostId());
            if (post.isPresent()) {
                Map<String, Object> p = post.get();
                title = firstNonBlank(string(p.get("userTitle")), string(p.get("title")));
                postBody = firstNonBlank(string(p.get("bodyPublished")), string(p.get("body")));
            }
            if (title.isBlank() || postBody.isBlank()) {
                AiThreadPlan plan = plans.findTopByPostIdOrderByPostRevisionDesc(entry.getPostId()).orElse(null);
                if (plan != null) {
                    if (title.isBlank()) title = nullToEmpty(plan.getSourceTitle());
                    if (postBody.isBlank()) postBody = nullToEmpty(plan.getSourceBody());
                }
            }
            CommentMatch match = findComment(backend.getComments(entry.getPostId(), 0, 100), commentId, null);
            if (match == null || match.body() == null || match.body().isBlank()) return null;
            return new CommentContext(match.body(), title, postBody, match.parentId(), match.parentBody());
        } catch (Exception e) {
            log.warn("backend comment load failed for {}: {}", entry.getSourceCommentId(), e.getMessage());
            return null;
        }
    }

    private CommentMatch findComment(List<CommentThreadDto> threads, long targetId, Long parentId) {
        if (threads == null) return null;
        for (CommentThreadDto c : threads) {
            if (c.getId() != null && c.getId() == targetId) {
                return new CommentMatch(c.getBody(), parentId, null);
            }
            if (c.getReplies() != null) {
                for (CommentThreadDto r : c.getReplies()) {
                    if (r.getId() != null && r.getId() == targetId) {
                        return new CommentMatch(r.getBody(), c.getId(), c.getBody());
                    }
                }
            }
        }
        return null;
    }

    private Map<String, Object> pickResponder(String postId) {
        List<String> preferred = List.of();
        try {
            preferred = planItems.findByPostAndTypesAndStatuses(
                            postId,
                            EnumSet.of(ThreadPlanItemType.COMMENT, ThreadPlanItemType.REPLY),
                            EnumSet.of(ThreadPlanItemStatus.SCHEDULED, ThreadPlanItemStatus.POSTED,
                                    ThreadPlanItemStatus.PROCESSING, ThreadPlanItemStatus.RESERVED))
                    .stream()
                    .map(AiThreadPlanItem::getPersonaId)
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank())
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.debug("responder preferred pool failed for {}: {}", postId, e.getMessage());
        }
        List<Persona> active = personaRepository.findByActiveTrue();
        if (active.isEmpty()) return Map.of();
        final List<String> preferredIds = preferred;
        List<Persona> pool = preferredIds.isEmpty() ? active
                : active.stream().filter(p -> preferredIds.contains(p.getId())).toList();
        if (pool.isEmpty()) pool = active;
        Persona chosen = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        Map<String, Object> responder = new LinkedHashMap<>();
        responder.put("personaId", chosen.getId());
        responder.put("nickname", nicknameOf(chosen));
        responder.put("voiceProfile", String.valueOf(chosen.getVoiceProfile()));
        responder.put("formality", formalityOf(chosen));
        return responder;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    void persist(String worker, List<AiHumanInteractionInbox> selected, Map<String, Object> response, Instant now) {
        Map<String, AiHumanInteractionInbox> byComment = new HashMap<>();
        selected.forEach(e -> byComment.put(e.getSourceCommentId(), e));
        Object raw = response.get("replies");
        if (!(raw instanceof List<?> replies)) {
            selected.forEach(e -> inbox.release(e.getId(), worker));
            return;
        }
        Set<String> answered = new HashSet<>();
        for (Object value : replies) {
            if (!(value instanceof Map<?, ?> row)) continue;
            String comment = String.valueOf(row.get("humanCommentId"));
            AiHumanInteractionInbox entry = byComment.get(comment);
            String body = LiteralNewlineNormalizer.normalize(
                    row.get("body") == null ? "" : String.valueOf(row.get("body"))).trim();
            String persona = row.get("personaId") == null ? "" : String.valueOf(row.get("personaId"));
            if (entry == null || body.isBlank() || persona.isBlank()
                    || !personaRepository.existsById(persona)
                    || !guard.check(body, ContentSafetyGuard.ContentType.COMMENT).passed()) continue;
            AiThreadPlan plan = plans.findTopByPostIdOrderByPostRevisionDesc(entry.getPostId()).orElse(null);
            if (plan == null || now.isAfter(plan.getAbsoluteExpiresAt())) continue;
            Duration delay = resolveDelay(row.get("delayMinutes"));
            Instant when = now.plus(delay);
            AiThreadPlanItem item = planItems.save(AiThreadPlanItem.builder()
                    .planId(plan.getId())
                    .itemType(ThreadPlanItemType.REPLY)
                    .status(ThreadPlanItemStatus.SCHEDULED)
                    .sequenceNo(10000 + answered.size())
                    .personaId(persona)
                    .targetPostId(entry.getPostId())
                    .targetCommentId(entry.getSourceCommentId())
                    .body(body)
                    .scheduledAt(when)
                    .notBefore(when)
                    .idempotencyKey("human-reply:" + entry.getSourceCommentId())
                    .build());
            inbox.markResponded(entry.getId(), worker, item.getId());
            answered.add(comment);
        }
        selected.stream().filter(e -> !answered.contains(e.getSourceCommentId())).forEach(e -> inbox.release(e.getId(), worker));
    }

    /** Prefer LLM delayMinutes when present and in range; otherwise random in configured [min, max]. */
    Duration resolveDelay(Object delayMinutesRaw) {
        OrchestratorProperties.HumanReply cfg = props.getHumanReply();
        int min = Math.max(1, Math.min(cfg.getDelayMinutesMin(), 30));
        int max = Math.max(min, Math.min(cfg.getDelayMinutesMax(), 30));
        Integer fromLlm = parseDelayMinutes(delayMinutesRaw);
        int minutes = fromLlm != null && fromLlm >= min && fromLlm <= max
                ? fromLlm
                : ThreadLocalRandom.current().nextInt(min, max + 1);
        return Duration.ofMinutes(minutes);
    }

    private static Integer parseDelayMinutes(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(raw).trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static String nicknameOf(Persona p) {
        Object nick = p.getVoiceProfile() == null ? null : p.getVoiceProfile().get("nickname");
        if (nick != null && !String.valueOf(nick).isBlank()) return String.valueOf(nick);
        return p.getId();
    }

    private static String formalityOf(Persona p) {
        Object f = p.getVoiceProfile() == null ? null : p.getVoiceProfile().get("formality");
        if (f != null && !String.valueOf(f).isBlank()) return String.valueOf(f);
        return "neutral";
    }

    private static String string(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
    private static String nullToEmpty(String v) { return v == null ? "" : v; }
    private static String firstNonBlank(String a, String b) { return !a.isBlank() ? a : b; }
    private static Long number(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    record CommentContext(String humanBody, String postTitle, String postBody, Long parentCommentId, String parentBody) {}
    record CommentMatch(String body, Long parentId, String parentBody) {}
}
