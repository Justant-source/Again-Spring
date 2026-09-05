package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.CommentThreadDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.service.GenerationConfigSupport;
import com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService;
import com.againspring.aiuser.orchestrator.service.persona.PersonaLottery;
import com.againspring.aiuser.orchestrator.domain.*;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemStatus;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemType;
import com.againspring.aiuser.orchestrator.repository.*;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.util.LiteralNewlineNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bounded 30-minute human interaction batch (§16.7 / W6-B+C):
 * chunk ≤20, 0~3 responders/interaction, 3×5·15 budgets, interested-pool candidates.
 * automatic_attempts_max=2 with durable GENERATION_FAILED on empty LLM (W6-C).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HumanReplyBatchService {
    /** Hard invariant (§16.7): initial call + one automatic retry. */
    static final int AUTOMATIC_ATTEMPTS_MAX = 2;
    static final String FAILURE_GENERATION_FAILED = "GENERATION_FAILED";
    static final String FAILURE_NO_RESPONSE = "NO_RESPONSE";
    static final String FAILURE_NO_ACTIVE_PLAN = "NO_ACTIVE_PLAN";
    static final String FAILURE_BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED";
    static final String FAILURE_MISSING_CONTEXT = "MISSING_CONTEXT";

    private static final EnumSet<ThreadPlanItemStatus> BUDGET_EXCLUDED = EnumSet.of(
            ThreadPlanItemStatus.CANCELLED, ThreadPlanItemStatus.FAILED, ThreadPlanItemStatus.EXPIRED);

    private final HumanInteractionInboxService inbox;
    private final AiThreadPlanRepository plans;
    private final AiThreadPlanItemRepository planItems;
    private final PersonaRepository personaRepository;
    private final AiPostInterestedPersonaRepository interestedPersonas;
    private final LlmAiUserClient llm;
    private final ContentSafetyGuard guard;
    private final OrchestratorProperties props;
    private final GenerationConfigSupport generationConfigSupport;
    private final AiUserGenerationConfigRepository configRepository;
    private final BackendBotClient backend;
    private final JdbcTemplate jdbc;
    private final LlmGenerationGateService llmGenerationGateService;
    private final com.againspring.aiuser.orchestrator.service.llm.PromptTemplateCache promptTemplateCache;
    private final PersonaLottery personaLottery;

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
            if (findUsablePlan(entry.getPostId(), now).isEmpty()) {
                log.info("human-reply skip NO_ACTIVE_PLAN inbox={} post={}", entry.getId(), entry.getPostId());
                inbox.markSkipped(entry.getId(), worker, FAILURE_NO_ACTIVE_PLAN);
                continue;
            }
            Optional<Map<String, Object>> item = buildItem(entry, config);
            if (item.isEmpty()) {
                inbox.markSkipped(entry.getId(), worker, FAILURE_MISSING_CONTEXT);
                continue;
            }
            items.add(item.get());
            ready.add(entry);
        }
        if (ready.isEmpty()) return;

        int chunkSize = resolvedChunkSize(config);
        List<List<Integer>> chunks = chunkIndexes(ready.size(), chunkSize);
        for (List<Integer> indexes : chunks) {
            List<AiHumanInteractionInbox> chunkEntries = new ArrayList<>(indexes.size());
            List<Map<String, Object>> chunkItems = new ArrayList<>(indexes.size());
            for (int idx : indexes) {
                chunkEntries.add(ready.get(idx));
                chunkItems.add(items.get(idx));
            }
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("provider", config.getProviderHumanInteraction());
            if (!props.getThreadPlan().getHumanPlanModel().isBlank()) request.put("model", props.getThreadPlan().getHumanPlanModel());
            request.put("promptOverrides", promptTemplateCache.overrides());
            request.put("correlationId", "human-replies-" + now.toEpochMilli() + "-c" + indexes.get(0));
            request.put("timeoutMs", generationConfigSupport.bundleTimeoutMs());
            request.put("items", chunkItems);

            // LLM Generation Gate check: skip generation if held
            if (llmGenerationGateService.isHeld()) {
                log.info("[HumanReplyBatch] generation held (LLM gate) for {} inbox entries",
                        chunkEntries.size());
                chunkEntries.forEach(e ->
                        inbox.markSkipped(e.getId(), worker, "GENERATION_HELD_BY_GATE", 0));
                continue;
            }

            int attempts = 0;
            Optional<Map<String, Object>> response = Optional.empty();
            while (attempts < AUTOMATIC_ATTEMPTS_MAX && response.isEmpty()) {
                attempts++;
                response = llm.generateHumanReplies(request);
            }
            if (response.isEmpty()) {
                final int failedAttempts = attempts;
                chunkEntries.forEach(e ->
                        inbox.markSkipped(e.getId(), worker, FAILURE_GENERATION_FAILED, failedAttempts));
                log.warn("human reply chunk GENERATION_FAILED after {} attempts (n={})",
                        failedAttempts, chunkEntries.size());
                continue;
            }
            persist(worker, chunkEntries, response.get(), now, attempts);
        }
    }

    Optional<Map<String, Object>> buildItem(AiHumanInteractionInbox entry) {
        return buildItem(entry, configRepository.findById(1).orElse(null));
    }

    Optional<Map<String, Object>> buildItem(AiHumanInteractionInbox entry, AiUserGenerationConfig config) {
        CommentContext ctx = loadCommentContext(entry);
        if (ctx == null || ctx.humanBody().isBlank()) return Optional.empty();
        List<Map<String, Object>> candidates = loadCandidateResponders(entry.getPostId(), config);
        if (candidates.isEmpty()) return Optional.empty();
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
        item.put("candidateResponders", candidates);
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

    /**
     * Prefer {@code ai_post_interested_personas} (W6-A); degrade to plan-item personas then active.
     * Caps at {@code candidateRespondersMax}. voiceProfile is a structured Map (not Map.toString()).
     */
    List<Map<String, Object>> loadCandidateResponders(String postId) {
        return loadCandidateResponders(postId, configRepository.findById(1).orElse(null));
    }

    /**
     * WP3 계약 6: preferred pool 안에서도 결정론 {@code findByPostIdOrderByScoreDesc} 대신
     * {@link com.againspring.aiuser.orchestrator.service.persona.PersonaLottery} 가중 추첨을
     * 쓴다(가중치는 {@code last_comment_at} 기준). candidateRespondersMax·hr_* admin 예산은
     * 그대로 존중한다(추첨 결과 개수의 상한일 뿐).
     */
    List<Map<String, Object>> loadCandidateResponders(String postId, AiUserGenerationConfig config) {
        int max = Math.max(1, candidateRespondersMax(config));
        List<Persona> active = personaRepository.findByActiveTrue();
        if (active.isEmpty()) return List.of();
        Map<String, Persona> byId = new LinkedHashMap<>();
        for (Persona p : active) byId.put(p.getId(), p);

        List<String> preferred = loadInterestedPersonaIds(postId);
        if (preferred.isEmpty()) {
            preferred = loadPlanItemPersonaIds(postId);
        }
        List<Persona> preferredPool = new ArrayList<>();
        for (String id : preferred) {
            Persona p = byId.get(id);
            if (p != null) preferredPool.add(p);
        }
        List<Persona> pool = personaLottery.drawCommenters(
                preferredPool.isEmpty() ? active : preferredPool, null, Set.of(), max, ThreadLocalRandom.current());
        List<Map<String, Object>> out = new ArrayList<>(pool.size());
        for (Persona chosen : pool) {
            out.add(toResponderMap(chosen));
        }
        return out;
    }

    /** 점수순이 아니라 후보 id 전체 — 순서는 {@link com.againspring.aiuser.orchestrator.service.persona.PersonaLottery}가 가중 추첨으로 정한다. */
    private List<String> loadInterestedPersonaIds(String postId) {
        try {
            return interestedPersonas.findByPostIdOrderByIdAsc(postId).stream()
                    .map(AiPostInterestedPersona::getPersonaId)
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank())
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.debug("interested pool unavailable for {} (degrade): {}", postId, e.getMessage());
            return List.of();
        }
    }

    private List<String> loadPlanItemPersonaIds(String postId) {
        try {
            return planItems.findByPostAndTypesAndStatuses(
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
            log.debug("plan-item persona pool failed for {}: {}", postId, e.getMessage());
            return List.of();
        }
    }

    static Map<String, Object> toResponderMap(Persona chosen) {
        Map<String, Object> responder = new LinkedHashMap<>();
        responder.put("personaId", chosen.getId());
        responder.put("nickname", nicknameOf(chosen));
        // Slim voiceProfile: include only essential fields to prevent token overflow
        // (exclude example_comments, example_replies, lexicon, writing_quirks, hot_buttons)
        Map<String, Object> voice = chosen.getVoiceProfile();
        if (voice != null && !voice.isEmpty()) {
            Map<String, Object> slimVoice = new LinkedHashMap<>();
            // Whitelist essential fields only
            String[] essentialFields = {"voice_type", "age", "gender", "slang_level", "tone", "formality"};
            for (String field : essentialFields) {
                Object value = voice.get(field);
                if (value != null && !String.valueOf(value).isBlank()) {
                    slimVoice.put(field, value);
                }
            }
            responder.put("voiceProfile", slimVoice);
        } else {
            responder.put("voiceProfile", Map.of());
        }
        responder.put("formality", formalityOf(chosen));
        return responder;
    }

    /**
     * Plan-less policy (0b): attach REPLY items to the latest non-expired plan (any status).
     * If none → caller marks {@link #FAILURE_NO_ACTIVE_PLAN}. No synthetic plan is created.
     */
    Optional<AiThreadPlan> findUsablePlan(String postId, Instant now) {
        AiThreadPlan plan = plans.findTopByPostIdOrderByPostRevisionDesc(postId).orElse(null);
        if (plan == null || plan.getAbsoluteExpiresAt() == null || now.isAfter(plan.getAbsoluteExpiresAt())) {
            return Optional.empty();
        }
        return Optional.of(plan);
    }

    @Transactional
    void persist(String worker, List<AiHumanInteractionInbox> selected, Map<String, Object> response, Instant now) {
        persist(worker, selected, response, now, 1);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    void persist(String worker, List<AiHumanInteractionInbox> selected, Map<String, Object> response,
                 Instant now, int attemptCount) {
        Map<String, AiHumanInteractionInbox> byComment = new HashMap<>();
        selected.forEach(e -> byComment.put(e.getSourceCommentId(), e));
        Object raw = response.get("replies");
        if (!(raw instanceof List<?> replies)) {
            selected.forEach(e -> inbox.release(e.getId(), worker));
            return;
        }

        Map<String, Integer> llmReplyCounts = new HashMap<>();
        /** Key = postId + ' ' + humanAuthorId (both are 32-char hex ids, so no collision). */
        Map<String, HumanReplyBudget> budgetsByConversation = new HashMap<>();
        Map<String, AiThreadPlan> plansByPost = new HashMap<>();
        Set<String> answered = new HashSet<>();
        Set<String> budgetBlocked = new HashSet<>();
        int seqBase = 10000;

        for (Object value : replies) {
            if (!(value instanceof Map<?, ?> row)) continue;
            String comment = String.valueOf(row.get("humanCommentId"));
            AiHumanInteractionInbox entry = byComment.get(comment);
            if (entry == null) continue;
            llmReplyCounts.merge(comment, 1, Integer::sum);

            String body = LiteralNewlineNormalizer.normalize(
                    row.get("body") == null ? "" : String.valueOf(row.get("body"))).trim();
            String persona = row.get("personaId") == null ? "" : String.valueOf(row.get("personaId"));
            if (body.isBlank() || persona.isBlank()
                    || !personaRepository.existsById(persona)
                    || !guard.check(body, ContentSafetyGuard.ContentType.COMMENT).passed()) continue;

            AiThreadPlan plan = plansByPost.computeIfAbsent(entry.getPostId(),
                    postId -> findUsablePlan(postId, now).orElse(null));
            if (plan == null) continue;

            // Budget is per conversation = (post, human author), never per post: different humans
            // on the same post hold independent 3 personas / 5 each / 15 total budgets (§1.1-24).
            String budgetKey = entry.getPostId() + "" + entry.getAuthorId();
            HumanReplyBudget budget = budgetsByConversation.computeIfAbsent(budgetKey, k -> {
                HumanReplyBudget b = newBudget(configRepository.findById(1).orElse(null));
                for (AiThreadPlanItem existing : planItems.findHumanReplyItemsForPostAndHuman(
                        entry.getPostId(), entry.getAuthorId(), BUDGET_EXCLUDED)) {
                    b.seed(existing.getPersonaId());
                }
                return b;
            });

            String idempotencyKey = humanReplyIdempotencyKey(entry.getId(), persona);
            if (planItems.existsByIdempotencyKey(idempotencyKey)) {
                if (!answered.contains(comment)) {
                    answered.add(comment);
                    inbox.markResponded(entry.getId(), worker, idempotencyKey, attemptCount);
                }
                continue;
            }
            if (!budget.tryAccept(persona)) {
                budgetBlocked.add(comment);
                continue;
            }

            Duration delay = resolveDelay(row.get("delayMinutes"));
            Instant when = now.plus(delay);
            AiThreadPlanItem item;
            try {
                item = planItems.save(AiThreadPlanItem.builder()
                        .planId(plan.getId())
                        .itemType(ThreadPlanItemType.REPLY)
                        .status(ThreadPlanItemStatus.SCHEDULED)
                        .sequenceNo(seqBase++)
                        .personaId(persona)
                        .targetPostId(entry.getPostId())
                        .targetCommentId(entry.getSourceCommentId())
                        .humanAuthorId(entry.getAuthorId())
                        .body(body)
                        .scheduledAt(when)
                        .notBefore(when)
                        .idempotencyKey(idempotencyKey)
                        .build());
            } catch (DataIntegrityViolationException duplicate) {
                log.info("human-reply idempotency collision skip key={}", idempotencyKey);
                if (!answered.contains(comment)) {
                    answered.add(comment);
                    inbox.markResponded(entry.getId(), worker, idempotencyKey, attemptCount);
                }
                continue;
            }
            if (!answered.contains(comment)) {
                inbox.markResponded(entry.getId(), worker, item.getId(), attemptCount);
                answered.add(comment);
            }
        }

        for (AiHumanInteractionInbox entry : selected) {
            String comment = entry.getSourceCommentId();
            if (answered.contains(comment)) continue;
            int llmCount = llmReplyCounts.getOrDefault(comment, 0);
            if (llmCount == 0) {
                inbox.markSkipped(entry.getId(), worker, FAILURE_NO_RESPONSE);
            } else if (budgetBlocked.contains(comment)) {
                inbox.markSkipped(entry.getId(), worker, FAILURE_BUDGET_EXHAUSTED);
            } else if (findUsablePlan(entry.getPostId(), now).isEmpty()) {
                log.info("human-reply skip NO_ACTIVE_PLAN at persist inbox={} post={}", entry.getId(), entry.getPostId());
                inbox.markSkipped(entry.getId(), worker, FAILURE_NO_ACTIVE_PLAN);
            } else {
                inbox.release(entry.getId(), worker);
            }
        }
    }

    // ── 댓글 생성량 설정 해석 ────────────────────────────────────────────────
    // SSOT는 ai_user_generation_config(/admin/ai-user). 컬럼이 0(미설정)이면 application.yml 기본값.

    private static int pick(int configured, int fallback) {
        return configured > 0 ? configured : fallback;
    }

    int respondersMax(AiUserGenerationConfig config) {
        int fallback = props.getHumanReply().getRespondersPerInteractionMax();
        return config == null ? fallback : pick(config.getHrRespondersPerInteractionMax(), fallback);
    }

    int candidateRespondersMax(AiUserGenerationConfig config) {
        int fallback = props.getHumanReply().getCandidateRespondersMax();
        return config == null ? fallback : pick(config.getHrCandidateRespondersMax(), fallback);
    }

    HumanReplyBudget newBudget(AiUserGenerationConfig config) {
        OrchestratorProperties.HumanReply cfg = props.getHumanReply();
        int distinct = config == null ? cfg.getDistinctPersonasPerPostHumanMax()
                : pick(config.getHrDistinctPersonasMax(), cfg.getDistinctPersonasPerPostHumanMax());
        int perPersona = config == null ? cfg.getRepliesPerPersonaPerPostHumanMax()
                : pick(config.getHrRepliesPerPersonaMax(), cfg.getRepliesPerPersonaPerPostHumanMax());
        // 총상한은 파생값이라 3x5!=15 같은 불일치 상태가 존재할 수 없다.
        return new HumanReplyBudget(distinct * perPersona, perPersona, distinct);
    }

    static String humanReplyIdempotencyKey(String inboxId, String personaId) {
        return "human-reply:" + inboxId + ":" + personaId;
    }

    static List<List<Integer>> chunkIndexes(int size, int chunkSize) {
        int cs = Math.max(1, chunkSize);
        List<List<Integer>> chunks = new ArrayList<>();
        for (int start = 0; start < size; start += cs) {
            List<Integer> chunk = new ArrayList<>();
            for (int i = start; i < Math.min(size, start + cs); i++) chunk.add(i);
            chunks.add(chunk);
        }
        return chunks;
    }

    int resolvedChunkSize(AiUserGenerationConfig config) {
        int fallback = props.getHumanReply().getChunkSize();
        int size = config == null ? fallback : pick(config.getHrChunkSize(), fallback);
        return size <= 0 ? 20 : size;
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
